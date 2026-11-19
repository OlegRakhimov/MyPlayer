package com.example.freelocalplayer.data

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 0-C.5.2: measure stage(), commit(), and clear() separately.
 *
 * Tests run only with unique QA SharedPreferences and QA files under filesDir.
 * They do not use LibraryStore, local_library_v1, Room or production media.
 * The main app APK is NOT replaced.
 *
 * Measured segment deliberately excludes JSON construction, strict decoding,
 * LibraryState encoding, Room mirroring and UI rendering.
 */
@RunWith(AndroidJUnit4::class)
class LibraryJournalStageCostDeviceTest {
    private val tag = "FLP_JOURNAL_BREAKDOWN"
    private val samples = 24
    private val warmups = 3

    @Test fun measure630Tracks() = benchmark(630)

    @Test fun measure5000Tracks() = benchmark(5_000)

    @Test fun measure20000Tracks() = benchmark(20_000)

    private data class Stages(
        val stageNs: Long,
        val commitNs: Long,
        val clearNs: Long
    ) {
        val totalNs: Long get() = stageNs + commitNs + clearNs
    }

    private fun benchmark(trackCount: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = UUID.randomUUID().toString().replace("-", "")
        val prefName = "__flp_0c52_breakdown_$token"
        val directory = File(context.filesDir, "__flp_0c52_journal_$token")
        assertTrue("QA directory cannot be created", directory.mkdirs())
        assertTrue(
            "QA path outside application private files",
            directory.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)
        )

        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val journal = LibraryPendingWriteJournal(directory)

        try {
            val a = syntheticLibrary(trackCount, "A")
            val b = syntheticLibrary(trackCount, "B")
            assertEquals(trackCount, CanonicalLibraryJsonDecoder.decode(a, true).media.size)
            assertEquals(trackCount, CanonicalLibraryJsonDecoder.decode(b, true).media.size)
            assertTrue("QA initial commit failed", prefs.edit().putString("library", a).commit())
            var current = a

            fun next(): String = if (current === a) b else a

            fun directWrite(): Long {
                val target = next()
                val startNs = SystemClock.elapsedRealtimeNanos()
                val ok = prefs.edit().putString("library", target).commit()
                val elapsedNs = SystemClock.elapsedRealtimeNanos() - startNs
                assertTrue("QA direct commit failed", ok)
                assertEquals(target, prefs.getString("library", null))
                current = target
                return elapsedNs
            }

            fun journalWrite(): Stages {
                val target = next()
                val stageStart = SystemClock.elapsedRealtimeNanos()
                journal.stage(current, target)
                val stageEnd = SystemClock.elapsedRealtimeNanos()
                val ok = prefs.edit().putString("library", target).commit()
                val commitEnd = SystemClock.elapsedRealtimeNanos()
                assertTrue("QA journaled canonical commit failed", ok)
                journal.clear()
                val clearEnd = SystemClock.elapsedRealtimeNanos()
                assertFalse(
                    "QA recovery journal unexpectedly left behind",
                    File(directory, LibraryPendingWriteJournal.FILE_NAME).exists()
                )
                assertEquals(target, prefs.getString("library", null))
                current = target
                return Stages(
                    stageNs = stageEnd - stageStart,
                    commitNs = commitEnd - stageEnd,
                    clearNs = clearEnd - commitEnd
                )
            }

            repeat(warmups) {
                directWrite()
                journalWrite()
            }

            val direct = LongArray(samples)
            val stage = LongArray(samples)
            val commit = LongArray(samples)
            val clear = LongArray(samples)
            val full = LongArray(samples)

            fun recordJournal(index: Int) {
                val result = journalWrite()
                stage[index] = result.stageNs
                commit[index] = result.commitNs
                clear[index] = result.clearNs
                full[index] = result.totalNs
            }

            // Alternating order reduces systematic "direct always first" bias.
            repeat(samples) { index ->
                if (index % 2 == 0) {
                    direct[index] = directWrite()
                    recordJournal(index)
                } else {
                    recordJournal(index)
                    direct[index] = directWrite()
                }
            }

            assertTrue(direct.all { it > 0 })
            assertTrue(stage.all { it > 0 })
            assertTrue(commit.all { it > 0 })
            assertTrue(clear.all { it > 0 })
            assertTrue(full.all { it > 0 })
            assertEquals(
                LibraryPendingWriteJournal.Recovery.NONE,
                journal.recover(current, validate = { CanonicalLibraryJsonDecoder.decode(it, true); Unit },
                    commit = { false })
            )

            val metric = String.format(
                Locale.US,
                "BREAKDOWN_RESULT tracks=%d samples=%d warmups=%d payload_bytes=%d " +
                    "direct_p50_ms=%.3f direct_p95_ms=%.3f " +
                    "stage_p50_ms=%.3f stage_p95_ms=%.3f " +
                    "commit_p50_ms=%.3f commit_p95_ms=%.3f " +
                    "clear_p50_ms=%.3f clear_p95_ms=%.3f " +
                    "total_p50_ms=%.3f total_p95_ms=%.3f",
                trackCount, samples, warmups, a.toByteArray(Charsets.UTF_8).size,
                percentile(direct, 50), percentile(direct, 95),
                percentile(stage, 50), percentile(stage, 95),
                percentile(commit, 50), percentile(commit, 95),
                percentile(clear, 50), percentile(clear, 95),
                percentile(full, 50), percentile(full, 95)
            )
            Log.i(tag, metric)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("flp_breakdown", metric)
            })
        } finally {
            assertTrue("QA preferences were not deleted", context.deleteSharedPreferences(prefName))
            assertTrue("QA files were not deleted", directory.deleteRecursively())
        }
    }

    private fun percentile(values: LongArray, level: Int): Double {
        val sorted = values.sorted()
        val rank = ceil(level / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1] / 1_000_000.0
    }

    private fun syntheticLibrary(count: Int, variant: String): String {
        val result = StringBuilder(count * 245)
        result.append("{\"media\":[")
        repeat(count) { index ->
            if (index > 0) result.append(',')
            result.append("{\"id\":\"qa-media-").append(index)
                .append("\",\"uri\":\"content://qa.invalid/media/").append(index)
                .append("\",\"title\":\"Synthetic-").append(variant).append("-Track-")
                .append(index)
                .append("\",\"video\":false,\"lyrics\":\"[00:02.00]Synthetic QA line\\n[00:04.00]Another line\"")
                .append(",\"sourceKey\":\"synthetic-").append(index)
                .append("\",\"sizeBytes\":4194304,\"durationMs\":240000}")
        }
        result.append("],\"playlists\":[")
        repeat(3) { playlistIndex ->
            if (playlistIndex > 0) result.append(',')
            result.append("{\"id\":\"qa-list-").append(playlistIndex)
                .append("\",\"name\":\"QA list\",\"mediaIds\":[")
            val selected = (playlistIndex * 100 until (playlistIndex + 1) * 100)
                .filter { it < count }
            selected.forEachIndexed { index, track ->
                if (index > 0) result.append(',')
                result.append("\"qa-media-").append(track).append('"')
            }
            result.append("]}")
        }
        result.append("],\"retiredMedia\":[]}")
        return result.toString()
    }
}
