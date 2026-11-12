package com.example.freelocalplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveRulesTest {
    private val id = "0123456789abcdef".repeat(4)

    @Test fun acceptsExplicitBackupPaths() {
        assertTrue(BackupArchiveRules.isAllowedPayloadPath("prefs.json"))
        for (suffix in listOf(".mid", ".chords", ".auto.chords", ".chord-edits")) {
            assertTrue(BackupArchiveRules.isAllowedPayloadPath("music/$id$suffix"))
        }
    }

    @Test fun rejectsTraversalAndExtraFiles() {
        for (name in listOf("../prefs.json", "music/../../x.mid", "music/./$id.mid",
            "music/another/$id.mid", "music\\$id.mid", "music/$id.mid/../../xx",
            "/music/$id.mid", "music/$id.mid.tmp", "files/backup.zip",
            "backup.xml", "music/$id.exe", "music/")) {
            assertFalse(name, BackupArchiveRules.isAllowedPayloadPath(name))
        }
    }

    @Test fun rejectsUppercaseAndNonHashMediaIds() {
        assertFalse(BackupArchiveRules.isMusicFilename("${id.uppercase()}.mid"))
        assertFalse(BackupArchiveRules.isMusicFilename("test.mid"))
        assertFalse(BackupArchiveRules.isMusicFilename("${id}x.mid"))
        assertTrue(BackupArchiveRules.isMusicFilename("$id.mid"))
    }

    @Test fun enforcesPerFileLimits() {
        assertTrue(BackupArchiveRules.isSafeDeclaredSize("music/$id.mid", 0))
        assertTrue(BackupArchiveRules.isSafeDeclaredSize("music/$id.mid", BackupArchiveRules.MAX_FILE_BYTES))
        assertFalse(BackupArchiveRules.isSafeDeclaredSize("music/$id.mid", BackupArchiveRules.MAX_FILE_BYTES + 1))
        assertFalse(BackupArchiveRules.isSafeDeclaredSize("prefs.json", -1))
        assertFalse(BackupArchiveRules.isSafeDeclaredSize("unknown", 1))
    }

    @Test fun protectsAgainstArchiveOverflow() {
        assertTrue(BackupArchiveRules.isSafeTotal(400, 600))
        assertFalse(BackupArchiveRules.isSafeTotal(BackupArchiveRules.MAX_UNCOMPRESSED_BYTES, 1))
        assertFalse(BackupArchiveRules.isSafeTotal(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFalse(BackupArchiveRules.isSafeTotal(0, -1))
    }

    @Test fun checksSha256Strictly() {
        assertTrue(BackupArchiveRules.isSha256("a".repeat(64)))
        assertFalse(BackupArchiveRules.isSha256("A".repeat(64)))
        assertFalse(BackupArchiveRules.isSha256("g".repeat(64)))
        assertFalse(BackupArchiveRules.isSha256("0".repeat(63)))
    }

    @Test fun pinBackupSchemaAndKnownPreferences() {
        assertEquals(1, BackupArchiveRules.VERSION)
        assertEquals(5, BackupArchiveRules.preferenceFiles.size)
        assertTrue("local_library_v1" in BackupArchiveRules.preferenceFiles)
        assertTrue("music_mode_offsets_v1" in BackupArchiveRules.preferenceFiles)
    }
}
