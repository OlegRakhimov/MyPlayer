package com.example.freelocalplayer.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LatestSnapshotWriter
import com.example.freelocalplayer.lyrics.EnhancedLrcWordAdjuster
import com.example.freelocalplayer.lyrics.LrcTimingAdjuster
import com.example.freelocalplayer.lyrics.LyricLineSync
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only uniquely named test prefs, never production JSON/Room or SAF data. */
@RunWith(AndroidJUnit4::class)
class TimingConfirmedDeviceTest {
    @Test fun lineTimingAcknowledgesOnlyAfterRealIsolatedCommit() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "__flp_0c41_timing_${UUID.randomUUID()}"
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val start = "[00:01.00]First\n[00:04.00]Second"
        val updated = LrcTimingAdjuster.retime(start, 0, 1200L, 8000L)!!
        val committed = CountDownLatch(1)
        val receipt = CountDownLatch(1)
        try {
            val writer = LatestSnapshotWriter<String>({ value ->
                if (!prefs.edit().putString("timing", value).commit()) {
                    throw IOException("QA commit failed")
                }
                committed.countDown()
            })
            writer.submit(updated) { result -> if (result.isSuccess) receipt.countDown() }
            writer.awaitIdle()
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            assertTrue(receipt.await(5, TimeUnit.SECONDS))
            assertEquals(updated, prefs.getString("timing", null))
        } finally {
            assertTrue(ctx.deleteSharedPreferences(name))
        }
    }

    @Test fun wordTimingInjectedFailureKeepsDraftRetryable() {
        val source = "[00:02.00]<00:02.00>We <00:02.40>sing <00:02.90>now"
        val update = EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 2500L, 7000L)!!
        var edit = TimingSaveReceiptState(source).start()
        val failure = CountDownLatch(1)
        val failing = LatestSnapshotWriter<String>({ throw IOException("synthetic commit failure") })
        failing.submit(update) { result -> if (result.isFailure) failure.countDown() }
        try { failing.awaitIdle() } catch (_: IOException) { /* expected */ }
        assertTrue(failure.await(5, TimeUnit.SECONDS))
        edit = edit.complete(false)
        assertTrue(edit.failed)
        assertFalse(edit.saving)
        assertEquals(source, edit.sourceLyrics)
        edit = edit.start().complete(true)
        assertEquals(TimingSaveReceiptState.Phase.SAVED, edit.phase)
    }

    @Test fun txtToLrcReceiptWaitsForSyntheticWrite() {
        val source = "First\nSecond"
        val lrc = LyricLineSync.toLrc(LyricLineSync.lines(source), listOf(1200L, 2600L))
        val entered = CountDownLatch(1)
        val allow = CountDownLatch(1)
        val confirmed = CountDownLatch(1)
        val writer = LatestSnapshotWriter<String>({
            entered.countDown()
            check(allow.await(5, TimeUnit.SECONDS))
        })
        val edit = TimingSaveReceiptState(source).start()
        try {
            writer.submit(lrc) { if (it.isSuccess) confirmed.countDown() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(confirmed.await(75, TimeUnit.MILLISECONDS))
        } finally { allow.countDown() }
        writer.awaitIdle()
        assertTrue(confirmed.await(5, TimeUnit.SECONDS))
        assertEquals(TimingSaveReceiptState.Phase.SAVED, edit.complete(true).phase)
    }
}
