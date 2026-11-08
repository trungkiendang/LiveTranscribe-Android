package com.kun.livetranslation.speech

import com.k2fsa.sherpa.onnx.OnlineRecognizer

/** Streaming session over a sherpa-onnx recognizer: partial text every chunk, a final line at each pause. */
class SherpaSttSession(private val recognizer: OnlineRecognizer) : SttSession {
    private val stream = recognizer.createStream("")

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        val n = length / 2
        val samples = FloatArray(n) { i ->
            ((pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)).toShort() / 32768f
        }
        stream.acceptWaveform(samples, 16000)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val text = recognizer.getResult(stream).text.trim()
        if (recognizer.isEndpoint(stream)) {
            recognizer.reset(stream)
            return SttFeedResult(text, accepted = text.isNotEmpty())
        }
        return SttFeedResult(text, accepted = false)
    }

    override fun finish(): String {
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }
}
