package com.kun.livetranslation.speech

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
    private val onText: (String) -> Unit
) : SttSession {

    private val segmenter = UtteranceSegmenter()
    private val pending = Channel<Deferred<String?>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in pending) {
                val text = request.await()?.trim().orEmpty()
                if (text.length >= 2 && !text.startsWith("[")) onText(text)
            }
        }
    }

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        segmenter.feed(pcm, length)?.let(::send)
        return SttFeedResult("", accepted = false)
    }

    private fun send(clip: ByteArray) {
        pending.trySend(scope.async { api.transcribe(WavEncoder.encode(clip), languageHint) })
    }

    override fun finish(): String {
        segmenter.flush()?.let(::send)
        return ""
    }

    override fun close() {
        pending.close() // clips already sent still finish and are delivered
    }
}
