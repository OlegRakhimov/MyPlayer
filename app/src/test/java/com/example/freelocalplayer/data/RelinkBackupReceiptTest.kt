package com.example.freelocalplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory only. No Android permissions, real ZIP, user library or Room. */
class RelinkBackupReceiptTest {
    private fun catalog() = LibraryState(
        media = listOf(MediaEntry(id = "a", uri = "content://media/external/audio/media/1",
            title = "Track.mp3", isVideo = false))
    )

    @Test fun sameSnapshotAndUnchangedAttachmentsAllowPreview() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, listOf("a"), setOf("a"))
        assertTrue(receipt.matches(catalog, 3, listOf("a"), setOf("a"), 0))
    }

    @Test fun aStructurallyEqualButDifferentSnapshotInvalidatesOldZip() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, emptyList(), emptySet())
        assertFalse(receipt.matches(catalog.copy(), 3, emptyList(), emptySet(), 0))
    }

    @Test fun changingMusicGenerationRequiresFreshZip() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, emptyList(), emptySet())
        assertFalse(receipt.matches(catalog, 4, emptyList(), emptySet(), 0))
    }

    @Test fun writingMidiOrChordsInBackgroundBlocksConfirmation() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, emptyList(), emptySet())
        assertFalse(receipt.matches(catalog, 3, emptyList(), emptySet(), 1))
        assertFalse(receipt.matches(catalog, 3, emptyList(), emptySet(), 2))
    }

    @Test fun changingRecentHistoryRequiresFreshZip() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, listOf("a"), emptySet())
        assertFalse(receipt.matches(catalog, 3, listOf("b"), emptySet(), 0))
    }

    @Test fun changingFavoritesRequiresFreshZip() {
        val catalog = catalog()
        val receipt = RelinkBackupReceipt.capture(catalog, 3, emptyList(), setOf("a"))
        assertFalse(receipt.matches(catalog, 3, emptyList(), setOf("b"), 0))
    }

    @Test fun historySnapshotDoesNotAliasMutableInputList() {
        val catalog = catalog()
        val recent = mutableListOf("a")
        val receipt = RelinkBackupReceipt.capture(catalog, 3, recent, emptySet())
        recent.add("b")
        assertTrue(receipt.matches(catalog, 3, listOf("a"), emptySet(), 0))
        assertFalse(receipt.matches(catalog, 3, recent, emptySet(), 0))
    }

    @Test fun favoriteSnapshotDoesNotAliasMutableInputSet() {
        val catalog = catalog()
        val favorites = mutableSetOf("a")
        val receipt = RelinkBackupReceipt.capture(catalog, 3, emptyList(), favorites)
        favorites.add("b")
        assertTrue(receipt.matches(catalog, 3, emptyList(), setOf("a"), 0))
        assertFalse(receipt.matches(catalog, 3, emptyList(), favorites, 0))
    }
}
