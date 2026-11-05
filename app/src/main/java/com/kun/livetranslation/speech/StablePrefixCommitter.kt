package com.kun.livetranslation.speech

/**
 * Turns the growing partial text of a streaming recognizer into short, final chunks without
 * waiting for the speaker to pause (LocalAgreement-2): a word is stable once two consecutive
 * partials agree on it and every word before it. Stable words are committed in chunks of at
 * least [minChunkWords]; the unstable tail stays a live partial. Not thread-safe.
 */
class StablePrefixCommitter(
    private val minChunkWords: Int = 5,
    /** Newest agreed words are still likely to flip, so they are held back. */
    private val holdBack: Int = 1
) {
    private var committed = 0
    private var previous: List<String> = emptyList()

    /** Feed the full text of the open utterance; returns the chunks that just became final. */
    fun update(text: String): List<String> {
        val words = words(text)
        var agreed = 0
        val limit = minOf(words.size, previous.size)
        while (agreed < limit && words[agreed] == previous[agreed]) agreed++
        previous = words
        val stable = agreed - holdBack
        if (stable - committed < minChunkWords) return emptyList()
        val chunk = words.subList(committed, stable).joinToString(" ")
        committed = stable
        return listOf(chunk)
    }

    /** The not-yet-committed part of [text]: what is still shown as a live partial. */
    fun tail(text: String): String = words(text).drop(committed).joinToString(" ")

    /** The recognizer closed the utterance: returns what was never committed and starts over. */
    fun finish(finalText: String): String = tail(finalText).also { reset() }

    fun reset() {
        committed = 0
        previous = emptyList()
    }

    private fun words(text: String) = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
