package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class AudioSegmentationTest {
    @Test fun followsChordChangesWithHalfSecondCues() {
        val rate = 22_050
        val analyzer = AudioChroma(rate)
        val progression = listOf(
            "C" to intArrayOf(48, 52, 55),
            "Am" to intArrayOf(45, 48, 52),
            "F" to intArrayOf(41, 45, 48),
            "G" to intArrayOf(43, 47, 50)
        )
        var timeSample = 0L
        for ((_, notes) in progression) {
            repeat(rate * 6) {
                val time = timeSample.toDouble() / rate
                val sample = notes.sumOf { midi ->
                    val frequency = 440.0 * 2.0.pow((midi - 69.0) / 12.0)
                    0.16 * sin(2.0 * PI * frequency * time) +
                        0.03 * sin(4.0 * PI * frequency * time)
                }
                analyzer.consume(sample.toFloat())
                timeSample++
            }
        }
        val cues = analyzer.finish()
        assertTrue("Expected a timeline, received $cues", cues.size >= 10)
        assertEquals(0L, cues.first().timeMs)
        assertTrue(cues.zipWithNext().all { (first, second) -> second.timeMs - first.timeMs == 500L })
        progression.forEachIndexed { index, (expected, _) ->
            val startMs = (index * 6 + 2) * 1000L
            val endMs = ((index + 1) * 6 - 2) * 1000L
            assertTrue("Missing $expected in interval $startMs..$endMs: $cues",
                cues.any { it.timeMs in startMs..endMs && it.name == expected })
        }
        assertEquals(cues, analyzer.finish()) // Analyzing twice must not append duplicates.
    }

    @Test fun silenceDoesNotCreateFakeChords() {
        val analyzer = AudioChroma(22_050)
        repeat(22_050 * 4) { analyzer.consume(0f) }
        assertTrue(analyzer.finish().isEmpty())
    }
}
