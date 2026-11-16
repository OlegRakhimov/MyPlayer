package com.example.freelocalplayer.data.room

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LibraryStore
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 0-C.3-C: only the short-lived rollout preference is written. The catalog,
 * playlists, lyrics, SAF permission grants and music database are not edited.
 * The PowerShell runner performs the actual cold start between these tests.
 */
@RunWith(AndroidJUnit4::class)
class TemporaryRoomReadRolloutDeviceTest {
    private val tag = "FLP_ROOM_C3C"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("local_library_v1", Context.MODE_PRIVATE)

    private fun canonicalSha(ctx: Context): String {
        val raw = prefs(ctx).getString("library", null)
        assertTrue("Missing canonical JSON: reject rollout", !raw.isNullOrBlank())
        val bytes = raw!!.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test fun armThreeMinuteVerifiedRoomLease() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(ctx)
        store.awaitPendingWrites()
        val p = prefs(ctx)
        assertFalse("Existing rollout flag must be OFF before a new QA session",
            p.getBoolean(RoomReadRolloutWindow.ENABLED_KEY, false))
        val baseline = canonicalSha(ctx)
        val preview = store.previewVerifiedRoomReadWithoutRepairForQa()
        assertTrue("REAL_CATALOG_ROOM_NOT_VERIFIED: refuse to arm", preview.verifiedRoom)
        assertTrue("Do not enable rollout for an empty library", preview.state.media.isNotEmpty())
        val now = System.currentTimeMillis()
        val expires = now + RoomReadRolloutWindow.MAX_LEASE_MS
        assertTrue("Could not atomically save the bounded QA lease",
            p.edit().putLong(RoomReadRolloutWindow.EXPIRY_KEY, expires)
                .putBoolean(RoomReadRolloutWindow.ENABLED_KEY, true).commit())
        assertEquals(RoomReadRolloutWindow.Status.ACTIVE,
            RoomReadRolloutWindow.evaluate(true, expires, System.currentTimeMillis()))
        Log.i(tag, "ROLLOUT_ARMED=PASS BASELINE_JSON_SHA256=$baseline " +
            "tracks=${preview.state.media.size} expiresAtMs=$expires")
    }

    @Test fun verifyLeaseAndRealCatalogAfterColdStart() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val p = prefs(ctx)
        val requested = p.getBoolean(RoomReadRolloutWindow.ENABLED_KEY, false)
        val expires = p.getLong(RoomReadRolloutWindow.EXPIRY_KEY, 0L)
        assertEquals("Rollout lease did not survive cold start",
            RoomReadRolloutWindow.Status.ACTIVE,
            RoomReadRolloutWindow.evaluate(requested, expires, System.currentTimeMillis()))
        val store = LibraryStore(ctx)
        store.awaitPendingWrites()
        val decision = store.previewVerifiedRoomReadWithoutRepairForQa()
        assertTrue("Catalog changed or Room became stale", decision.verifiedRoom)
        Log.i(tag, "COLD_START_SESSION_GATE=PASS CURRENT_JSON_SHA256=${canonicalSha(ctx)} " +
            "tracks=${decision.state.media.size}")
    }

    @Test fun revokeTemporaryLeaseAndVerifyJsonPresent() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val p = prefs(ctx)
        val cleared = p.edit().putBoolean(RoomReadRolloutWindow.ENABLED_KEY, false)
            .remove(RoomReadRolloutWindow.EXPIRY_KEY).commit()
        assertTrue("Cannot disable rollout", cleared)
        assertFalse("Room read flag remains ON",
            p.getBoolean(RoomReadRolloutWindow.ENABLED_KEY, false))
        assertFalse("Expiration must be removed", p.contains(RoomReadRolloutWindow.EXPIRY_KEY))
        Log.i(tag, "ROLLOUT_REVOKED=PASS FINAL_JSON_SHA256=${canonicalSha(ctx)}")
    }

    @Test fun expiredLeasePolicyDoesNotPermitRoom() {
        val now = System.currentTimeMillis()
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now - 1L, now))
        assertEquals(RoomReadRolloutWindow.Status.DISABLED,
            RoomReadRolloutWindow.evaluate(false, now + 1000L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now + RoomReadRolloutWindow.MAX_LEASE_MS + 1L, now))
        Log.i(tag, "LEASE_FAIL_CLOSED_POLICY=PASS")
    }
}
