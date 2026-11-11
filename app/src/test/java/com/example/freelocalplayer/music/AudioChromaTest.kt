package com.example.freelocalplayer.music

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class AudioChromaTest {
    @Test fun syntheticMajorAndMinorTriads() {
        val examples = listOf(
            "C" to intArrayOf(48, 60, 64, 67),
            "Am" to intArrayOf(45, 57, 60, 64),
            "G" to intArrayOf(43, 55, 59, 62),
            "F" to intArrayOf(41, 53, 57, 60),
            "Dm" to intArrayOf(50, 62, 65, 69)
        )
        for ((name, notes) in examples) {
            val analyzer = AudioChroma(44_100)
            for (index in 0 until 44_100 * 3) {
                val value = notes.sumOf { midi ->
                    val hz = 440.0 * 2.0.pow((midi - 69.0) / 12.0)
                    (sin(2.0 * PI * hz * index / 44_100) +
                        0.15 * sin(4.0 * PI * hz * index / 44_100)) * 0.13
                }
                analyzer.consume(value.toFloat())
            }
            assertTrue("Expected $name; got ${analyzer.finish()}",
                analyzer.finish().any { it.name == name })
        }
    }

    @Test fun silenceHasNoChords() {
        val analyzer = AudioChroma(44_100)
        repeat(44_100 * 3) { analyzer.consume(0f) }
        assertEquals(emptyList<ChordCue>(), analyzer.finish())
    }
}
