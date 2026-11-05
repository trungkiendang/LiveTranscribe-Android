package com.kun.livetranslation

import com.kun.livetranslation.speech.StablePrefixCommitter
import org.junit.Assert.assertEquals
import org.junit.Test

class StablePrefixCommitterTest {
    @Test
    fun commitsWordsOnlyOnceTwoPartialsAgree() {
        val c = StablePrefixCommitter(minChunkWords = 3, holdBack = 1)
        assertEquals(emptyList<String>(), c.update("the quick brown fox"))
        // agreed on 4 words, one held back -> "the quick brown" is final
        assertEquals(listOf("the quick brown"), c.update("the quick brown fox jumps"))
        assertEquals("fox jumps", c.tail("the quick brown fox jumps"))
    }

    @Test
    fun aRevisedWordIsNotCommitted() {
        val c = StablePrefixCommitter(minChunkWords = 2, holdBack = 0)
        c.update("i red the book")
        assertEquals(emptyList<String>(), c.update("i read the book"))
    }

    @Test
    fun finishReturnsOnlyTheUncommittedRestAndResets() {
        val c = StablePrefixCommitter(minChunkWords = 3, holdBack = 1)
        c.update("one two three four")
        c.update("one two three four five")
        assertEquals("four five six", c.finish("one two three four five six"))
        assertEquals("", c.tail(""))
        assertEquals(emptyList<String>(), c.update("seven"))
    }
}
