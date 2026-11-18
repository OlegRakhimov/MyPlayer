package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PendingLibraryRecoveryPolicyTest {
    @Test fun unchangedBaseAllowsRecovery() {
        val base = "{\"media\":[],\"playlists\":[]}"
        val desired = "{\"media\":[],\"playlists\":[],\"retiredMedia\":[]}"
        assertEquals(PendingLibraryRecoveryPolicy.Decision.REPLAY,
            PendingLibraryRecoveryPolicy.decide(
                PendingLibraryRecoveryPolicy.fingerprint(base),
                PendingLibraryRecoveryPolicy.fingerprint(desired), base))
    }

    @Test fun completedCanonicalCommitMustNeverReplay() {
        val original = "old"
        val saved = "saved"
        assertEquals(PendingLibraryRecoveryPolicy.Decision.ALREADY_COMMITTED,
            PendingLibraryRecoveryPolicy.decide(
                PendingLibraryRecoveryPolicy.fingerprint(original),
                PendingLibraryRecoveryPolicy.fingerprint(saved), saved))
    }

    @Test fun newerCatalogIsNotOverwritten() {
        assertEquals(PendingLibraryRecoveryPolicy.Decision.CONFLICT,
            PendingLibraryRecoveryPolicy.decide(
                PendingLibraryRecoveryPolicy.fingerprint("before"),
                PendingLibraryRecoveryPolicy.fingerprint("pending"), "newer"))
    }

    @Test fun absentIsDifferentFromEmpty() {
        assertEquals("ABSENT", PendingLibraryRecoveryPolicy.fingerprint(null))
        assertNotEquals("ABSENT", PendingLibraryRecoveryPolicy.fingerprint(""))
    }

    @Test fun firstInstallCanRecoverOnlyWhenPreferenceAbsent() {
        val target = PendingLibraryRecoveryPolicy.fingerprint("fresh library")
        assertEquals(PendingLibraryRecoveryPolicy.Decision.REPLAY,
            PendingLibraryRecoveryPolicy.decide("ABSENT", target, null))
        assertEquals(PendingLibraryRecoveryPolicy.Decision.CONFLICT,
            PendingLibraryRecoveryPolicy.decide("ABSENT", target, ""))
    }

    @Test fun digestIsDeterministicAndDetectsOneCharacterChange() {
        val baseline = PendingLibraryRecoveryPolicy.fingerprint("[00:01.00]word")
        assertEquals(baseline, PendingLibraryRecoveryPolicy.fingerprint("[00:01.00]word"))
        assertNotEquals(baseline, PendingLibraryRecoveryPolicy.fingerprint("[00:01.01]word"))
        assertEquals(64, baseline.length)
    }
}
