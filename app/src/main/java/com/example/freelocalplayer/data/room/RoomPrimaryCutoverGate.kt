package com.example.freelocalplayer.data.room

import com.example.freelocalplayer.data.LibraryState

/**
 * Stage 0-C.3-A: a fail-closed, read-only check before any future Room-primary cutover.
 * It deliberately does not open a database, mutate rows or switch the source of truth.
 */
object RoomPrimaryCutoverGate {
    enum class Status {
        READY,
        INVALID_JSON_STATE,
        ROOM_ROWS_INVALID,
        ROOM_STAMP_MISSING,
        ROOM_SCHEMA_MISMATCH,
        ROOM_COUNTERS_MISMATCH,
        ROOM_DATA_MISMATCH,
        ROOM_SIGNATURE_MISMATCH
    }

    data class Result(
        val status: Status,
        val tracks: Int,
        val playlists: Int,
        val retiredTracks: Int
    ) {
        val ready: Boolean get() = status == Status.READY
    }

    /**
     * Only a *complete* semantic match to the canonical JSON plus a matching
     * transaction stamp can pass. A missing stamp never signifies an empty DB.
     * Never includes lyrics, names, URIs or identifiers in the result.
     */
    fun evaluate(canonical: LibraryState, sqlRows: ShadowRowSet, stamp: ShadowStamp?): Result {
        fun result(status: Status) = Result(
            status, canonical.media.size, canonical.playlists.size, canonical.retiredMedia.size
        )

        val expected = try {
            ShadowRoomMapping.toRows(canonical)
        } catch (_: IllegalArgumentException) {
            return result(Status.INVALID_JSON_STATE)
        } catch (_: IllegalStateException) {
            return result(Status.INVALID_JSON_STATE)
        }
        val actual = try {
            sqlRows.reconstruct()
        } catch (_: IllegalArgumentException) {
            return result(Status.ROOM_ROWS_INVALID)
        } catch (_: IllegalStateException) {
            return result(Status.ROOM_ROWS_INVALID)
        }
        if (stamp == null) return result(Status.ROOM_STAMP_MISSING)
        if (stamp.singleton != 1 || stamp.schemaVersion != 1) {
            return result(Status.ROOM_SCHEMA_MISMATCH)
        }
        if (stamp.tracks != canonical.media.size ||
            stamp.playlists != canonical.playlists.size ||
            stamp.retiredTracks != canonical.retiredMedia.size) {
            return result(Status.ROOM_COUNTERS_MISMATCH)
        }
        if (actual != canonical) return result(Status.ROOM_DATA_MISMATCH)
        val expectedSignature = ShadowRoomMapping.fingerprint(expected)
        val actualSignature = ShadowRoomMapping.fingerprint(sqlRows)
        if (actualSignature != expectedSignature || stamp.signature != expectedSignature) {
            return result(Status.ROOM_SIGNATURE_MISMATCH)
        }
        return result(Status.READY)
    }
}
