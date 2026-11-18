package com.example.freelocalplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every test uses a unique private directory and QA-only preferences, never production JSON. */
@RunWith(AndroidJUnit4::class)
class LibraryPendingJournalDeviceTest {
    private lateinit var app: Context
    private lateinit var directory: File
    private lateinit var prefName: String
    private lateinit var journal: LibraryPendingWriteJournal
    private val before = """{"media":[],"playlists":[],"retiredMedia":[]}"""
    private val desired = """{"media":[],"playlists":[{"id":"qa-playlist","name":"QA","mediaIds":[]}],"retiredMedia":[]}"""

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val token = UUID.randomUUID().toString().replace("-", "")
        directory = File(app.filesDir, "__flp_0c42_journal_$token")
        assertTrue(directory.mkdirs())
        assertTrue(directory.canonicalPath.startsWith(app.filesDir.canonicalPath + File.separator))
        prefName = "__flp_0c42_prefs_$token"
        journal = LibraryPendingWriteJournal(directory)
    }

    @After fun cleanup() {
        assertTrue(app.deleteSharedPreferences(prefName))
        assertTrue(directory.deleteRecursively())
    }

    private fun prefs() = app.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    private fun validate(raw: String) {
        CanonicalLibraryJsonDecoder.decode(raw, keyExists = true)
    }

    private fun restore(): LibraryPendingWriteJournal.Recovery = journal.recover(
        prefs().getString("library", null),
        ::validate
    ) { payload -> prefs().edit().putString("library", payload).commit() }

    @Test fun interruptedCommitRecoversIdenticalPendingSnapshotOnColdReopen() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, desired)
        // Mimic process restart: new object, original journal still on disk.
        journal = LibraryPendingWriteJournal(directory)
        assertEquals(before, prefs().getString("library", null))
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, restore())
        assertEquals(desired, prefs().getString("library", null))
        assertFalse(File(directory, LibraryPendingWriteJournal.FILE_NAME).exists())
    }

    @Test fun alreadyCommittedJournalIsRemovedWithoutAnyReplay() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, desired)
        assertTrue(prefs().edit().putString("library", desired).commit())
        var extraCommits = 0
        assertEquals(LibraryPendingWriteJournal.Recovery.ALREADY_COMMITTED,
            journal.recover(prefs().getString("library", null), ::validate) {
                extraCommits++
                false
            })
        assertEquals(0, extraCommits)
        assertEquals(desired, prefs().getString("library", null))
    }

    @Test fun conflictingCanonicalSnapshotIsNeverOverwritten() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, desired)
        val newer = """{"media":[],"playlists":[{"id":"newer","name":"New","mediaIds":[]}]}"""
        assertTrue(prefs().edit().putString("library", newer).commit())
        try {
            restore()
            fail("Conflicting journal must fail closed")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("JOURNAL_CONFLICT"))
        }
        assertEquals(newer, prefs().getString("library", null))
        assertTrue(File(directory, LibraryPendingWriteJournal.FILE_NAME).isFile)
    }

    @Test fun invalidTargetCannotReplaceExistingCatalog() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, "{invalid json")
        try {
            restore()
            fail("Invalid pending JSON must never be committed")
        } catch (_: Exception) { /* valid refusal */ }
        assertEquals(before, prefs().getString("library", null))
        assertTrue(File(directory, LibraryPendingWriteJournal.FILE_NAME).isFile)
    }

    @Test fun failedRecoveryCommitLeavesJournalForLaterRetry() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, desired)
        try {
            journal.recover(before, ::validate) { false }
            fail("Simulated commit refusal must be reported")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("JOURNAL_RECOVERY_COMMIT_FAILED"))
        }
        assertEquals(before, prefs().getString("library", null))
        assertTrue(File(directory, LibraryPendingWriteJournal.FILE_NAME).isFile)
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, restore())
    }

    @Test fun newerStagedWriteCanSupersedeOlderPendingWriteWithSameBase() {
        assertTrue(prefs().edit().putString("library", before).commit())
        journal.stage(before, desired)
        val latest = """{"media":[],"playlists":[{"id":"qa-next","name":"Latest","mediaIds":[]}]}"""
        journal.stage(before, latest)
        assertEquals(LibraryPendingWriteJournal.Recovery.RESTORED, restore())
        assertEquals(latest, prefs().getString("library", null))
    }
}
