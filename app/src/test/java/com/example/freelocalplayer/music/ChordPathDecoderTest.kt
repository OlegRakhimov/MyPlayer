package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordPathDecoderTest {
    private fun chroma(vararg notes: Int): DoubleArray = DoubleArray(12) { i -> if (i in notes) 0.3 else 0.004 }

    @Test fun followsProgressionAndPreservesTimestamp() {
        val windows = listOf(
            chroma(0, 4, 7), chroma(0, 4, 7),
            chroma(9, 0, 4), chroma(9, 0, 4),
            chroma(5, 9, 0), chroma(5, 9, 0),
            chroma(7, 11, 2), chroma(7, 11, 2)
        ).mapIndexed { i, c -> ChordPathDecoder.Window(i * 2000L, c, 3) }
        val out = ChordPathDecoder.decode(windows)
        assertEquals(8, out.size)
        assertEquals("C", out[0].name)
        assertEquals("Am", out[2].name)
        assertEquals("F", out[4].name)
        assertEquals("G", out[6].name)
        assertEquals(14_000L, out.last().timeMs)
    }

    @Test fun retainsUnknownSilenceAndDoesNotInventHarmony() {
        val windows = listOf(
            ChordPathDecoder.Window(0L, chroma(0, 4, 7), 4),
            ChordPathDecoder.Window(2000L, null, 0),
            ChordPathDecoder.Window(4000L, null, 0),
            ChordPathDecoder.Window(6000L, chroma(7, 11, 2), 4)
        )
        val out = ChordPathDecoder.decode(windows)
        assertEquals("—", out[1].name)
        assertEquals("—", out[2].name)
        assertTrue(ChordPathDecoder.decode(windows.drop(1).take(2)).isEmpty())
    }
}
