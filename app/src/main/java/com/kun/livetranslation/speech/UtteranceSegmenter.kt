package com.kun.livetranslation.speech

import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Cuts a continuous 16 kHz mono 16-bit PCM stream into utterances for cloud recognition.
 *
 * - Speech is detected against an adaptive noise floor (the quietest idle chunk of the last ~3 s), so a
 *   noisy room does not read as endless speech and a quiet voice still counts.
 * - A few chunks of audio before the first loud chunk are kept so soft word starts are not clipped.
 * - An utterance ends after [END_QUIET_CHUNKS] quiet chunks (~0.5 s); most of that trailing silence is trimmed.
 * - Very short bursts (a click, a cough) are dropped instead of being sent to the recognizer.
 * - A long utterance is cut at its quietest recent chunk rather than in the middle of a word.
 *
 * Feed chunks of about 100 ms. Not thread-safe.
 */
class UtteranceSegmenter {
    private val recentEnergy = ArrayDeque<Int>()
    private val preRoll = ArrayDeque<ByteArray>()
    private val chunks = ArrayList<ByteArray>()
    private val energies = ArrayList<Int>()
    private var speaking = false
    private var loudChunks = 0
    private var quietChunks = 0

    /** Returns a finished utterance (PCM) when this chunk completes one, else null. */
    fun feed(pcm: ByteArray, length: Int): ByteArray? {
        val chunk = pcm.copyOf(length)
        val e = averageAbs(chunk)
        // The noise floor is learned only while idle. Updating it during speech would make a long
        // phrase look like "the noise" and cut it short after ~3 s.
        if (!speaking) {
            recentEnergy.addLast(e)
            if (recentEnergy.size > NOISE_WINDOW_CHUNKS) recentEnergy.removeFirst()
        }
        val noise = recentEnergy.minOrNull() ?: e
        val startThreshold = maxOf(MIN_START_THRESHOLD, noise * 3)
        val endThreshold = maxOf(MIN_END_THRESHOLD, noise * 2)

        if (!speaking) {
            if (e >= startThreshold) {
                speaking = true
                chunks.addAll(preRoll)
                energies.addAll(preRoll.map { 0 })
                preRoll.clear()
                loudChunks = 1
                quietChunks = 0
                add(chunk, e)
            } else {
                preRoll.addLast(chunk)
                if (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
            }
            return null
        }

        add(chunk, e)
        if (e >= endThreshold) {
            loudChunks++
            quietChunks = 0
        } else {
            quietChunks++
        }
        if (quietChunks >= END_QUIET_CHUNKS) return finish()
        if (chunks.sumOf { it.size } >= MAX_UTTERANCE_BYTES) return cutAtQuietest()
        return null
    }

    /** Ends the current utterance now (stop/pause); null if there is nothing worth sending. */
    fun flush(): ByteArray? = if (speaking) finish() else null

    private fun add(chunk: ByteArray, energy: Int) {
        chunks.add(chunk)
        energies.add(energy)
    }

    private fun finish(): ByteArray? {
        // Keep a short tail of silence, drop the rest of the pause that ended the utterance.
        val drop = (quietChunks - KEEP_TAIL_CHUNKS).coerceIn(0, chunks.size)
        val clip = join(chunks.subList(0, chunks.size - drop))
        val enough = loudChunks >= MIN_SPEECH_CHUNKS
        reset()
        return if (enough && clip.size >= MIN_UTTERANCE_BYTES) clip else null
    }

    /** Too long: split at the quietest chunk of the last 1.5 s; the rest starts the next utterance. */
    private fun cutAtQuietest(): ByteArray? {
        val from = (chunks.size - CUT_SEARCH_CHUNKS).coerceAtLeast(1)
        var cut = chunks.size - 1
        for (i in from until chunks.size) if (energies[i] < energies[cut]) cut = i
        val head = join(chunks.subList(0, cut + 1))
        val tailChunks = ArrayList(chunks.subList(cut + 1, chunks.size))
        val tailEnergies = ArrayList(energies.subList(cut + 1, energies.size))
        chunks.clear(); energies.clear()
        chunks.addAll(tailChunks); energies.addAll(tailEnergies)
        quietChunks = 0
        loudChunks = tailEnergies.count { it > 0 }.coerceAtLeast(1)
        return head
    }

    private fun reset() {
        chunks.clear(); energies.clear(); preRoll.clear()
        speaking = false; loudChunks = 0; quietChunks = 0
    }

    private fun join(parts: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun averageAbs(pcm: ByteArray): Int {
        var sum = 0L
        var n = 0
        var i = 0
        while (i + 1 < pcm.size) {
            sum += abs(((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt())
            n++
            i += 2
        }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    companion object {
        const val BYTES_PER_SECOND = 32_000
        const val NOISE_WINDOW_CHUNKS = 30        // ~3 s
        const val MIN_START_THRESHOLD = 150
        const val MIN_END_THRESHOLD = 110
        const val PRE_ROLL_CHUNKS = 3             // ~300 ms
        const val END_QUIET_CHUNKS = 5            // ~0.5 s
        const val KEEP_TAIL_CHUNKS = 3
        const val MIN_SPEECH_CHUNKS = 3           // ignore bursts shorter than ~300 ms
        const val MIN_UTTERANCE_BYTES = BYTES_PER_SECOND * 4 / 10
        const val MAX_UTTERANCE_BYTES = 5 * BYTES_PER_SECOND // continuous speech (video) -> a caption at least every ~5 s
        const val CUT_SEARCH_CHUNKS = 15          // ~1.5 s
    }
}
