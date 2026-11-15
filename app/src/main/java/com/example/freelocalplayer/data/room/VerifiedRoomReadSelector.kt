package com.example.freelocalplayer.data.room

import com.example.freelocalplayer.data.LibraryState

/**
 * Stage 0-C.3-B: opt-in, fully verified Room read with JSON recovery.
 *
 * Never writes to SQLite, SharedPreferences or the user's media. The canonical
 * JSON snapshot is always available to recover from a missing, stale or corrupt
 * Room replica. It is an explicit verified-read rollout, NOT Room-only storage.
 */
object VerifiedRoomReadSelector {
    enum class Path { JSON, ROOM }

    enum class Reason {
        DISABLED,
        VERIFIED,
        GATE_REJECTED,
        ROOM_READ_FAILED
    }

    data class Decision(
        val state: LibraryState,
        val path: Path,
        val reason: Reason,
        val validation: RoomPrimaryCutoverGate.Status? = null
    ) {
        val verifiedRoom: Boolean get() = path == Path.ROOM && reason == Reason.VERIFIED
    }

    /**
     * The read callback must return rows and stamp from the SAME SQLite read
     * transaction. It is never called when the rollout is disabled.
     *
     * Compare canonical JSON with every Room row *before* accepting Room data.
     * An IOException, database failure or incompatible stamp must fall back to
     * the exact unmodified JSON state, not to an empty library.
     */
    fun select(
        canonical: LibraryState,
        enabled: Boolean,
        readRoom: () -> Pair<ShadowRowSet, ShadowStamp?>
    ): Decision {
        if (!enabled) {
            return Decision(canonical, Path.JSON, Reason.DISABLED)
        }
        return try {
            val (rows, stamp) = readRoom()
            val gate = RoomPrimaryCutoverGate.evaluate(canonical, rows, stamp)
            if (!gate.ready) {
                Decision(canonical, Path.JSON, Reason.GATE_REJECTED, gate.status)
            } else {
                // Gate already validated the complete semantic and hashed match.
                // Only a fully matching snapshot may become the in-memory source.
                Decision(rows.reconstruct(), Path.ROOM, Reason.VERIFIED, gate.status)
            }
        } catch (_: Exception) {
            // An exception from SQLite must not make a valid JSON library vanish.
            Decision(canonical, Path.JSON, Reason.ROOM_READ_FAILED)
        }
    }
}
