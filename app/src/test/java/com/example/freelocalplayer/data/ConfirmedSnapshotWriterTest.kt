package com.example.freelocalplayer.data

import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 0-C.4: an acknowledgement means a completed write, never an enqueued write. */
class ConfirmedSnapshotWriterTest {
    @Test fun noSuccessBeforeWriteFinishes() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val confirmed = CountDownLatch(1)
        val writer = LatestSnapshotWriter<String>({
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        })
        writer.submit("lyrics") { result ->
            assertTrue(result.isSuccess)
            confirmed.countDown()
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertFalse("An enqueued edit must not be reported as saved", confirmed.await(80, TimeUnit.MILLISECONDS))
        release.countDown()
        writer.awaitIdle()
        assertTrue(confirmed.await(5, TimeUnit.SECONDS))
    }

    @Test fun coalescedRequestsGetReceiptsForActuallyWrittenLatestSnapshot() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val writes = Collections.synchronizedList(mutableListOf<Int>())
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        val writer = LatestSnapshotWriter<Int>({ value ->
            writes.add(value)
            if (value == 0) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        })
        writer.submit(0)
        assertTrue(started.await(5, TimeUnit.SECONDS))
        writer.submit(1) { outcome -> results.add(outcome.isSuccess); completed.countDown() }
        writer.submit(2) { outcome -> results.add(outcome.isSuccess); completed.countDown() }
        assertFalse(completed.await(80, TimeUnit.MILLISECONDS))
        release.countDown()
        writer.awaitIdle()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(0, 2), writes.toList())
        assertEquals(listOf(true, true), results.toList())
    }

    @Test fun failedDiskWriteDoesNotProduceSuccessReceipt() {
        val failure = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val writer = LatestSnapshotWriter<Int>({ throw IOException("injected commit failure") })
        writer.submit(7) { outcome ->
            if (outcome.isFailure) errors.incrementAndGet()
            failure.countDown()
        }
        try { writer.awaitIdle() } catch (_: IOException) { /* expected */ }
        assertTrue(failure.await(5, TimeUnit.SECONDS))
        assertEquals(1, errors.get())
    }

    @Test fun successfulRetryGetsReceiptAfterEarlierFailure() {
        val failed = CountDownLatch(1)
        val succeeded = CountDownLatch(1)
        val writer = LatestSnapshotWriter<Int>({ if (it == 1) throw IOException("first") })
        writer.submit(1) { outcome -> if (outcome.isFailure) failed.countDown() }
        try { writer.awaitIdle() } catch (_: IOException) { /* expected */ }
        assertTrue(failed.await(5, TimeUnit.SECONDS))
        writer.submit(2) { outcome -> if (outcome.isSuccess) succeeded.countDown() }
        writer.awaitIdle()
        assertTrue(succeeded.await(5, TimeUnit.SECONDS))
    }
}
