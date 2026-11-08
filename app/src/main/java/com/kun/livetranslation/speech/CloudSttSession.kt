package com.kun.livetranslation.speech

import android.os.SystemClock
import android.util.Log
import com.kun.livetranslation.ai.AiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Cloud speech recognition through the configured AI provider. Not streaming: an
 * [UtteranceSegmenter] cuts the audio into phrases and each phrase is sent as one WAV clip,
 * so captions appear one phrase at a time, a second or two after it is spoken.
 *
 * Requests run concurrently (a slow one does not delay the ones after it) but their text is
 * delivered to [onText] strictly in speaking order.
 */
class CloudSttSession(
    private val api: AiApi,
    private val languageHint: String?,
    private val scope: CoroutineScope,
    private val speechDetector: SileroSpeechDetector? = null,
    /** Non-null: ask for the transcript and its translation in one request (Gemini only). */
    private val translateTo: String? = null,
    private val onText: (text: String, translation: String?) -> Unit
) : SttSession {

    // 2 s clips: continuous speech (video) gets a caption about every 2 s instead of 3 s.
    private val segmenter = UtteranceSegmenter(
        speechDetector?.let { d -> { chunk: ByteArray -> d.isSpeech(chunk) } },
        maxUtteranceBytes = 2 * UtteranceSegmenter.BYTES_PER_SECOND
    )
    private val pending = Channel<Deferred<Pair<String, String?>?>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in pending) {
                val (text, translation) = request.await() ?: continue
                if (text.length >= 2 && !text.startsWith("[")) onText(text, translation?.takeIf { it.isNotBlank() })
            }
        }
    }

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        segmenter.feed(pcm, length)?.let(::send)
        return SttFeedResult("", accepted = false)
    }

    private fun send(clip: ByteArray) {
        val queuedAt = SystemClock.elapsedRealtime()
        pending.trySend(scope.async {
            val wav = WavEncoder.encode(clip)
            val result: Pair<String, String?>? = if (translateTo != null) {
                api.transcribeAndTranslate(wav, languageHint, translateTo)?.let { it.first to it.second }
            } else {
                api.transcribe(wav, languageHint)?.let { it.trim() to null }
            }
            // Cloud latency per clip: how long the request took, on top of the clip's own length.
            Log.d(TAG, "cloud clip ${clip.size / UtteranceSegmenter.BYTES_PER_SECOND.toDouble()} s answered in ${SystemClock.elapsedRealtime() - queuedAt} ms (oneCall=${translateTo != null})")
            result
        })
    }

    override fun finish(): String {
        segmenter.flush()?.let(::send)
        return ""
    }

    override fun close() {
        speechDetector?.close()
        pending.close() // clips already sent still finish and are delivered
    }

    private companion object { const val TAG = "CloudSttSession" }
}
