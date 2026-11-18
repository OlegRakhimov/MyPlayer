package com.example.freelocalplayer.data

import java.security.MessageDigest

/**
 * A conservative decision for one staged, whole-library JSON write.
 *
 * A matching BASE hash is the only authority to replay a pending write.
 * A matching TARGET hash proves that the normal commit already finished.
 * Any other hash is a conflict: never overwrite a newer or different catalog.
 */
internal object PendingLibraryRecoveryPolicy {
    enum class Decision { REPLAY, ALREADY_COMMITTED, CONFLICT }

    fun fingerprint(raw: String?): String {
        if (raw == null) return "ABSENT"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    fun decide(baseFingerprint: String, targetFingerprint: String, currentRaw: String?): Decision {
        val current = fingerprint(currentRaw)
        return when (current) {
            targetFingerprint -> Decision.ALREADY_COMMITTED
            baseFingerprint -> Decision.REPLAY
            else -> Decision.CONFLICT
        }
    }
}
