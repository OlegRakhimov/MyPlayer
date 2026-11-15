package com.example.freelocalplayer.data.room

import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedRoomReadSelectorTest {
    private val track = MediaEntry("original-uuid", "content://media/audio/1", "Песня 🎵", false,
        "[00:01.30]<00:01.45>слово", "mediastore:external:audio:1", 1872L, 9030L)
    private val archived = RetiredMedia(
        MediaEntry("retired-uuid", "content://media/audio/2", "Удалено", false, "LRC"),
        listOf(PlaylistPlace("list-1", 2))
    )
    private val catalog = LibraryState(
        listOf(track),
        listOf(UserPlaylist("list-1", "Мой список", listOf(track.id, track.id))),
        listOf(archived)
    )
    private val rows = ShadowRoomMapping.toRows(catalog)
    private val stamp = ShadowStamp(
        signature = ShadowRoomMapping.fingerprint(rows), tracks = 1, playlists = 1, retiredTracks = 1
    )

    @Test fun defaultDisabledReturnsExactJsonWithoutOpeningRoom() {
        var opened = false
        val decision = VerifiedRoomReadSelector.select(catalog, false) {
            opened = true
            rows to stamp
        }
        assertFalse(opened)
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(VerifiedRoomReadSelector.Reason.DISABLED, decision.reason)
        assertEquals(catalog, decision.state)
    }

    @Test fun validRoomSnapshotUsesSqliteRowsAndPreservesAllFields() {
        val decision = VerifiedRoomReadSelector.select(catalog, true) { rows to stamp }
        assertTrue(decision.verifiedRoom)
        assertEquals(VerifiedRoomReadSelector.Path.ROOM, decision.path)
        assertEquals(catalog, decision.state)
        assertEquals("original-uuid", decision.state.media.single().id)
        assertEquals("[00:01.30]<00:01.45>слово", decision.state.media.single().lyrics)
        assertEquals(listOf(track.id, track.id), decision.state.playlists.single().mediaIds)
        assertEquals("retired-uuid", decision.state.retiredMedia.single().entry.id)
        assertEquals(RoomPrimaryCutoverGate.Status.READY, decision.validation)
    }

    @Test fun staleRoomLyricsNeverReplaceNewerCanonicalJson() {
        val changed = catalog.copy(media = listOf(track.copy(lyrics = "New manual LRC")))
        val decision = VerifiedRoomReadSelector.select(changed, true) { rows to stamp }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(VerifiedRoomReadSelector.Reason.GATE_REJECTED, decision.reason)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_DATA_MISMATCH, decision.validation)
        assertEquals(changed, decision.state)
    }

    @Test fun emptyOrMissingStampIsNeverAnAuthority() {
        val decision = VerifiedRoomReadSelector.select(catalog, true) { rows to null }
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_STAMP_MISSING, decision.validation)
        assertEquals(catalog, decision.state)
        assertFalse(decision.verifiedRoom)
    }

    @Test fun wrongSchemaOrCountersSelectJson() {
        val schema = VerifiedRoomReadSelector.select(catalog, true) {
            rows to stamp.copy(schemaVersion = 2)
        }
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SCHEMA_MISMATCH, schema.validation)
        assertEquals(VerifiedRoomReadSelector.Path.JSON, schema.path)
        val counters = VerifiedRoomReadSelector.select(catalog, true) {
            rows to stamp.copy(retiredTracks = 0)
        }
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_COUNTERS_MISMATCH, counters.validation)
        assertEquals(catalog, counters.state)
    }

    @Test fun invalidSignatureFallsBackWithoutModifyingJson() {
        val decision = VerifiedRoomReadSelector.select(catalog, true) {
            rows to stamp.copy(signature = "f".repeat(64))
        }
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH, decision.validation)
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(catalog, decision.state)
    }

    @Test fun orphanRoomRowsAreRejectedEvenWhenReconstructedCatalogMatches() {
        val polluted = rows.copy(playlistItems = rows.playlistItems +
            ShadowPlaylistItem("unknown-list", 0, track.id))
        val decision = VerifiedRoomReadSelector.select(catalog, true) { polluted to stamp }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH, decision.validation)
        assertEquals(catalog, decision.state)
    }

    @Test fun corruptedRoomRowsFallBackToJson() {
        val bad = rows.copy(tracks = rows.tracks + rows.tracks.first())
        val decision = VerifiedRoomReadSelector.select(catalog, true) { bad to stamp }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_ROWS_INVALID, decision.validation)
        assertEquals(catalog, decision.state)
    }

    @Test fun failedSqliteReaderNeverReplacesCatalogWithEmptyState() {
        val decision = VerifiedRoomReadSelector.select(catalog, true) {
            throw IllegalStateException("Simulated SQLite disk I/O error")
        }
        assertEquals(VerifiedRoomReadSelector.Reason.ROOM_READ_FAILED, decision.reason)
        assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
        assertEquals(catalog, decision.state)
    }

    @Test fun emptyCatalogNeedsValidStampBeforeRoomCanBeUsed() {
        val empty = LibraryState()
        val emptyRows = ShadowRoomMapping.toRows(empty)
        val noStamp = VerifiedRoomReadSelector.select(empty, true) { emptyRows to null }
        assertFalse(noStamp.verifiedRoom)
        val valid = VerifiedRoomReadSelector.select(empty, true) {
            emptyRows to ShadowStamp(signature = ShadowRoomMapping.fingerprint(emptyRows),
                tracks = 0, playlists = 0, retiredTracks = 0)
        }
        assertTrue(valid.verifiedRoom)
        assertEquals(empty, valid.state)
    }

    @Test fun duplicateJsonUuidCannotBeLaunderedThroughRoom() {
        val invalid = catalog.copy(media = listOf(track, track))
        val result = VerifiedRoomReadSelector.select(invalid, true) { rows to stamp }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, result.path)
        assertEquals(RoomPrimaryCutoverGate.Status.INVALID_JSON_STATE, result.validation)
        assertEquals(invalid, result.state)
    }
}
