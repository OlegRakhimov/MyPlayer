package com.example.freelocalplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only uniquely named QA prefs; never opens local_library_v1 or production Room. */
@RunWith(AndroidJUnit4::class)
class ConfirmedLibraryWriteDeviceTest {
    @Test fun realAndroidCommitAcknowledgedOnlyAfterIsolatedWrite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "__flp_0c4_qa_${UUID.randomUUID().toString().replace("-", "")}" 
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = CountDownLatch(1)
        val callbackSuccess = CountDownLatch(1)
        try {
            val writer = LatestSnapshotWriter<String>({ text ->
                if (!prefs.edit().putString("test_lyrics", text).commit()) {
                    throw IOException("Isolated SharedPreferences commit failed")
                }
                saved.countDown()
            })
            writer.submit("[00:01.10]сохранённый тест") { result ->
                if (result.isSuccess) callbackSuccess.countDown()
            }
            writer.awaitIdle()
            assertTrue(saved.await(5, TimeUnit.SECONDS))
            assertTrue(callbackSuccess.await(5, TimeUnit.SECONDS))
            assertEquals("[00:01.10]сохранённый тест", prefs.getString("test_lyrics", null))
        } finally {
            assertTrue("Isolated QA prefs deletion failed", context.deleteSharedPreferences(prefsName))
        }
    }

    @Test fun injectedFailureKeepsSyntheticUserEditUnconfirmed() {
        val failed = CountDownLatch(1)
        val writer = LatestSnapshotWriter<String>({ throw IOException("synthetic disk failure") })
        writer.submit("unsaved") { result -> if (result.isFailure) failed.countDown() }
        try { writer.awaitIdle() } catch (_: IOException) { /* intended */ }
        assertTrue(failed.await(5, TimeUnit.SECONDS))
    }

    @Test fun receiptOfSupersededSnapshotWaitsForFinalCommit() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val allReceipts = CountDownLatch(2)
        val committed = mutableListOf<Int>()
        val writer = LatestSnapshotWriter<Int>({ value ->
            if (value == 0) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            synchronized(committed) { committed.add(value) }
        })
        writer.submit(0)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        writer.submit(1) { if (it.isSuccess) allReceipts.countDown() }
        writer.submit(2) { if (it.isSuccess) allReceipts.countDown() }
        assertFalse(allReceipts.await(75, TimeUnit.MILLISECONDS))
        release.countDown()
        writer.awaitIdle()
        assertTrue(allReceipts.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(0, 2), synchronized(committed) { committed.toList() })
    }
}
