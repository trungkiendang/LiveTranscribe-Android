package com.kun.livetranslation.speech

import android.content.Context
import android.util.Log
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

/**
 * Neural voice-activity detection (Silero). Unlike a loudness threshold it tells speech from music
 * and steady noise, which is what makes video captions cut at real pauses. The model keeps state
 * between calls, so feed it one continuous stream, in order. Not thread-safe.
 */
class SileroSpeechDetector private constructor(private val vad: VadSilero) : AutoCloseable {

    /** True when the first ~96 ms (1536 samples) of 16 kHz mono 16-bit [pcm] contain speech. */
    fun isSpeech(pcm: ByteArray, length: Int = pcm.size): Boolean {
        if (length < FRAME_BYTES) return false
        return vad.isSpeech(pcm.copyOf(FRAME_BYTES))
    }

    override fun close() {
        try { vad.close() } catch (_: Throwable) {}
    }

    companion object {
        /** FRAME_SIZE_1536 = 96 ms at 16 kHz, the largest frame the model supports and a fit for 100 ms chunks. */
        const val FRAME_BYTES = 1536 * 2

        /** Null when the model cannot load (callers fall back to the loudness threshold). */
        fun create(context: Context): SileroSpeechDetector? = try {
            SileroSpeechDetector(
                Vad.builder()
                    .setContext(context.applicationContext)
                    .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                    .setFrameSize(FrameSize.FRAME_SIZE_1536)
                    .setMode(Mode.NORMAL)
                    .setSpeechDurationMs(0) // the segmenter does its own start/hangover smoothing
                    .setSilenceDurationMs(0)
                    .build()
            )
        } catch (t: Throwable) {
            Log.w("SileroSpeechDetector", "Silero VAD unavailable, using loudness threshold", t)
            null
        }
    }
}
