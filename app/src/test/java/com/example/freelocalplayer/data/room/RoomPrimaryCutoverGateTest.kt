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

class RoomPrimaryCutoverGateTest {
    private val song = MediaEntry("stable-uuid", "content://media/audio/5", "Song", false,
        "[00:01.00]line <00:01.2>word", "mediastore:external:audio:5", 400L, 900L)
    private val retired = RetiredMedia(
        MediaEntry("retired-uuid", "content://media/audio/6", "Archived", false, "TXT"),
        listOf(PlaylistPlace("playlist-uuid", 1))
    )
    private val source = LibraryState(
        listOf(song),
        listOf(UserPlaylist("playlist-uuid", "Ordered", listOf(song.id, song.id))),
        listOf(retired)
    )
    private val goodRows = ShadowRoomMapping.toRows(source)
    private val goodStamp = ShadowStamp(
        signature = ShadowRoomMapping.fingerprint(goodRows),
        tracks = 1, playlists = 1, retiredTracks = 1
    )
    private fun gate(rows: ShadowRowSet = goodRows, stamp: ShadowStamp? = goodStamp,
                     json: LibraryState = source) = RoomPrimaryCutoverGate.evaluate(json, rows, stamp)

    @Test fun matchingRowsAndStampAllowCutoverPreflight() {
        val check = gate()
        assertTrue(check.ready)
        assertEquals(RoomPrimaryCutoverGate.Status.READY, check.status)
        assertEquals(1, check.retiredTracks)
    }

    @Test fun noStampMeansNotReadyEvenIfBothLibrariesAreEmpty() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_STAMP_MISSING, gate(stamp = null).status)
        val empty = LibraryState()
        assertFalse(RoomPrimaryCutoverGate.evaluate(empty, ShadowRoomMapping.toRows(empty), null).ready)
    }

    @Test fun incompatibleRoomSchemaOrSingletonIsRejected() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SCHEMA_MISMATCH,
            gate(stamp = goodStamp.copy(schemaVersion = 2)).status)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SCHEMA_MISMATCH,
            gate(stamp = goodStamp.copy(singleton = 2)).status)
    }

    @Test fun mismatchedCountersRejectEvenIfRowsMatch() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_COUNTERS_MISMATCH,
            gate(stamp = goodStamp.copy(tracks = 0)).status)
    }

    @Test fun mutatedLyricsAndPlaylistOrderRejectRoomPrimary() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_DATA_MISMATCH,
            gate(json = source.copy(media = listOf(song.copy(lyrics = "changed")))).status)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_DATA_MISMATCH,
            gate(json = source.copy(playlists = listOf(
                UserPlaylist("playlist-uuid", "Ordered", listOf(song.id))
            ))).status)
    }

    @Test fun orphanedRowsAndStaleSignatureRejectRoomPrimary() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH,
            gate(rows = goodRows.copy(playlistItems = goodRows.playlistItems +
                ShadowPlaylistItem("unknown-list", 0, song.id))).status)
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH,
            gate(stamp = goodStamp.copy(signature = "0".repeat(64))).status)
    }

    @Test fun invalidCanonicalDuplicateUuidIsRejected() {
        assertEquals(RoomPrimaryCutoverGate.Status.INVALID_JSON_STATE,
            gate(json = source.copy(media = listOf(song, song))).status)
    }

    @Test fun duplicatedRoomPositionIsRejectedWithoutException() {
        assertEquals(RoomPrimaryCutoverGate.Status.ROOM_ROWS_INVALID,
            gate(rows = goodRows.copy(playlistItems = goodRows.playlistItems + goodRows.playlistItems[0])).status)
    }
}
