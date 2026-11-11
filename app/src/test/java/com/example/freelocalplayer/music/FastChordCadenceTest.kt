package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class FastChordCadenceTest {
    @Test fun changesAtSubSecondIntervalsAreNotCollapsedToTwoSeconds() {
        val rate = AudioChroma.TARGET_RATE
        val analyzer = AudioChroma(rate)
        val triads = listOf(
            intArrayOf(60, 64, 67), intArrayOf(57, 60, 64),
            intArrayOf(53, 57, 60), intArrayOf(55, 59, 62),
            intArrayOf(60, 64, 67), intArrayOf(57, 60, 64)
        )
        val totalSamples = (rate * triads.size * 0.75).toInt()
        for (sampleIndex in 0 until totalSamples) {
            val seconds = sampleIndex.toDouble() / rate
            val triad = triads[(seconds / 0.75).toInt().coerceAtMost(triads.lastIndex)]
            val signal = triad.sumOf { midi ->
                val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
                sin(2 * PI * frequency * seconds) + 0.18 * sin(4 * PI * frequency * seconds)
            } / 4.0
            analyzer.consume(signal.toFloat())
        }
        val cues = analyzer.finish()
        assertTrue(cues.size >= 8)
        assertTrue(cues.zipWithNext().all { (left, right) -> right.timeMs - left.timeMs == 500L })
        assertTrue(cues.map { it.name }.filter { it != AudioChroma.UNKNOWN_CHORD }.distinct().size >= 3)
        assertEquals(0L, cues.first().timeMs)
    }
}
