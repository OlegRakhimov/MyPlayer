package com.example.freelocalplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedLrcParserTest {
    @Test
    fun parsesWordLevelTimestamps() {
        val input = "[00:02.00]<00:02.00>My <00:02.35>life <00:02.70>my <00:03.00>rules"
        val line = LrcParser.parse(input).single()
        assertEquals("My life my rules", line.text)
        assertEquals(listOf(2000L, 2350L, 2700L, 3000L), line.words.map { it.timeMs })
        assertEquals("life ", line.words[1].text)
        assertEquals(1, LrcParser.currentWord(line.words, 2400L))
    }

    @Test
    fun plainTextAndOrdinaryLrcHaveNoInventedWordTimes() {
        assertTrue(LrcParser.parse("Hello world").isEmpty())
        assertTrue(LrcParser.parse("[00:01.00]Hello world").single().words.isEmpty())
    }

    @Test
    fun invalidSecondsIgnoredInWords() {
        val line = LrcParser.parse("[00:01.00]<00:70.00>Wrong <00:02.00>valid").single()
        assertEquals(1, line.words.size)
        assertEquals("valid", line.words.single().text)
    }
}
