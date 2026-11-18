package com.example.freelocalplayer.data

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Atomic write-ahead recovery copy for the canonical JSON library.
 *
 * This is NOT a second active library. No code reads it as playback state.
 * It is a single, app-private, checksummed recovery snapshot that is deleted
 * after the canonical SharedPreferences.commit() succeeds. On a cold start the
 * snapshot is replayed only when the current canonical JSON still matches the
 * exact catalog it was based on. A conflict is always fail-closed.
 *
 * The journal is saved on FLP-LibraryWriter, never the Android main thread.
 * If Android kills the process BEFORE staging completes, recovery is not possible;
 * callers must still wait for a confirmed commit before reporting success.
 */
internal class LibraryPendingWriteJournal(directory: File) {
    companion object {
        const val FILE_NAME = "flp-pending-library-0c42.json"
        private const val VERSION = 1
        private const val MAX_BYTES = 64 * 1024 * 1024
    }

    enum class Recovery { NONE, ALREADY_COMMITTED, RESTORED }

    private data class Record(
        val previousFingerprint: String,
        val desiredFingerprint: String,
        val desiredJson: String
    )

    private val root: File = directory.canonicalFile
    private val file = File(root, FILE_NAME)
    private val atomic = AtomicFile(file)

    private fun stored(): Boolean = file.exists() || File(file.path + ".bak").exists()

    private fun read(): Record? {
        if (!stored()) return null
        val bytes = try {
            atomic.openRead().use { input ->
                val bounded = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    if (total > MAX_BYTES - length) throw IOException("JOURNAL_SIZE_LIMIT")
                    bounded.write(buffer, 0, length)
                    total += length
                }
                bounded.toByteArray()
            }
        } catch (error: Exception) {
            throw IOException("JOURNAL_READ_FAILED_KEEP_ORIGINAL", error)
        }
        try {
            val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
            require(json.getInt("version") == VERSION) { "JOURNAL_VERSION_UNSUPPORTED" }
            val before = json.getString("previousSha256")
            val after = json.getString("desiredSha256")
            val value = json.getString("desiredJson")
            require(before == "ABSENT" || before.matches(Regex("[0-9A-F]{64}"))) {
                "JOURNAL_PREVIOUS_DIGEST_INVALID"
            }
            require(after.matches(Regex("[0-9A-F]{64}"))) {
                "JOURNAL_TARGET_DIGEST_INVALID"
            }
            require(PendingLibraryRecoveryPolicy.fingerprint(value) == after) {
                "JOURNAL_CHECKSUM_MISMATCH"
            }
            return Record(before, after, value)
        } catch (error: Exception) {
            throw IOException("JOURNAL_INVALID_KEEP_ORIGINAL", error)
        }
    }

    /** Called from the serial writer after validating/encoding the desired state. */
    fun stage(previousRaw: String?, desiredJson: String) {
        require(desiredJson.isNotBlank()) { "JOURNAL_EMPTY_SNAPSHOT" }
        val before = PendingLibraryRecoveryPolicy.fingerprint(previousRaw)
        val after = PendingLibraryRecoveryPolicy.fingerprint(desiredJson)
        val old = read()
        if (old != null) {
            val previous = PendingLibraryRecoveryPolicy.decide(
                old.previousFingerprint, old.desiredFingerprint, previousRaw
            )
            if (previous == PendingLibraryRecoveryPolicy.Decision.CONFLICT) {
                throw IOException("JOURNAL_CONFLICT_PRESERVED_NO_OVERWRITE")
            }
        }
        val bytes = JSONObject()
            .put("version", VERSION)
            .put("previousSha256", before)
            .put("desiredSha256", after)
            .put("desiredJson", desiredJson)
            .toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("JOURNAL_SIZE_LIMIT")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("JOURNAL_DIRECTORY_NOT_CREATED")
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            if (output != null) atomic.failWrite(output)
            throw IOException("JOURNAL_STAGE_FAILED_CANONICAL_NOT_MODIFIED", error)
        }
    }

    /**
     * A checked cold-start recovery. The supplied validator must reject any malformed
     * canonical JSON before commit; the supplied commit must be a synchronous write.
     * Nothing is committed if the previous catalog checksum differs.
     */
    fun recover(
        currentRaw: String?,
        validate: (String) -> Unit,
        commit: (String) -> Boolean
    ): Recovery {
        val pending = read() ?: return Recovery.NONE
        return when (PendingLibraryRecoveryPolicy.decide(
            pending.previousFingerprint, pending.desiredFingerprint, currentRaw
        )) {
            PendingLibraryRecoveryPolicy.Decision.ALREADY_COMMITTED -> {
                clear()
                Recovery.ALREADY_COMMITTED
            }
            PendingLibraryRecoveryPolicy.Decision.CONFLICT -> {
                throw IOException("JOURNAL_CONFLICT_KEEP_BOTH_NO_AUTOMATIC_OVERWRITE")
            }
            PendingLibraryRecoveryPolicy.Decision.REPLAY -> {
                validate(pending.desiredJson)
                if (!commit(pending.desiredJson)) {
                    throw IOException("JOURNAL_RECOVERY_COMMIT_FAILED_KEEP_JOURNAL")
                }
                clear()
                Recovery.RESTORED
            }
        }
    }

    /** Must be called only after the canonical commit succeeds. */
    fun clear() {
        atomic.delete()
        if (stored()) throw IOException("JOURNAL_CLEANUP_FAILED")
    }
}
