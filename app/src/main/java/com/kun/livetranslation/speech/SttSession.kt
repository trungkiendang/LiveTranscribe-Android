package com.kun.livetranslation.speech

/** Result of feeding one audio chunk: current text, and whether it is a finished segment. */
data class SttFeedResult(val text: String, val accepted: Boolean)

/** A running speech recognizer fed with 16 kHz mono 16-bit PCM chunks. */
interface SttSession {
    fun feed(pcm: ByteArray, length: Int = pcm.size): SttFeedResult
    /** Flushes buffered audio; returns remaining text if it is available synchronously. */
    fun finish(): String
    /** Closes the open utterance now and keeps listening; returns its text. No-op for sessions that segment themselves. */
    fun endUtterance(): String = ""
    fun close()
}
