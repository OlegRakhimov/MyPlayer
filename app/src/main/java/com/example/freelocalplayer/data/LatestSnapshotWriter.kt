package com.example.freelocalplayer.data

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Single-writer, latest-snapshot persistence queue.
 *
 * submit() only replaces an immutable reference; serialization and disk writes happen
 * on the dedicated worker. Fast consecutive submissions are coalesced, but the latest
 * complete LibraryState is always written. awaitIdle() must only be called off-main.
 *
 * This writer intentionally does not own Activity lifecycle. A process-local instance
 * is retained by LibraryStore so an Activity rotation never cancels pending saves.
 */
internal class LatestSnapshotWriter<T : Any>(
    private val write: (T) -> Unit,
    private val onWriteFailure: (T, Throwable) -> Unit = { _, _ -> }
) {
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FLP-LibraryWriter").apply { isDaemon = true }
    }
    private var pending: T? = null
    // Receipts of superseded snapshots travel with the newest pending snapshot.
    // A callback is never invoked merely because submit() accepted an item.
    private val pendingReceipts = mutableListOf<(Result<Unit>) -> Unit>()
    private var running = false
    private var lastFailure: Throwable? = null

    fun submit(snapshot: T, onPersisted: ((Result<Unit>) -> Unit)? = null) {
        synchronized(lock) {
            pending = snapshot
            if (onPersisted != null) pendingReceipts.add(onPersisted)
            if (!running) {
                running = true
                try {
                    executor.execute(::drain)
                } catch (error: RuntimeException) {
                    running = false
                    pending = null
                    pendingReceipts.clear()
                    throw error
                }
            }
        }
    }

    private fun drain() {
        while (true) {
            val (next, receipts) = synchronized(lock) {
                val value = pending
                if (value == null) {
                    running = false
                    return
                }
                pending = null
                val callbacks = pendingReceipts.toList()
                pendingReceipts.clear()
                value to callbacks
            }
            val outcome: Result<Unit> = try {
                write(next)
                synchronized(lock) { lastFailure = null }
                Result.success(Unit)
            } catch (error: Throwable) {
                synchronized(lock) { lastFailure = error }
                try { onWriteFailure(next, error) } catch (_: Throwable) { /* preserve writer */ }
                Result.failure(error)
            }
            // A failed or slow observer must not prevent subsequent disk writes.
            receipts.forEach { callback ->
                try { callback(outcome) } catch (_: Throwable) { /* preserve writer */ }
            }
        }
    }

    /** Serial barrier. Also throws if the most recent disk write failed. */
    fun awaitIdle() {
        while (true) {
            // This Future is sequenced after any drain already enqueued.
            executor.submit { }.get(60, TimeUnit.SECONDS)
            val result = synchronized(lock) {
                Pair(!running && pending == null, lastFailure)
            }
            if (!result.first) continue
            if (result.second != null) {
                throw IOException("Не удалось сохранить медиатеку на устройство", result.second)
            }
            return
        }
    }
}
