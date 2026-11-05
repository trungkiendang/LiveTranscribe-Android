package com.kun.livetranslation.speech

import com.kun.livetranslation.ai.GeminiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Cloud speech recognition with Gemini. Not streaming: speech is cut into utterances
 * (a pause of ~0.7 s, or 8 s at most) and each utterance is sent as one WAV clip, so
 * captions appear one phrase at a time, a second or two after it is spoken.
 * Results arrive through [onText] in order, from a background thread.
 */
class GeminiSttSession(
    private val api: GeminiApi,
    private val languageHint: String?,
    scope: CoroutineScope,
    private val onText: (String) -> Unit
) : SttSession {

    private val clips = Channel<ByteArray>(Channel.UNLIMITED)
    private val worker: Job = scope.launch {
        for (clip in clips) {
            val text = api.transcribe(WavEncoder.encode(clip), languageHint)?.trim().orEmpty()
            if (text.length >= 2 && !text.startsWith("[")) onText(text)
        }
    }

    private val buffer = ByteArrayOutputStream()
    private var preRoll = ByteArray(0)
    private var speaking = false
    private var quietChunks = 0

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        val loud = averageAbs(pcm, length) >= SPEECH_THRESHOLD
        if (loud) {
            if (!speaking) {
                speaking = true
                buffer.write(preRoll) // keep the soft start of the first word
            }
            quietChunks = 0
            buffer.write(pcm, 0, length)
        } else if (speaking) {
            buffer.write(pcm, 0, length)
            if (++quietChunks >= END_OF_UTTERANCE_CHUNKS) flush()
        } else {
            preRoll = pcm.copyOf(length)
        }
        if (speaking && buffer.size() >= MAX_UTTERANCE_BYTES) flush()
        return SttFeedResult("", accepted = false)
    }

    private fun flush() {
        val clip = buffer.toByteArray()
        buffer.reset()
        speaking = false
        quietChunks = 0
        if (clip.size >= MIN_UTTERANCE_BYTES) clips.trySend(clip)
    }

    override fun finish(): String {
        if (speaking) flush()
        return ""
    }

    override fun close() {
        clips.close()
        // Let a clip already in flight finish; the worker ends when the channel drains.
    }

    private fun averageAbs(pcm: ByteArray, length: Int): Int {
        var sum = 0L
        var n = 0
        var i = 0
        while (i + 1 < length) {
            sum += abs((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF))
            n++
            i += 2
        }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    private companion object {
        const val SPEECH_THRESHOLD = 200
        const val END_OF_UTTERANCE_CHUNKS = 7 // ~0.7 s of quiet at 100 ms per chunk
        const val BYTES_PER_SECOND = 32_000
        const val MAX_UTTERANCE_BYTES = 8 * BYTES_PER_SECOND
        const val MIN_UTTERANCE_BYTES = BYTES_PER_SECOND * 4 / 10
    }
}
