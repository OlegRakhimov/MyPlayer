package com.example.freelocalplayer.qa

import com.example.freelocalplayer.data.ConfirmedRetiredMediaRebind
import com.example.freelocalplayer.data.CanonicalLibraryStateGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs only for :app:testQaUnitTest, not the production debug APK. */
class RelinkQaFixtureTest {
    @Test fun fixtureIsValidAndContainsOnlySyntheticEvidence() {
        val state = RelinkQaFixture.create()
        CanonicalLibraryStateGuard.requireValid(state)
        assertEquals(1, state.retiredMedia.size)
        assertEquals(3, state.media.size)
        assertEquals(1, state.playlists.size)
        assertTrue(state.media.all { it.uri.startsWith("content://media/") })
        assertTrue(state.retiredMedia.all { it.entry.id.startsWith("qa-") })
        assertTrue(state.media.all { it.id.startsWith("qa-") })
    }

    @Test fun validCandidateCanPassPreview() {
        val state = RelinkQaFixture.create()
        assertEquals(
            ConfirmedRetiredMediaRebind.Refusal.NONE,
            ConfirmedRetiredMediaRebind.preview(
                state, RelinkQaFixture.OLD_UUID, RelinkQaFixture.NEW_UUID
            )
        )
    }

    @Test fun incorrectSizeCannotPassPreview() {
        val state = RelinkQaFixture.create()
        assertEquals(
            ConfirmedRetiredMediaRebind.Refusal.SIZE_MISMATCH,
            ConfirmedRetiredMediaRebind.preview(
                state, RelinkQaFixture.OLD_UUID, RelinkQaFixture.MISMATCH_UUID
            )
        )
    }

    @Test fun simulatedRebindRestoresOriginalUuidLyricsAndPlaylistWithoutMutatingFixture() {
        val source = RelinkQaFixture.create()
        val result = ConfirmedRetiredMediaRebind.confirmed(
            source, RelinkQaFixture.OLD_UUID,
            RelinkQaFixture.NEW_UUID, userConfirmed = true
        )
        assertTrue(result.media.any { it.id == RelinkQaFixture.OLD_UUID })
        assertFalse(result.media.any { it.id == RelinkQaFixture.NEW_UUID })
        assertEquals(
            RelinkQaFixture.SAVED_LYRICS,
            result.media.single { it.id == RelinkQaFixture.OLD_UUID }.lyrics
        )
        assertTrue(
            result.playlists.single { it.id == RelinkQaFixture.PLAYLIST_ID }
                .mediaIds.contains(RelinkQaFixture.OLD_UUID)
        )
        assertTrue(result.retiredMedia.isEmpty())
        assertEquals(1, source.retiredMedia.size)
        assertTrue(source.media.any { it.id == RelinkQaFixture.NEW_UUID })
    }

    @Test fun everyNewFixtureIsIndependent() {
        val first = RelinkQaFixture.create()
        val second = RelinkQaFixture.create()
        assertNotSame(first, second)
        assertNotSame(first.media, second.media)
        assertNotSame(first.playlists, second.playlists)
        assertEquals(first, second)
    }

    @Test fun noConfirmationCannotCommitEvenInMemory() {
        val source = RelinkQaFixture.create()
        var refused = false
        try {
            ConfirmedRetiredMediaRebind.confirmed(
                source, RelinkQaFixture.OLD_UUID,
                RelinkQaFixture.NEW_UUID, userConfirmed = false
            )
        } catch (expected: IllegalArgumentException) {
            refused = expected.message?.contains("EXPLICIT_USER_CONFIRMATION_REQUIRED") == true
        }
        assertTrue(refused)
        assertEquals(1, source.retiredMedia.size)
    }
}
