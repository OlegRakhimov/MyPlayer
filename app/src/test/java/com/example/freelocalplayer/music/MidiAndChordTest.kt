package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

class MidiAndChordTest {
    private fun file(vararg tracks: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.writeBytes("MThd")
        out.writeInt(6)
        out.writeShort(if (tracks.size == 1) 0 else 1)
        out.writeShort(tracks.size)
        out.writeShort(480)
        tracks.forEach { track ->
            out.writeBytes("MTrk")
            out.writeInt(track.size)
            out.write(track)
        }
        return bytes.toByteArray()
    }

    @Test fun basicMidiCreatesTimedGuitarNotes() {
        val track = byteArrayOf(
            0, -64, 24, // program change: nylon guitar
            0, -112, 60, 100, // C4, note on
            -125, 96, -128, 60, 0, // 480 ticks = 500ms, note off
            0, -1, 47, 0
        )
        val parsed = MidiScoreParser.parse(file(track))
        assertEquals(1, parsed.parts.size)
        assertEquals(PartFamily.GUITAR, parsed.parts[0].family)
        assertEquals(500, parsed.durationMs)
        assertEquals(60, parsed.parts[0].notes[0].pitch)
    }

    @Test fun tempoChangeConvertsTicksCorrectly() {
        val tempoTrack = byteArrayOf(
            0, -1, 81, 3, 7, -95, 32, // 500000us quarter
            -125, 96, -1, 81, 3, 15, 66, 64, // tick 480: 1_000_000us quarter
            0, -1, 47, 0
        )
        val noteTrack = byteArrayOf(
            0, -112, 64, 80,
            -113, 0, -128, 64, 0, // tick 1920
            0, -1, 47, 0
        )
        val parsed = MidiScoreParser.parse(file(tempoTrack, noteTrack))
        assertEquals(3500, parsed.durationMs) // first 480:500ms; following 1440:3000ms
    }

    @Test fun chordSheetSortsAndTracksCurrent() {
        val cues = ChordTimeline.parse("[00:04.50] F\n[00:00.00] Am\n[00:09.00] C")
        assertEquals(listOf("Am", "F", "C"), cues.map { it.name })
        assertEquals(1, ChordTimeline.current(cues, 7000))
        assertEquals(-1, ChordTimeline.current(cues, -10))
    }

    @Test fun invalidMidiIsRejected() {
        assertThrows(IOException::class.java) { MidiScoreParser.parse("not midi".toByteArray()) }
    }

    @Test fun blankChordSheetIsAllowed() {
        assertTrue(ChordTimeline.parse("").isEmpty())
    }
}
