package com.example.freelocalplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parseTimedLinesAndHundredths() {
        val lines = LrcParser.parse("[00:01.50]Hello\n[01:02.123]World")
        assertEquals(2, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello", lines[0].text)
        assertEquals(62123L, lines[1].timeMs)
    }

    @Test
    fun oneLineCanHaveSeveralTimestamps() {
        val lines = LrcParser.parse("[00:05.1][00:10.50]Repeat")
        assertEquals(listOf(5100L, 10500L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "Repeat" })
    }

    @Test
    fun metadataAndInvalidSecondsIgnored() {
        val lines = LrcParser.parse("[ar:Artist]\n[00:70.20]Bad\n[00:02.0]Good")
        assertEquals(1, lines.size)
        assertEquals(2000L, lines[0].timeMs)
    }

    @Test
    fun findsActiveLine() {
        val lines = LrcParser.parse("[00:03.0]First\n[00:08.0]Second")
        assertEquals(-1, LrcParser.currentLine(lines, 1000))
        assertEquals(0, LrcParser.currentLine(lines, 5000))
        assertEquals(1, LrcParser.currentLine(lines, 8000))
    }
}
