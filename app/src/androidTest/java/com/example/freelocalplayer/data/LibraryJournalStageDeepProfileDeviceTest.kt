package com.example.freelocalplayer.data

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 0-C.5.3: isolated measurements of the stages inside the recovery-journal write.
 *
 * No main APK update, no production prefs, no LibraryStore, no Room, no import,
 * no actual media, no app-killing or intentional production corruption.
 * Compressed candidate stays inside unique __flp_0c53_* QA directories only.
 * The shipping LibraryPendingWriteJournal code is NEVER modified by this test.
 */
@RunWith(AndroidJUnit4::class)
class LibraryJournalStageDeepProfileDeviceTest {
    private val samples = 16
    private val warmups = 3
    private val maxDecodedBytes = 64 * 1024 * 1024
    private val label = "FLP_JOURNAL_DEEP"

    @Test fun repetitive630Tracks() = profile(630, variable = false)
    @Test fun repetitive5000Tracks() = profile(5_000, variable = false)
    @Test fun repetitive20000Tracks() = profile(20_000, variable = false)
    @Test fun variable630Tracks() = profile(630, variable = true)
    @Test fun variable5000Tracks() = profile(5_000, variable = true)
    @Test fun variable20000Tracks() = profile(20_000, variable = true)

    @Test fun compressedCandidateRejectsDamagedOrOversizedPayload() {
        val sample = "test-stage-0c53-✓".toByteArray(Charsets.UTF_8)
        val compressed = gzip(sample)
        assertArrayEquals(sample, gunzipBounded(compressed))
        try {
            gunzipBounded(byteArrayOf(1, 2, 3, 4))
            org.junit.Assert.fail("Truncated/invalid GZIP must be rejected")
        } catch (_: IOException) { /* expected */ }
        try {
            gunzipBounded(compressed, maximum = sample.size - 1)
            org.junit.Assert.fail("Oversized inflated payload must be rejected")
        } catch (_: IOException) { /* expected */ }
        val damagedTrailer = compressed.copyOf()
        damagedTrailer[damagedTrailer.lastIndex] = (damagedTrailer.last().toInt() xor 0x01).toByte()
        try {
            gunzipBounded(damagedTrailer)
            org.junit.Assert.fail("Damaged GZIP trailer must be rejected")
        } catch (_: IOException) { /* expected */ }
    }

    private fun profile(count: Int, variable: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val token = UUID.randomUUID().toString().replace("-", "")
        val directory = File(app.filesDir, "__flp_0c53_profile_$token")
        assertTrue("QA directory not created", directory.mkdirs())
        assertTrue("QA directory outside filesDir",
            directory.canonicalPath.startsWith(app.filesDir.canonicalPath + File.separator))
        val baseline = LibraryPendingWriteJournal(directory)
        val experimentalFile = File(directory, "__flp_0c53_candidate.bin")
        val experimental = AtomicFile(experimentalFile)
        try {
            val previous = snapshot(count, "A", variable)
            val desired = snapshot(count, "B", variable)
            assertEquals(count, CanonicalLibraryJsonDecoder.decode(previous, true).media.size)
            assertEquals(count, CanonicalLibraryJsonDecoder.decode(desired, true).media.size)
            assertFalse(previous == desired)
            val rawBytes = desired.toByteArray(Charsets.UTF_8)
            val rawSamples = LongArray(samples)
            val hashSamples = LongArray(samples)
            val envelopeSamples = LongArray(samples)
            val gzipSamples = LongArray(samples)
            val atomicSamples = LongArray(samples)
            val inflateSamples = LongArray(samples)
            val candidateSamples = LongArray(samples)
            var envelopeLength = 0
            var gzipLength = 0

            fun actualJournalWrite(): Long {
                val started = now()
                baseline.stage(previous, desired)
                val duration = now() - started
                baseline.clear() // clearing is intentionally excluded
                return duration
            }

            fun candidateWrite(index: Int?) {
                val totalStart = now()
                val hashStart = now()
                val beforeHash = PendingLibraryRecoveryPolicy.fingerprint(previous)
                val afterHash = PendingLibraryRecoveryPolicy.fingerprint(desired)
                val hashEnd = now()

                // Exact v1 production envelope, before hypothetical GZIP encoding.
                val envelope = JSONObject()
                    .put("version", 1)
                    .put("previousSha256", beforeHash)
                    .put("desiredSha256", afterHash)
                    .put("desiredJson", desired)
                    .toString().toByteArray(Charsets.UTF_8)
                val envelopeEnd = now()
                val compressed = gzip(envelope)
                val gzipEnd = now()
                writeAtomic(experimental, compressed)
                val atomicEnd = now()

                val recovered = gunzipBounded(experimental.openRead().use { it.readBytes() })
                // Integrity check is outside the timing sections except candidate total.
                assertArrayEquals(envelope, recovered)
                val restored = JSONObject(String(recovered, Charsets.UTF_8))
                assertEquals(beforeHash, restored.getString("previousSha256"))
                assertEquals(afterHash, restored.getString("desiredSha256"))
                assertEquals(afterHash,
                    PendingLibraryRecoveryPolicy.fingerprint(restored.getString("desiredJson")))
                val inflateEnd = now()
                experimental.delete()
                assertFalse(experimentalFile.exists())
                envelopeLength = envelope.size
                gzipLength = compressed.size

                if (index != null) {
                    hashSamples[index] = hashEnd - hashStart
                    envelopeSamples[index] = envelopeEnd - hashEnd
                    gzipSamples[index] = gzipEnd - envelopeEnd
                    atomicSamples[index] = atomicEnd - gzipEnd
                    inflateSamples[index] = inflateEnd - atomicEnd
                    candidateSamples[index] = atomicEnd - totalStart
                }
            }

            repeat(warmups) {
                actualJournalWrite()
                candidateWrite(null)
            }
            repeat(samples) { index ->
                if (index % 2 == 0) {
                    rawSamples[index] = actualJournalWrite()
                    candidateWrite(index)
                } else {
                    candidateWrite(index)
                    rawSamples[index] = actualJournalWrite()
                }
            }
            assertTrue(rawSamples.all { it > 0L })
            assertTrue(hashSamples.all { it > 0L })
            assertTrue(envelopeSamples.all { it > 0L })
            assertTrue(gzipSamples.all { it > 0L })
            assertTrue(atomicSamples.all { it > 0L })
            assertTrue(inflateSamples.all { it > 0L })
            assertTrue(candidateSamples.all { it > 0L })
            assertEquals(LibraryPendingWriteJournal.Recovery.NONE,
                baseline.recover(previous, validate = { error("Unexpected journal") },
                    commit = { false }))

            val metrics = String.format(Locale.US,
                "DEEP_STAGE_RESULT tracks=%d profile=%s samples=%d warmups=%d " +
                    "payload_bytes=%d envelope_bytes=%d gzip_bytes=%d gzip_ratio=%.4f " +
                    "raw_stage_p50_ms=%.3f raw_stage_p95_ms=%.3f " +
                    "hash_p50_ms=%.3f hash_p95_ms=%.3f " +
                    "envelope_p50_ms=%.3f envelope_p95_ms=%.3f " +
                    "gzip_p50_ms=%.3f gzip_p95_ms=%.3f " +
                    "gzip_atomic_p50_ms=%.3f gzip_atomic_p95_ms=%.3f " +
                    "gzip_restore_p50_ms=%.3f gzip_restore_p95_ms=%.3f " +
                    "candidate_write_p50_ms=%.3f candidate_write_p95_ms=%.3f",
                count, if (variable) "varied" else "repetitive", samples, warmups,
                rawBytes.size, envelopeLength, gzipLength,
                gzipLength.toDouble() / envelopeLength.toDouble(),
                percentile(rawSamples, 50), percentile(rawSamples, 95),
                percentile(hashSamples, 50), percentile(hashSamples, 95),
                percentile(envelopeSamples, 50), percentile(envelopeSamples, 95),
                percentile(gzipSamples, 50), percentile(gzipSamples, 95),
                percentile(atomicSamples, 50), percentile(atomicSamples, 95),
                percentile(inflateSamples, 50), percentile(inflateSamples, 95),
                percentile(candidateSamples, 50), percentile(candidateSamples, 95)
            )
            Log.i(label, metrics)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("flp_deep_stage", metrics)
            })
        } finally {
            experimental.delete()
            // Production journal is never opened; this directory is QA-only.
            assertTrue("QA files cleanup failed", directory.deleteRecursively())
        }
    }

    private fun snapshot(count: Int, variant: String, variable: Boolean): String {
        val random = Random(581L + if (variant == "A") 0L else 1L)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val builder = StringBuilder(count * 275)
        builder.append("{\"media\":[")
        repeat(count) { index ->
            if (index > 0) builder.append(',')
            val lyric = if (variable) buildString(110) {
                repeat(110) { append(alphabet[random.nextInt(alphabet.length)]) }
            } else "one repeated chorus every day one repeated chorus every day"
            builder.append("{\"id\":\"qa-").append(index)
                .append("\",\"uri\":\"content://qa.invalid/media/").append(index)
                .append("\",\"title\":\"Stage QA ").append(variant).append(' ').append(index)
                .append("\",\"video\":false,\"lyrics\":\"").append(lyric)
                .append("\",\"sourceKey\":\"qa-source-").append(index)
                .append("\",\"sizeBytes\":4194304,\"durationMs\":240000}")
        }
        builder.append("],\"playlists\":[],\"retiredMedia\":[]}")
        return builder.toString()
    }

    private fun gzip(content: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(content.size.coerceAtMost(16 * 1024))
        GZIPOutputStream(output).use { it.write(content) }
        return output.toByteArray()
    }

    private fun gunzipBounded(compressed: ByteArray, maximum: Int = maxDecodedBytes): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            while (true) {
                val received = input.read(buffer)
                if (received < 0) break
                if (out.size() > maximum - received) throw IOException("QA_GZIP_OUTPUT_TOO_LARGE")
                out.write(buffer, 0, received)
            }
        }
        return out.toByteArray()
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    private fun percentile(samples: LongArray, percent: Int): Double {
        val sorted = samples.sorted()
        val rank = ceil((percent / 100.0) * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1] / 1_000_000.0
    }

    private fun now(): Long = SystemClock.elapsedRealtimeNanos()
}
