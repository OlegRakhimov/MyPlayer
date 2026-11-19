package com.example.freelocalplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumePolicyTest {
    @Test fun acceptsLocalContentWithPosition() {
        assertEquals(42_500L, PlaybackResumePolicy.create(
            "track-1", "content://media/external/audio/media/42", "My Song", 42_500L
        )?.positionMs)
    }

    @Test fun acceptsLocalFileWithEncodedCharacters() {
        assertNotNull(PlaybackResumePolicy.create("track-2", "file:///storage/emulated/0/A%20B.wav", "", 0))
    }

    @Test fun refusesRemoteMedia() {
        assertNull(PlaybackResumePolicy.create("id", "https://example.com/audio.mp3", "", 500))
        assertNull(PlaybackResumePolicy.create("id", "http://example.com/audio.mp3", "", 500))
    }

    @Test fun refusesMissingOrMalformedLocalAuthority() {
        assertNull(PlaybackResumePolicy.create("id", "content://", "", 500))
        assertNull(PlaybackResumePolicy.create("id", "content:///hello", "", 500))
        assertNull(PlaybackResumePolicy.create("id", "file://bad", "", 500))
    }

    @Test fun refusesMissingOrTooLongIdentity() {
        assertNull(PlaybackResumePolicy.create(" ", "content://media/track/1", "", 100))
        assertNull(PlaybackResumePolicy.create("x".repeat(257), "content://media/track/1", "", 100))
        assertNull(PlaybackResumePolicy.create("id", "content://media/" + "z".repeat(5000), "", 100))
    }

    @Test fun clampsCorruptPositionAndLongTitle() {
        val tooLarge = PlaybackResumePolicy.create("id", "content://media/track/1", "X".repeat(800), Long.MAX_VALUE)!!
        assertEquals(PlaybackResumePolicy.MAX_POSITION_MS, tooLarge.positionMs)
        assertEquals(PlaybackResumePolicy.MAX_TITLE_LENGTH, tooLarge.title.length)
        assertEquals(0, PlaybackResumePolicy.create("id", "content://media/track/1", "A", -1)!!.positionMs)
    }

    @Test fun uriAllowlistIsExplicit() {
        assertTrue(PlaybackResumePolicy.isLocalUri("content://com.example.documents/document/abc"))
        assertFalse(PlaybackResumePolicy.isLocalUri("ftp://example.net/song.mp3"))
    }
}
