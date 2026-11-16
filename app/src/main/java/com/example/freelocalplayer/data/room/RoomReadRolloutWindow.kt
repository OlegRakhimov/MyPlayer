package com.example.freelocalplayer.data.room

/**
 * Stage 0-C.3-C: fail-closed expiration for temporary Room-read QA.
 * This is NOT the Room-primary storage migration. JSON remains canonical.
 * The short lease also limits exposure if ADB disconnects or the test process dies.
 */
object RoomReadRolloutWindow {
    const val ENABLED_KEY = "room_verified_read_0c3b"
    const val EXPIRY_KEY = "room_verified_read_expires_0c3c_ms"
    const val MAX_LEASE_MS = 180_000L

    enum class Status { DISABLED, ACTIVE, EXPIRED_OR_INVALID }

    fun evaluate(enabled: Boolean, expiresAtMs: Long, nowMs: Long): Status {
        if (!enabled) return Status.DISABLED
        // No valid lease, system-clock rollback, arithmetic overflow or a long
        // expiration must never turn Room read on indefinitely.
        if (expiresAtMs <= nowMs) return Status.EXPIRED_OR_INVALID
        val remaining = expiresAtMs - nowMs
        return if (remaining in 1L..MAX_LEASE_MS) Status.ACTIVE
        else Status.EXPIRED_OR_INVALID
    }
}
