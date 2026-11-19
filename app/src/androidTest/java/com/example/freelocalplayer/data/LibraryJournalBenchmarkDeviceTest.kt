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
 * Stage 0-C.5 — physical-device I/O observations, NEVER a production library test.
 * All files and preferences have randomly named __flp_0c5_ prefixes.
 * No production LibraryStore instance, Room connection, main app APK change,
 * playback engine, force-stop, or crash injection is involved.
 *
 * Timed segment: SharedPreferences.commit() versus AtomicFile journal stage +
 * SharedPreferences.commit() + journal cleanup. JSON construction and Room
 * mirroring are deliberately OUTSIDE the timed segment. Synthetic payloads
 * are not measured user data; no hardcoded success/performance threshold.
 */
@RunWith(AndroidJUnit4::class)
class LibraryJournalBenchmarkDeviceTest {
    private val tag = "FLP_JOURNAL_BENCH"
    private val samples = 5

    @Test fun compare630SyntheticTracks() = benchmark(630)

    @Test fun compare5000SyntheticTracks() = benchmark(5_000)

    @Test fun compare20000SyntheticTracks() = benchmark(20_000)

    private fun benchmark(count: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = UUID.randomUUID().toString().replace("-", "")
        val name = "__flp_0c5_bench_$token"
        val directory = File(context.filesDir, "__flp_0c5_journal_$token")
        assertTrue("Cannot create isolated QA directory", directory.mkdirs())
        assertTrue("QA directory escaped filesDir",
            directory.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val journal = LibraryPendingWriteJournal(directory)
        try {
            val a = buildSnapshot(count, "A")
            val b = buildSnapshot(count, "B")
            // Sanity check with the real strict decoder, outside timed writes.
            assertEquals(count, CanonicalLibraryJsonDecoder.decode(a, keyExists = true).media.size)
            assertEquals(count, CanonicalLibraryJsonDecoder.decode(b, keyExists = true).media.size)
            assertTrue(prefs.edit().putString("library", a).commit())

            var current = a
            val direct = LongArray(samples)
            repeat(samples) { index ->
                val target = if (current === a) b else a
                val started = SystemClock.elapsedRealtimeNanos()
                check(prefs.edit().putString("library", target).commit()) {
                    "ISOLATED_DIRECT_COMMIT_FAILED"
                }
                direct[index] = SystemClock.elapsedRealtimeNanos() - started
                assertEquals("Direct commit lost its snapshot", target, prefs.getString("library", null))
                current = target
            }

            val journaled = LongArray(samples)
            repeat(samples) { index ->
                val target = if (current === a) b else a
                val started = SystemClock.elapsedRealtimeNanos()
                journal.stage(current, target)
                check(prefs.edit().putString("library", target).commit()) {
                    "ISOLATED_JOURNALED_COMMIT_FAILED"
                }
                journal.clear()
                journaled[index] = SystemClock.elapsedRealtimeNanos() - started
                assertEquals("Journaled commit lost its snapshot", target, prefs.getString("library", null))
                assertFalse("Journal leaked after commit",
                    File(directory, LibraryPendingWriteJournal.FILE_NAME).exists())
                current = target
            }

            assertEquals(LibraryPendingWriteJournal.Recovery.NONE,
                journal.recover(current, validate = { CanonicalLibraryJsonDecoder.decode(it, true); Unit },
                    commit = { false }))
            assertTrue(direct.all { it > 0L } && journaled.all { it > 0L })
            val baseline50 = percentileMillis(direct, 50)
            val journal50 = percentileMillis(journaled, 50)
            val metric = String.format(Locale.US,
                "BENCHMARK_RESULT tracks=%d samples=%d payload_bytes=%d " +
                    "direct_p50_ms=%.3f direct_p95_ms=%.3f " +
                    "journal_p50_ms=%.3f journal_p95_ms=%.3f " +
                    "journal_over_direct_p50_ratio=%.3f",
                count, samples, a.toByteArray(Charsets.UTF_8).size,
                baseline50, percentileMillis(direct, 95),
                journal50, percentileMillis(journaled, 95),
                if (baseline50 > 0.0) journal50 / baseline50 else 0.0)
            Log.i(tag, metric)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("flp_benchmark", metric)
            })
        } finally {
            assertTrue("Could not delete isolated QA preferences", context.deleteSharedPreferences(name))
            assertTrue("Could not delete isolated QA directory", directory.deleteRecursively())
        }
    }

    private fun percentileMillis(valuesNs: LongArray, percentile: Int): Double {
        val sorted = valuesNs.sorted()
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1] / 1_000_000.0
    }

    private fun buildSnapshot(count: Int, variant: String): String {
        val out = StringBuilder(count * 245)
        out.append("{\"media\":[")
        for (i in 0 until count) {
            if (i > 0) out.append(',')
            out.append("{\"id\":\"qa-media-").append(i)
                .append("\",\"uri\":\"content://qa.invalid/media/").append(i)
                .append("\",\"title\":\"Synthetic-").append(variant)
                .append("-Track-").append(i)
                .append("\",\"video\":false,\"lyrics\":\"[00:02.00]Synthetic QA line\\n[00:04.00]Another line\"")
                .append(",\"sourceKey\":\"synthetic-").append(i)
                .append("\",\"sizeBytes\":4194304,\"durationMs\":240000}")
        }
        out.append("],\"playlists\":[")
        repeat(3) { playlistIndex ->
            if (playlistIndex > 0) out.append(',')
            out.append("{\"id\":\"qa-list-").append(playlistIndex)
                .append("\",\"name\":\"QA list\",\"mediaIds\":[")
            val selected = (playlistIndex * 100 until (playlistIndex + 1) * 100).filter { it < count }
            selected.forEachIndexed { index, track ->
                if (index > 0) out.append(',')
                out.append("\"qa-media-").append(track).append('"')
            }
            out.append("]}")
        }
        out.append("],\"retiredMedia\":[]}")
        return out.toString()
    }
}
