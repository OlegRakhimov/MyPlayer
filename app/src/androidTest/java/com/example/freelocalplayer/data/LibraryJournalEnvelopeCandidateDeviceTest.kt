package com.example.freelocalplayer.data

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.Random
import java.util.UUID
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 0-C.5.5: benchmark two *uncompressed* v1 journal envelope encoders on
 * isolated synthetic media, independently from the working library and APK.
 *
 * No production source is modified. Neither LibraryStore nor production
 * preferences are opened. No process-kill/fault injection on user data.
 */
@RunWith(AndroidJUnit4::class)
class LibraryJournalEnvelopeCandidateDeviceTest {
    private val tag = "FLP_JOURNAL_ENVELOPE"
    private val iterations = 16
    private val warmups = 3

    @Test fun repetitive630Tracks() = benchmark(630, varied = false)
    @Test fun repetitive5000Tracks() = benchmark(5_000, varied = false)
    @Test fun repetitive20000Tracks() = benchmark(20_000, varied = false)
    @Test fun varied630Tracks() = benchmark(630, varied = true)
    @Test fun varied5000Tracks() = benchmark(5_000, varied = true)
    @Test fun varied20000Tracks() = benchmark(20_000, varied = true)

    @Test fun exactRoundtripForEscapesControlCharactersAndUnicode() {
        val previous = "base🌍\\\"\n\t\r\b\u000C\u0000\u001F\u2028\u2029é中😀"
        val desired = "\\quoted\\\" slash/ newline\n tabs\t CR\r backspace\b formfeed\u000C " +
            "null\u0000 unit\u001F paragraph\u2028 end\u2029 accent é 中文 emoji 😁"
        val a = PendingLibraryRecoveryPolicy.fingerprint(previous)
        val b = PendingLibraryRecoveryPolicy.fingerprint(desired)
        val reference = referenceEnvelope(a, b, desired)
        val candidate = streamedEnvelope(a, b, desired)
        assertEnvelope(reference, a, b, desired)
        assertEnvelope(candidate, a, b, desired)
        assertEquals(
            JSONObject(reference).getString("desiredJson"),
            JSONObject(candidate).getString("desiredJson")
        )
        assertArrayEquals(
            desired.toByteArray(Charsets.UTF_8),
            JSONObject(candidate).getString("desiredJson").toByteArray(Charsets.UTF_8)
        )
    }

    @Test fun existingV1RecoveryCanReplayCandidateEnvelopeExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "__flp_0c55_compat_${UUID.randomUUID().toString().replace("-", "")}" 
        val dir = File(context.filesDir, "${name}_dir")
        assertTrue(dir.mkdirs())
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val before = """{"media":[],"playlists":[],"retiredMedia":[]}"""
        val after = """{"media":[],"playlists":[{"id":"qa","name":"V1 restore","mediaIds":[]}],"retiredMedia":[]}"""
        try {
            assertTrue(prefs.edit().putString("library", before).commit())
            writeV1File(dir, before, after)
            val journal = LibraryPendingWriteJournal(dir)
            val restored = journal.recover(before,
                validate = { CanonicalLibraryJsonDecoder.decode(it, keyExists = true); Unit },
                commit = { raw -> prefs.edit().putString("library", raw).commit() }
            )
            assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, restored)
            assertEquals(after, prefs.getString("library", null))
            assertFalse(File(dir, LibraryPendingWriteJournal.FILE_NAME).exists())
            assertEquals(LibraryPendingWriteJournal.Recovery.NONE,
                LibraryPendingWriteJournal(dir).recover(after, validate = { fail("unexpected") },
                    commit = { fail("unexpected"); false }))
        } finally {
            assertTrue(context.deleteSharedPreferences(name))
            assertTrue(dir.deleteRecursively())
        }
    }

    @Test fun candidateV1EnvelopePreservesNewerCanonicalOnConflict() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "__flp_0c55_conflict_${UUID.randomUUID().toString().replace("-", "")}" 
        val dir = File(context.filesDir, "${name}_dir")
        assertTrue(dir.mkdirs())
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val before = """{"media":[],"playlists":[],"retiredMedia":[]}"""
        val after = """{"media":[],"playlists":[{"id":"pending","name":"Pending","mediaIds":[]}],"retiredMedia":[]}"""
        val newer = """{"media":[],"playlists":[{"id":"newer","name":"Newer","mediaIds":[]}],"retiredMedia":[]}"""
        try {
            assertTrue(prefs.edit().putString("library", before).commit())
            writeV1File(dir, before, after)
            assertTrue(prefs.edit().putString("library", newer).commit())
            var committed = false
            try {
                LibraryPendingWriteJournal(dir).recover(newer,
                    validate = { CanonicalLibraryJsonDecoder.decode(it, keyExists = true); Unit },
                    commit = { committed = true; false })
                fail("Conflicting canonical data must not be replaced")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("JOURNAL_CONFLICT"))
            }
            assertFalse(committed)
            assertEquals(newer, prefs.getString("library", null))
            assertTrue(File(dir, LibraryPendingWriteJournal.FILE_NAME).exists())
        } finally {
            assertTrue(context.deleteSharedPreferences(name))
            assertTrue(dir.deleteRecursively())
        }
    }

    private fun benchmark(trackCount: Int, varied: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir,
            "__flp_0c55_envelope_${UUID.randomUUID().toString().replace("-", "")}")
        assertTrue(dir.mkdirs())
        assertTrue(dir.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
        val baseline = LibraryPendingWriteJournal(File(dir, "baseline"))
        val reference = AtomicFile(File(dir, "reference-qa-v1.json"))
        val streamed = AtomicFile(File(dir, "streamed-qa-v1.json"))
        try {
            val oldJson = syntheticLibrary(trackCount, varied, "A")
            val newJson = syntheticLibrary(trackCount, varied, "B")
            assertEquals(trackCount, CanonicalLibraryJsonDecoder.decode(oldJson, true).media.size)
            assertEquals(trackCount, CanonicalLibraryJsonDecoder.decode(newJson, true).media.size)
            assertFalse(oldJson == newJson)

            val legacyStage = LongArray(iterations)
            val referenceHash = LongArray(iterations)
            val referenceEncode = LongArray(iterations)
            val referenceAtomic = LongArray(iterations)
            val referenceTotal = LongArray(iterations)
            val streamedHash = LongArray(iterations)
            val streamedEncode = LongArray(iterations)
            val streamedAtomic = LongArray(iterations)
            val streamedTotal = LongArray(iterations)
            var referenceSize = 0
            var streamSize = 0

            fun original(): Long {
                val start = now()
                baseline.stage(oldJson, newJson)
                val duration = now() - start
                baseline.clear()
                return duration
            }

            fun candidate(stream: Boolean, index: Int?) {
                val start = now()
                val from = PendingLibraryRecoveryPolicy.fingerprint(oldJson)
                val to = PendingLibraryRecoveryPolicy.fingerprint(newJson)
                val hashed = now()
                val envelope = if (stream) streamedEnvelope(from, to, newJson)
                    else referenceEnvelope(from, to, newJson)
                val bytes = envelope.toByteArray(Charsets.UTF_8)
                val encoded = now()
                val file = if (stream) streamed else reference
                writeAtomic(file, bytes)
                val written = now()

                // Integrity assertions are deliberately outside measured segments.
                val onDisk = file.openRead().use { it.readBytes() }
                assertArrayEquals(bytes, onDisk)
                assertEnvelope(String(onDisk, Charsets.UTF_8), from, to, newJson)
                file.delete()
                assertFalse(File(dir, if (stream) "streamed-qa-v1.json"
                    else "reference-qa-v1.json").exists())
                if (stream) {
                    streamSize = bytes.size
                    if (index != null) {
                        streamedHash[index] = hashed - start
                        streamedEncode[index] = encoded - hashed
                        streamedAtomic[index] = written - encoded
                        streamedTotal[index] = written - start
                    }
                } else {
                    referenceSize = bytes.size
                    if (index != null) {
                        referenceHash[index] = hashed - start
                        referenceEncode[index] = encoded - hashed
                        referenceAtomic[index] = written - encoded
                        referenceTotal[index] = written - start
                    }
                }
            }

            repeat(warmups) {
                original()
                candidate(stream = false, index = null)
                candidate(stream = true, index = null)
            }
            repeat(iterations) { index ->
                when (index % 3) {
                    0 -> {
                        legacyStage[index] = original()
                        candidate(false, index)
                        candidate(true, index)
                    }
                    1 -> {
                        candidate(true, index)
                        legacyStage[index] = original()
                        candidate(false, index)
                    }
                    else -> {
                        candidate(false, index)
                        candidate(true, index)
                        legacyStage[index] = original()
                    }
                }
            }
            val groups = listOf(legacyStage, referenceHash, referenceEncode, referenceAtomic,
                referenceTotal, streamedHash, streamedEncode, streamedAtomic, streamedTotal)
            assertTrue(groups.all { group -> group.all { it > 0L } })
            assertTrue(referenceSize > 0 && streamSize > 0)
            assertEquals(LibraryPendingWriteJournal.Recovery.NONE,
                baseline.recover(oldJson, validate = { fail("unexpected") },
                    commit = { fail("unexpected"); false }))

            val result = String.format(Locale.US,
                "ENVELOPE_RESULT tracks=%d profile=%s samples=%d warmups=%d " +
                    "payload_bytes=%d legacy_envelope_bytes=%d stream_envelope_bytes=%d " +
                    "original_stage_p50_ms=%.3f original_stage_p95_ms=%.3f " +
                    "legacy_hash_p50_ms=%.3f legacy_encode_p50_ms=%.3f " +
                    "legacy_atomic_p50_ms=%.3f legacy_atomic_p95_ms=%.3f " +
                    "legacy_total_p50_ms=%.3f legacy_total_p95_ms=%.3f " +
                    "stream_hash_p50_ms=%.3f stream_encode_p50_ms=%.3f " +
                    "stream_atomic_p50_ms=%.3f stream_atomic_p95_ms=%.3f " +
                    "stream_total_p50_ms=%.3f stream_total_p95_ms=%.3f",
                trackCount, if (varied) "varied" else "repetitive", iterations, warmups,
                newJson.toByteArray(Charsets.UTF_8).size, referenceSize, streamSize,
                percentile(legacyStage, 50), percentile(legacyStage, 95),
                percentile(referenceHash, 50), percentile(referenceEncode, 50),
                percentile(referenceAtomic, 50), percentile(referenceAtomic, 95),
                percentile(referenceTotal, 50), percentile(referenceTotal, 95),
                percentile(streamedHash, 50), percentile(streamedEncode, 50),
                percentile(streamedAtomic, 50), percentile(streamedAtomic, 95),
                percentile(streamedTotal, 50), percentile(streamedTotal, 95))
            Log.i(tag, result)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("flp_envelope", result)
            })
        } finally {
            reference.delete()
            streamed.delete()
            assertTrue(dir.deleteRecursively())
        }
    }

    private fun writeV1File(directory: File, before: String, desired: String) {
        val oldHash = PendingLibraryRecoveryPolicy.fingerprint(before)
        val newHash = PendingLibraryRecoveryPolicy.fingerprint(desired)
        val contents = streamedEnvelope(oldHash, newHash, desired)
        assertEnvelope(contents, oldHash, newHash, desired)
        writeAtomic(AtomicFile(File(directory, LibraryPendingWriteJournal.FILE_NAME)),
            contents.toByteArray(Charsets.UTF_8))
    }

    private fun assertEnvelope(envelope: String, before: String, after: String, desired: String) {
        val decoded = JSONObject(envelope)
        assertEquals(1, decoded.getInt("version"))
        assertEquals(before, decoded.getString("previousSha256"))
        assertEquals(after, decoded.getString("desiredSha256"))
        assertEquals(desired, decoded.getString("desiredJson"))
        assertEquals(after, PendingLibraryRecoveryPolicy.fingerprint(decoded.getString("desiredJson")))
        assertEquals(setOf("version", "previousSha256", "desiredSha256", "desiredJson"),
            decoded.keys().asSequence().toSet())
    }

    private fun referenceEnvelope(oldHash: String, newHash: String, value: String): String =
        JSONObject().put("version", 1)
            .put("previousSha256", oldHash)
            .put("desiredSha256", newHash)
            .put("desiredJson", value).toString()

    private fun streamedEnvelope(oldHash: String, newHash: String, value: String): String =
        buildString(value.length + 224) {
            append("{\"version\":1,\"previousSha256\":\"")
            append(oldHash)
            append("\",\"desiredSha256\":\"")
            append(newHash)
            append("\",\"desiredJson\":\"")
            appendEscapedJson(value)
            append("\"}")
        }

    // UTF-16 code units are copied as-is except JSON syntax, U+2028/2029, and controls.
    // Never split or normalize surrogate pairs; UTF-8 roundtrip is checked separately.
    private fun StringBuilder.appendEscapedJson(value: String) {
        val hex = "0123456789abcdef"
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ' || character == '\u2028' || character == '\u2029') {
                    append("\\u")
                    for (shift in 12 downTo 0 step 4) {
                        append(hex[(character.code ushr shift) and 15])
                    }
                } else append(character)
            }
        }
    }

    private fun syntheticLibrary(count: Int, varied: Boolean, variant: String): String {
        val random = Random(531L + if (variant == "A") 0L else 1L)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(count * 250) {
            append("{\"media\":[")
            repeat(count) { index ->
                if (index > 0) append(',')
                val lyric = if (varied) buildString(110) {
                    repeat(110) { append(alphabet[random.nextInt(alphabet.length)]) }
                } else "one repeated chorus every day one repeated chorus every day"
                append("{\"id\":\"qa-").append(index)
                    .append("\",\"uri\":\"content://qa.invalid/media/").append(index)
                    .append("\",\"title\":\"Stage QA ").append(variant).append(' ').append(index)
                    .append("\",\"video\":false,\"lyrics\":\"").append(lyric)
                    .append("\",\"sourceKey\":\"qa-source-").append(index)
                    .append("\",\"sizeBytes\":4194304,\"durationMs\":240000}")
            }
            append("],\"playlists\":[],\"retiredMedia\":[]}")
        }
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IOException("QA_ATOMIC_WRITE_FAILED", error)
        }
    }

    private fun percentile(values: LongArray, rank: Int): Double {
        val sorted = values.sorted()
        val index = ceil(rank / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[index - 1] / 1_000_000.0
    }

    private fun now(): Long = SystemClock.elapsedRealtimeNanos()
}
