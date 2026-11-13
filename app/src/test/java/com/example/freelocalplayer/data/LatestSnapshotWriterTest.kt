package com.example.freelocalplayer.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestSnapshotWriterTest {
    @Test fun writesLatestSnapshotAndDrainsBeforeBarrier() {
        val values = mutableListOf<Int>()
        val writer = LatestSnapshotWriter<Int>({ value -> synchronized(values) { values.add(value) } })
        writer.submit(1)
        writer.submit(2)
        writer.submit(3)
        writer.awaitIdle()
        assertEquals(3, synchronized(values) { values.last() })
    }

    @Test fun rapidUpdatesCoalesceWithoutOverwritingNewest() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val values = mutableListOf<Int>()
        val writer = LatestSnapshotWriter<Int>({ value ->
            synchronized(values) { values.add(value) }
            if (value == 0) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        })
        writer.submit(0)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        for (value in 1..1000) writer.submit(value)
        release.countDown()
        writer.awaitIdle()
        assertEquals(listOf(0, 1000), synchronized(values) { values.toList() })
    }

    @Test fun savingRunsOffTheCallingThread() {
        val caller = Thread.currentThread().name
        var worker = ""
        val writer = LatestSnapshotWriter<String>({ worker = Thread.currentThread().name })
        writer.submit("item")
        writer.awaitIdle()
        assertFalse(worker == caller)
        assertTrue(worker.contains("FLP-LibraryWriter"))
    }

    @Test fun failedWriteIsReportedByBarrier() {
        val writer = LatestSnapshotWriter<Int>({ throw IOException("disk error") })
        writer.submit(5)
        var failed = false
        try { writer.awaitIdle() } catch (error: IOException) { failed = true }
        assertTrue(failed)
    }

    @Test fun newerSuccessClearsPreviousFailure() {
        val failures = AtomicInteger(0)
        val writer = LatestSnapshotWriter<Int>({ value ->
            if (value == 1) throw IOException("first write failed")
        }, { _, _ -> failures.incrementAndGet() })
        writer.submit(1)
        try { writer.awaitIdle() } catch (_: IOException) { /* expected */ }
        writer.submit(2)
        writer.awaitIdle()
        assertEquals(1, failures.get())
    }

    @Test fun failureCallbackCannotKillWriter() {
        val success = AtomicInteger(0)
        val writer = LatestSnapshotWriter<Int>({ value ->
            if (value == 1) throw IllegalStateException("fail")
            success.incrementAndGet()
        }, { _, _ -> throw IllegalArgumentException("callback failed") })
        writer.submit(1)
        try { writer.awaitIdle() } catch (_: IOException) { /* expected */ }
        writer.submit(2)
        writer.awaitIdle()
        assertEquals(1, success.get())
    }

    @Test fun barrierWaitsForInFlightWrite() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val writer = LatestSnapshotWriter<Int>({
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        })
        writer.submit(1)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val waiting = Thread {
            writer.awaitIdle()
            finished.countDown()
        }
        waiting.start()
        assertFalse(finished.await(50, TimeUnit.MILLISECONDS))
        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        waiting.join()
    }
}
