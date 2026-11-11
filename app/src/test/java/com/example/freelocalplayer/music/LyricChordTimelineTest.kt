package com.example.freelocalplayer.music

import com.example.freelocalplayer.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricChordTimelineTest {
    @Test fun bindsChordsToTimedLinesAndKeepsChanges() {
        val lines = LrcParser.parse("[00:00.00]First lyric line\n[00:05.00]Second lyric line")
        val cues = listOf(
            ChordCue(0, "C", "auto"), ChordCue(2000, "C", "auto"),
            ChordCue(4000, "G", "auto"), ChordCue(6000, "Am", "auto")
        )
        val bound = LyricChordTimeline.bind(lines, cues, 0, 10000)
        assertEquals(listOf("C", "G"), bound[0].chords.map { it.name })
        assertEquals(listOf("G", "Am"), bound[1].chords.map { it.name })
    }

    @Test fun offsetAndUnknownArePreserved() {
        val lines = LrcParser.parse("[00:03.00]Line one\n[00:06.00]Line two")
        val cues = listOf(ChordCue(2000, "Am", "auto"), ChordCue(4000, "—", "auto"))
        val mapped = LyricChordTimeline.bind(lines, cues, 1000, 9000)
        assertEquals(3000L, mapped[0].chords.first().audioTimeMs)
        assertEquals("—", mapped[1].chords.first().name)
    }

    @Test fun manualChordTokensAreOnlyRecognizedForChordSymbols() {
        val tokens = LyricChordTimeline.parseInline("[Am]Hello [G7]world [00:12]now")
        assertEquals("Am", tokens[0].chord)
        assertEquals("G7", tokens[1].chord)
        assertTrue(tokens.joinToString("") { it.text }.contains("[00:12]"))
        assertFalse(LyricChordTimeline.containsInlineChords("[00:25.25] Test"))
    }

    @Test fun noInventedChords() {
        val lines = LrcParser.parse("[00:00]A line")
        val bound = LyricChordTimeline.bind(lines, emptyList(), 0, 3000)
        assertTrue(bound.single().chords.isEmpty())
        assertEquals(null, LyricChordTimeline.chordAt(emptyList(), 1000, 0))
    }
}
