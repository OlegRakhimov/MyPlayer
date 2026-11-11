package com.example.freelocalplayer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatSupportTest {
    @Test fun wavIsAudioEvenWithWrongMime() {
        assertEquals(false, MediaFormatSupport.isVideo("song.WAV", "application/octet-stream"))
        assertEquals(false, MediaFormatSupport.isVideo("song.wav", "video/mp4"))
        assertEquals(false, MediaFormatSupport.isVideo("recording.wave", null))
        assertTrue(MediaFormatSupport.isWave("test.wav"))
    }
    @Test fun videoAndUnknownRemainDistinguished() {
        assertEquals(true, MediaFormatSupport.isVideo("clip.mp4", "application/octet-stream"))
        assertEquals(null, MediaFormatSupport.isVideo("notes.txt", "text/plain"))
    }
}
