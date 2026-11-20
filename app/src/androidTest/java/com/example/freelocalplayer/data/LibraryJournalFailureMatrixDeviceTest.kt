package com.example.freelocalplayer.data

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 0-C.5.4: adversarial recovery matrix against the real v1 journal implementation.
 *
 * ALL mutations happen in a random QA-only subdirectory under filesDir and in
 * a uniquely named SharedPreferences file. The production media library and
 * LibraryStore are not opened, modified, or copied by these tests.
 */
@RunWith(AndroidJUnit4::class)
class LibraryJournalFailureMatrixDeviceTest {
    private lateinit var app: Context
    private lateinit var directory: File
    private lateinit var preferencesName: String
    private lateinit var journal: LibraryPendingWriteJournal

    private val initial = """{"media":[],"playlists":[],"retiredMedia":[]}"""
    private val target = """{"media":[],"playlists":[{"id":"qa-list","name":"QA","mediaIds":[]}],"retiredMedia":[]}"""
    private val competing = """{"media":[],"playlists":[{"id":"other","name":"Other","mediaIds":[]}],"retiredMedia":[]}"""

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val token = UUID.randomUUID().toString().replace("-", "")
        directory = File(app.filesDir, "__flp_0c54_failure_matrix_$token")
        assertTrue("QA directory creation failed", directory.mkdirs())
        assertTrue(
            "QA directory escaped filesDir",
            directory.canonicalPath.startsWith(app.filesDir.canonicalPath + File.separator)
        )
        preferencesName = "__flp_0c54_preferences_$token"
        journal = LibraryPendingWriteJournal(directory)
    }

    @After
    fun tearDown() {
        app.deleteSharedPreferences(preferencesName)
        assertTrue("QA directory cleanup failed", directory.deleteRecursively())
    }

    private fun prefs() = app.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private fun journalPath() = File(directory, LibraryPendingWriteJournal.FILE_NAME)

    private fun initialize(raw: String = initial) {
        assertTrue("QA initial SharedPreferences.commit failed",
            prefs().edit().putString("library", raw).commit())
    }

    private fun checkedValidate(raw: String) {
        CanonicalLibraryJsonDecoder.decode(raw, keyExists = true)
    }

    private fun recover(): LibraryPendingWriteJournal.Recovery = journal.recover(
        prefs().getString("library", null),
        ::checkedValidate
    ) { raw -> prefs().edit().putString("library", raw).commit() }

    private fun expectIOException(fragment: String, operation: () -> Unit) {
        try {
            operation()
            fail("Expected fail-closed IOException: $fragment")
        } catch (error: IOException) {
            assertTrue(
                "Unexpected failure code: ${error.message}",
                error.message?.contains(fragment) == true
            )
        }
    }

    private fun writeTestJournal(bytes: ByteArray) {
        val atomic = AtomicFile(journalPath())
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) atomic.failWrite(stream)
            throw error
        }
    }

    private fun editTestJournal(change: (JSONObject) -> Unit) {
        val record = JSONObject(journalPath().readText(Charsets.UTF_8))
        change(record)
        writeTestJournal(record.toString().toByteArray(Charsets.UTF_8))
    }

    private fun ensureNotChangedAndJournalPreserved(expected: String = initial) {
        assertEquals("QA canonical library unexpectedly changed", expected,
            prefs().getString("library", null))
        assertTrue("Corrupted/conflicting QA journal must be retained", journalPath().isFile)
    }

    @Test
    fun alteredTargetHashCannotOverwriteCanonical() {
        initialize()
        journal.stage(initial, target)
        editTestJournal { it.put("desiredSha256", "0".repeat(64)) }
        expectIOException("JOURNAL_INVALID_KEEP_ORIGINAL") { recover() }
        ensureNotChangedAndJournalPreserved()
    }

    @Test
    fun malformedPreviousDigestIsRejectedWithoutDiscardingJournal() {
        initialize()
        journal.stage(initial, target)
        editTestJournal { it.put("previousSha256", "not-a-sha256") }
        expectIOException("JOURNAL_INVALID_KEEP_ORIGINAL") { recover() }
        ensureNotChangedAndJournalPreserved()
    }

    @Test
    fun unsupportedJournalVersionIsRejectedWithoutAnyCommit() {
        initialize()
        journal.stage(initial, target)
        editTestJournal { it.put("version", 999) }
        expectIOException("JOURNAL_INVALID_KEEP_ORIGINAL") { recover() }
        ensureNotChangedAndJournalPreserved()
    }

    @Test
    fun truncatedJournalIsRejectedAndRetained() {
        initialize()
        journal.stage(initial, target)
        writeTestJournal("{\"version\":1,\"desiredJson\":".toByteArray(Charsets.UTF_8))
        expectIOException("JOURNAL_INVALID_KEEP_ORIGINAL") { recover() }
        ensureNotChangedAndJournalPreserved()
    }

    @Test
    fun validatorFailureBlocksCommitAndRetainsValidJournal() {
        initialize()
        journal.stage(initial, target)
        var commitAttempts = 0
        try {
            journal.recover(initial, validate = { throw IllegalArgumentException("QA_VALIDATOR_REJECTED") }) {
                commitAttempts++
                true
            }
            fail("The validator must prevent commit")
        } catch (error: IllegalArgumentException) {
            assertEquals("QA_VALIDATOR_REJECTED", error.message)
        }
        assertEquals(0, commitAttempts)
        ensureNotChangedAndJournalPreserved()
    }

    @Test
    fun rejectedRecoveryCommitCanRecoverAfterNewJournalInstance() {
        initialize()
        journal.stage(initial, target)
        expectIOException("JOURNAL_RECOVERY_COMMIT_FAILED") {
            journal.recover(initial, ::checkedValidate) { false }
        }
        ensureNotChangedAndJournalPreserved()
        journal = LibraryPendingWriteJournal(directory)
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, recover())
        assertEquals(target, prefs().getString("library", null))
        assertFalse(journalPath().exists())
    }

    @Test
    fun differentButValidCanonicalSnapshotCausesConflict() {
        initialize()
        journal.stage(initial, target)
        assertTrue(prefs().edit().putString("library", competing).commit())
        expectIOException("JOURNAL_CONFLICT_KEEP_BOTH_NO_AUTOMATIC_OVERWRITE") { recover() }
        ensureNotChangedAndJournalPreserved(competing)
    }

    @Test
    fun equivalentJsonWithDifferentBytesIsNotSilentlyReplaced() {
        initialize()
        journal.stage(initial, target)
        val reserialized = """{ "media": [], "playlists": [], "retiredMedia": [] }"""
        checkedValidate(reserialized)
        assertTrue(prefs().edit().putString("library", reserialized).commit())
        expectIOException("JOURNAL_CONFLICT_KEEP_BOTH_NO_AUTOMATIC_OVERWRITE") { recover() }
        ensureNotChangedAndJournalPreserved(reserialized)
    }

    @Test
    fun failedAtomicReplacementKeepsPreviousValidSnapshot() {
        initialize()
        journal.stage(initial, target)
        val atomic = AtomicFile(journalPath())
        val pendingStream = atomic.startWrite()
        try {
            pendingStream.write("{PARTIAL_UNCOMMITTED".toByteArray(Charsets.UTF_8))
        } finally {
            // This simulates a failed atomic write, not killing the real app.
            atomic.failWrite(pendingStream)
        }
        journal = LibraryPendingWriteJournal(directory)
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, recover())
        assertEquals(target, prefs().getString("library", null))
        assertFalse(journalPath().exists())
    }

    @Test
    fun alreadyCommittedColdReopenDoesNotCommitAgain() {
        initialize()
        journal.stage(initial, target)
        assertTrue(prefs().edit().putString("library", target).commit())
        journal = LibraryPendingWriteJournal(directory)
        var unwantedCommits = 0
        val recovery = journal.recover(target, ::checkedValidate) {
            unwantedCommits++
            false
        }
        assertEquals(LibraryPendingWriteJournal.Recovery.ALREADY_COMMITTED, recovery)
        assertEquals(0, unwantedCommits)
        assertFalse(journalPath().exists())
        journal = LibraryPendingWriteJournal(directory)
        assertEquals(LibraryPendingWriteJournal.Recovery.NONE, recover())
        assertEquals(target, prefs().getString("library", null))
    }

    @Test
    fun absentCanonicalCanReplayButEmptyCanonicalMustConflict() {
        // Distinguish a missing preference from an existing empty string.
        journal.stage(null, target)
        assertTrue(prefs().edit().putString("library", "").commit())
        expectIOException("JOURNAL_CONFLICT_KEEP_BOTH_NO_AUTOMATIC_OVERWRITE") { recover() }
        ensureNotChangedAndJournalPreserved("")
        assertTrue(prefs().edit().remove("library").commit())
        journal = LibraryPendingWriteJournal(directory)
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, recover())
        assertEquals(target, prefs().getString("library", null))
        assertFalse(journalPath().exists())
    }
}
