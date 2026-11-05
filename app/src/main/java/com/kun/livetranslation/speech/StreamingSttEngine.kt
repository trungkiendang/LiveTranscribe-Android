package com.kun.livetranslation.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.kun.livetranslation.settings.AudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Continuous, low-latency speech-to-text engine backed by a live
 * [VoskStreamingSession]. Reads 16kHz mono PCM from either the mic or
 * MediaProjection system-audio capture and feeds ~100ms chunks straight into
 * Vosk, emitting partial results as they evolve and final segments on
 * silence boundaries.
 *
 * This replaces the old batch SystemAudioEngine, which accumulated 2-6s
 * windows and created a fresh Recognizer per chunk, throwing away streaming
 * state and making captions feel laggy and incoherent.
 */
class StreamingSttEngine(
    private val context: Context,
    private val audioSource: AudioSource,
    private val mediaProjection: MediaProjection?,
    private val languageCode: String,
    private val localSttClient: LocalVoskSttClient,
    /** When set, speech goes to Gemini instead of Vosk ([languageHint] null = detect). */
    private val aiApi: com.kun.livetranslation.ai.AiApi? = null,
    private val languageHint: String? = null,
    private val scope: CoroutineScope,
    private val onResult: (SpeechResult) -> Unit,
    private val onError: (String?) -> Unit = {}
) : SpeechEngine {

    private val statusMutable = MutableStateFlow(RecognitionStatus.IDLE)
    val status: StateFlow<RecognitionStatus> = statusMutable

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var session: SttSession? = null

    @Volatile private var running = false
    @Volatile private var paused = false

    override fun start() {
        if (running) return
        running = true
        paused = false
        scope.launch(Dispatchers.IO) { startInternal() }
    }

    override fun stop() {
        running = false
        paused = false
        // Unblocks a pending AudioRecord.read(); the capture loop then exits and
        // releases the recognizer itself, so it is never closed mid-feed.
        try { audioRecord?.stop() } catch (_: Throwable) {}
        val job = captureJob
        captureJob = null
        if (job == null) releaseResources() else job.cancel()
        if (audioSource == AudioSource.SYSTEM) {
            try { mediaProjection?.stop() } catch (_: Throwable) {}
        }
        statusMutable.value = RecognitionStatus.IDLE
    }

    private fun releaseResources() {
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        val s = session
        session = null
        s?.finish()?.let { remainder ->
            if (remainder.isNotBlank()) onResult(SpeechResult(remainder, isFinal = true))
        }
        s?.close()
    }

    override fun pause() {
        paused = true
        statusMutable.value = RecognitionStatus.PAUSED
    }

    override fun resume() {
        if (!running) return
        paused = false
        statusMutable.value = RecognitionStatus.LISTENING
    }

    private suspend fun startInternal() {
        val localSession: SttSession? = if (aiApi != null) {
            CloudSttSession(aiApi, languageHint, scope) { text -> onResult(SpeechResult(text, isFinal = true)) }
        } else {
            localSttClient.openSession(languageCode, SAMPLE_RATE)
        }
        if (localSession == null) {
            onError(
                "No on-device model installed for '$languageCode'. " +
                    "Open the language picker to download one."
            )
            statusMutable.value = RecognitionStatus.ERROR
            running = false
            return
        }
        session = localSession

        val record = try {
            buildAudioRecord()
        } catch (t: Throwable) {
            Log.e(TAG, "buildAudioRecord failed", t)
            onError("Audio capture failed: ${t.message ?: t::class.java.simpleName}")
            statusMutable.value = RecognitionStatus.ERROR
            session?.close(); session = null
            running = false
            return
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            onError(
                if (audioSource == AudioSource.SYSTEM)
                    "System audio capture failed to initialize"
                else
                    "Microphone capture failed to initialize"
            )
            statusMutable.value = RecognitionStatus.ERROR
            record?.release()
            session?.close(); session = null
            running = false
            return
        }
        audioRecord = record

        try {
            record.startRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "startRecording failed", t)
            onError("Audio capture failed to start: ${t.message ?: t::class.java.simpleName}")
            statusMutable.value = RecognitionStatus.ERROR
            record.release()
            audioRecord = null
            session?.close(); session = null
            running = false
            return
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            onError("Audio capture did not start")
            statusMutable.value = RecognitionStatus.ERROR
            record.release()
            audioRecord = null
            session?.close(); session = null
            running = false
            return
        }

        statusMutable.value = RecognitionStatus.LISTENING
        onError(null)

        if (!running) { // stop() arrived while we were starting
            releaseResources()
            return
        }
        captureJob = scope.launch(Dispatchers.Default) {
            try { captureLoop(record) } finally { releaseResources() }
        }
    }

    private suspend fun captureLoop(record: AudioRecord) {
        // Same priority class the platform gives audio threads: avoids capture overruns
        // when the UI or translation is busy.
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = ByteArray(CHUNK_BYTES)
        val preRoll = ByteArray(CHUNK_BYTES) // last skipped silent chunk, fed before speech resumes
        var preRollLen = 0
        var silentChunkStreak = 0
        var lastEmittedPartial = ""
        var utteranceChunks = 0 // chunks fed since the last final
        var sawAnyAudioEver = false

        while (scope.isActive && running) {
            if (paused) {
                delay(120)
                continue
            }

            val read = try {
                record.read(buffer, 0, buffer.size)
            } catch (t: Throwable) {
                Log.w(TAG, "AudioRecord.read threw", t)
                onError("Audio capture read failed")
                statusMutable.value = RecognitionStatus.ERROR
                delay(500)
                continue
            }

            if (read <= 0) {
                // read == 0 is transient on some devices; anything negative is fatal-ish.
                if (read < 0) {
                    Log.w(TAG, "AudioRecord.read returned $read")
                    delay(100)
                }
                continue
            }

            val avgAbs = averageAbsAmplitude(buffer, read)
            val isSilent = avgAbs < SILENCE_AVERAGE_ABS_THRESHOLD

            if (isSilent) {
                silentChunkStreak += 1
                // For system audio, flag when we've been silent for a long time so the
                // user knows nothing is actually playing.
                if (audioSource == AudioSource.SYSTEM &&
                    !sawAnyAudioEver &&
                    silentChunkStreak >= SILENT_CHUNKS_BEFORE_SYSTEM_HINT
                ) {
                    onError(NO_CAPTURABLE_AUDIO_MESSAGE)
                }
            } else {
                silentChunkStreak = 0
                sawAnyAudioEver = true
                onError(null)
            }

            val localSession = session ?: break

            // Idle gating: no utterance in progress and ~1s of quiet -> don't spend CPU on Vosk.
            // The last skipped chunk is replayed first so a soft word onset is not lost.
            if (isSilent && lastEmittedPartial.isEmpty() && silentChunkStreak > IDLE_SKIP_AFTER_CHUNKS) {
                System.arraycopy(buffer, 0, preRoll, 0, read)
                preRollLen = read
                continue
            }
            if (preRollLen > 0) {
                localSession.feed(preRoll, preRollLen)
                preRollLen = 0
            }
            val result = localSession.feed(buffer, read)

            utteranceChunks++
            if (result.accepted) {
                // Vosk hit a silence/segment boundary and emitted a finalized chunk.
                if (result.text.isNotBlank()) {
                    onResult(SpeechResult(result.text, isFinal = true))
                    lastEmittedPartial = ""
                }
                utteranceChunks = 0
            } else if (lastEmittedPartial.isNotEmpty() && isSilent &&
                utteranceChunks >= SOFT_CUT_MIN_CHUNKS && silentChunkStreak >= SOFT_CUT_QUIET_CHUNKS
            ) {
                // Continuous speech (a video) rarely gives Vosk the long pause it waits for, so the
                // utterance and its translation grow for many seconds. A short breath ends it instead.
                val text = localSession.endUtterance().ifBlank { lastEmittedPartial }
                onResult(SpeechResult(text, isFinal = true))
                lastEmittedPartial = ""
                utteranceChunks = 0
            } else if (result.text.isNotBlank() && result.text != lastEmittedPartial) {
                // Live partial — drives the "words appearing as you speak" overlay.
                onResult(SpeechResult(result.text, isFinal = false))
                lastEmittedPartial = result.text
            }

            if (!paused && running) {
                statusMutable.value = RecognitionStatus.LISTENING
            }
        }
    }

    private fun buildAudioRecord(): AudioRecord? {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_BYTES * 8)

        return when (audioSource) {
            AudioSource.SYSTEM -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    onError("System audio capture requires Android 10 (API 29) or newer. Please switch to Microphone mode.")
                    return null
                }
                val projection = mediaProjection
                    ?: run {
                        onError("MediaProjection not available for system audio")
                        return null
                    }
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf)
                    .build()
            }
            AudioSource.MIC -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    onError("Microphone permission not granted. Allow microphone access and try again.")
                    return null
                }
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf)
                    .build()
            }
        }
    }

    companion object {
        private const val TAG = "StreamingSttEngine"
        private const val SAMPLE_RATE = 16_000
        /** ~100ms at 16kHz mono 16-bit = 3200 bytes. Small chunks = live partials. */
        private const val CHUNK_BYTES = 3200
        private const val SILENCE_AVERAGE_ABS_THRESHOLD = 120
        private const val IDLE_SKIP_AFTER_CHUNKS = 10 // ~1s of silence
        private const val SOFT_CUT_MIN_CHUNKS = 25 // only cut utterances longer than ~2.5 s
        private const val SOFT_CUT_QUIET_CHUNKS = 2 // ...at a ~200 ms pause
        private const val SILENT_CHUNKS_BEFORE_SYSTEM_HINT = 30 // ~3s of silence
        private const val NO_CAPTURABLE_AUDIO_MESSAGE =
            "No capturable system audio yet. Play unmuted media in an app that allows audio capture."

        private fun averageAbsAmplitude(pcmData: ByteArray, length: Int): Int {
            var sum = 0L
            var samples = 0
            var i = 0
            val end = minOf(length, pcmData.size) - 1
            while (i < end) {
                val low = pcmData[i].toInt() and 0xFF
                val high = pcmData[i + 1].toInt()
                val sample = (high shl 8) or low
                sum += abs(sample)
                samples += 1
                i += 2
            }
            return if (samples == 0) 0 else (sum / samples).toInt()
        }
    }
}
