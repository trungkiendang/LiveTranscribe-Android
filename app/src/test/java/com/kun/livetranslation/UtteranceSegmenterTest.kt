package com.kun.livetranslation

import com.kun.livetranslation.speech.UtteranceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceSegmenterTest {

    /** 100 ms of a square wave: its average absolute amplitude is exactly [amp]. */
    private fun chunk(amp: Int): ByteArray {
        val out = ByteArray(3200)
        for (i in 0 until 1600) {
            val v = if (i % 2 == 0) amp else -amp
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun UtteranceSegmenter.feedAll(amp: Int, chunks: Int): List<ByteArray> =
        (0 until chunks).mapNotNull { feed(chunk(amp), 3200) }

    private fun seconds(clip: ByteArray) = clip.size / 32000.0

    @Test
    fun silenceAlone_producesNothing() {
        assertTrue(UtteranceSegmenter().feedAll(30, 100).isEmpty())
    }

    @Test
    fun aSpokenPhrase_isCutAfterThePause_withPreRollAndShortTail() {
        val s = UtteranceSegmenter()
        val clips = s.feedAll(30, 10) + s.feedAll(4000, 10) + s.feedAll(30, 12)
        assertEquals(1, clips.size)
        // 3 pre-roll + 10 speech + 3 tail chunks = 1.6 s; the rest of the pause is dropped.
        assertEquals(1.6, seconds(clips[0]), 0.05)
    }

    @Test
    fun aShortClick_isDiscarded() {
        val s = UtteranceSegmenter()
        val clips = s.feedAll(30, 10) + s.feedAll(6000, 1) + s.feedAll(30, 15)
        assertTrue(clips.isEmpty())
    }

    @Test
    fun constantBackgroundNoise_isNotSpeech_butVoiceOverItIs() {
        val s = UtteranceSegmenter()
        val noise = s.feedAll(500, 40)
        assertTrue("steady noise must not start an utterance", noise.isEmpty())
        val clips = s.feedAll(4000, 10) + s.feedAll(500, 12)
        assertEquals(1, clips.size)
    }

    @Test
    fun quietSpeaker_isStillDetectedInASilentRoom() {
        val s = UtteranceSegmenter()
        val clips = s.feedAll(20, 10) + s.feedAll(200, 10) + s.feedAll(20, 12)
        assertEquals(1, clips.size)
    }

    @Test
    fun longSpeech_isCutAtItsQuietestRecentMoment_notMidWord() {
        val s = UtteranceSegmenter()
        s.feedAll(30, 5)
        // 9 s of speech with a brief dip at ~2.4 s (a breath) inside the last 1 s before the 3 s limit.
        val clips = mutableListOf<ByteArray>()
        for (i in 0 until 90) {
            val amp = if (i == 24) 400 else 4000
            s.feed(chunk(amp), 3200)?.let { clips += it }
        }
        val first = clips.firstOrNull()
        assertNotNull(first)
        // pre-roll (0.3 s) + speech up to the dip chunk (2.4 s) -> about 2.7 s, not the full 3 s.
        assertTrue("cut near the dip, got ${seconds(first!!)}", seconds(first) in 2.5..2.9)
    }

    @Test
    fun flush_returnsTheUnfinishedPhrase_andNothingWhenIdle() {
        val s = UtteranceSegmenter()
        assertNull(s.flush())
        s.feedAll(30, 5)
        s.feedAll(4000, 8)
        assertNotNull(s.flush())
        assertNull(s.flush())
    }
}
