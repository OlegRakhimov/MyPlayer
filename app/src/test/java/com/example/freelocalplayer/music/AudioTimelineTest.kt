package com.example.freelocalplayer.music

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class AudioTimelineTest {
    @Test fun sameChordProducesMultipleTimeMarkers() {
        val analyzer = AudioChroma(11_025)
        val chord = intArrayOf(48, 60, 64, 67)
        repeat(11_025 * 7) { i ->
            val sample = chord.sumOf { note ->
                val hz = 440.0 * 2.0.pow((note - 69.0) / 12.0)
                0.14 * sin(2 * PI * hz * i / 11_025.0)
            }
            analyzer.consume(sample.toFloat())
        }
        val timeline = analyzer.finish()
        assertTrue("No timeline generated: $timeline", timeline.size >= 2)
        assertTrue("Timeline is not chronological", timeline.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
        assertTrue("Unexpected key recognition", timeline.any { it.name == "C" })
    }
}
