package com.kun.livetranslation.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TranscriptHistoryDedupeTest {
    private fun entry(original: String, at: Long) =
        TranscriptEntry(timestamp = at, originalText = original, translatedText = "t-$original", sourceLanguage = "en", targetLanguage = "vi")

    @Test
    fun sameLineTwiceWithinSeconds_isStoredOnce_butLaterRepeatsAreKept() = runBlocking {
        val file = File.createTempFile("history", ".json").apply { delete(); deleteOnExit() }
        val store = TranscriptHistoryStore(file, true)
        store.add(entry("hello", 1_000))
        store.add(entry("hello", 1_500))   // duplicate
        store.add(entry("world", 2_000))
        store.add(entry("world", 60_000))  // same words, much later: a new line
        assertEquals(3, store.getAll().size)
    }
}
