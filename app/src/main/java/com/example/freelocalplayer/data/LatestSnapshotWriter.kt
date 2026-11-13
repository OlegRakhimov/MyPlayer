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
    private var running = false
    private var lastFailure: Throwable? = null

    fun submit(snapshot: T) {
        synchronized(lock) {
            pending = snapshot
            if (!running) {
                running = true
                try {
                    executor.execute(::drain)
                } catch (error: RuntimeException) {
                    running = false
                    pending = null
                    throw error
                }
            }
        }
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val value = pending
                if (value == null) {
                    running = false
                    return
                }
                pending = null
                value
            }
            try {
                write(next)
                synchronized(lock) { lastFailure = null }
            } catch (error: Throwable) {
                synchronized(lock) { lastFailure = error }
                try { onWriteFailure(next, error) } catch (_: Throwable) { /* preserve writer */ }
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
