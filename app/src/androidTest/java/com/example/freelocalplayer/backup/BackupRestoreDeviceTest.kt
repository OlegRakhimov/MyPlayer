package com.example.freelocalplayer.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import com.example.freelocalplayer.data.room.ShadowRoomMapping
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real AppDataBackup ZIP/restore tests, with ZERO access to the user's production
 * prefs, music attachments or Room database. No adb uninstall, pm clear or real SAF data.
 *
 * All five preferences are redirected to uniquely prefixed test-only names; filesDir
 * and cacheDir are redirected into a fresh test-only directory under targetContext.
 * This also works when the emulator already contains a test music library.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {
    private class SandboxContext(base: Context) : ContextWrapper(base) {
        private val token = UUID.randomUUID().toString().replace("-", "")
        private val root = File(base.getFilesDir(), "flp-restore-qa-$token")
        private val fileRoot = File(root, "files")
        private val cacheRoot = File(root, "cache")
        private val names = BackupArchiveRules.preferenceFiles.toSet()
        @Volatile var failNextCommitFor: String? = null

        init {
            check(fileRoot.mkdirs() && cacheRoot.mkdirs()) {
                "Cannot create isolated test-only storage"
            }
            check(root.canonicalPath != base.getFilesDir().canonicalPath) {
                "Refusing to test against the application filesDir"
            }
            check(root.canonicalPath.startsWith(base.getFilesDir().canonicalPath + File.separator)) {
                "Test directory escaped the app sandbox"
            }
        }

        override fun getFilesDir(): File = fileRoot
        override fun getCacheDir(): File = cacheRoot

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            require(name != null && name in names) {
                "Backup QA cannot access an unrecognized SharedPreferences name: $name"
            }
            val actual = baseContext.getSharedPreferences("__flp_restore_qa_${token}_$name", mode)
            // Inject a single failed commit for a genuine mid-restore rollback test.
            // The backing preferences are ALWAYS test-prefixed, never production prefs.
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val backingEditor = actual.edit()
                    return object : SharedPreferences.Editor by backingEditor {
                        override fun clear(): SharedPreferences.Editor {
                            backingEditor.clear()
                            return this
                        }

                        override fun commit(): Boolean {
                            if (failNextCommitFor == name) {
                                failNextCommitFor = null
                                return false
                            }
                            return backingEditor.commit()
                        }
                    }
                }
            }
        }

        fun cleanup() {
            for (name in names) {
                check(baseContext.deleteSharedPreferences("__flp_restore_qa_${token}_$name")) {
                    "Could not delete isolated preference: $name"
                }
            }
            check(root.deleteRecursively()) { "Could not remove temporary backup QA data" }
        }
    }

    private data class StateSnapshot(
        val preferences: Map<String, Map<String, Any?>>,
        val musicFiles: Map<String, List<Byte>>
    )

    private lateinit var sandbox: SandboxContext
    private lateinit var backup: AppDataBackup
    private val songId = "qa-existing-song-uuid"
    private val otherId = "qa-second-song-uuid"
    private val removedId = "qa-retired-song-uuid"

    @Before
    fun createSandbox() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = SandboxContext(target)
        backup = AppDataBackup(sandbox)
        seedOriginalData()
    }

    @After
    fun removeSandbox() {
        if (this::sandbox.isInitialized) sandbox.cleanup()
    }

    private fun prefs(name: String): SharedPreferences =
        sandbox.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun musicDirectory(): File = File(sandbox.getFilesDir(), "music_mode_v1").also {
        check(it.isDirectory || it.mkdirs())
    }

    private fun originalLibrary(): String {
        val song = JSONObject()
            .put("id", songId)
            .put("uri", "content://media/external/audio/media/191")
            .put("title", "Тестовая песня 🎵")
            .put("video", false)
            .put("lyrics", "[00:01.10]<00:01.12>Ка-ра-о-ке")
            .put("sourceKey", "mediastore:external:audio:191")
            .put("sizeBytes", 144004L)
            .put("durationMs", 198000L)
        val second = JSONObject()
            .put("id", otherId)
            .put("uri", "content://media/external/video/media/52")
            .put("title", "Видео тест")
            .put("video", true)
            .put("lyrics", "Субтитры TXT")
        val retired = JSONObject()
            .put("entry", JSONObject()
                .put("id", removedId)
                .put("uri", "content://media/external/audio/media/92")
                .put("title", "Ранее удалённая")
                .put("video", false)
                .put("lyrics", "[00:09.00]Удалённый текст"))
            .put("playlistPlaces", JSONArray().put(JSONObject()
                .put("playlistId", "qa-playlist-1").put("index", 1)))
        val one = JSONObject().put("id", "qa-playlist-1")
            .put("name", "Практика 🎸")
            .put("mediaIds", JSONArray().put(songId).put(otherId))
        val two = JSONObject().put("id", "qa-playlist-2")
            .put("name", "Избранное")
            .put("mediaIds", JSONArray().put(otherId).put(songId))
        return JSONObject().put("media", JSONArray().put(song).put(second))
            .put("playlists", JSONArray().put(one).put(two))
            .put("retiredMedia", JSONArray().put(retired))
            .toString()
    }

    private fun seedOriginalData() {
        assertTrue(prefs("local_library_v1").edit()
            .putString("library", originalLibrary())
            .putBoolean("is_test", true)
            .commit())
        assertTrue(prefs("player_shortcuts_v1").edit()
            .putString("recent_ids", "$otherId|$songId")
            .putStringSet("favorite_ids", setOf(songId, otherId))
            .commit())
        assertTrue(prefs("music_mode_offsets_v1").edit()
            .putLong("qa-offset", -1250L)
            .putInt("qa-instrument", 3)
            .commit())
        assertTrue(prefs("freelocalplayer_appearance").edit()
            .putString("palette", "STUDIO_DARK")
            .putFloat("qa_scale", 1.25f)
            .commit())
        assertTrue(prefs("media_discovery_v1").edit()
            .putBoolean("asked_once", true)
            .commit())
        val digest = sha256(songId)
        val music = musicDirectory()
        File(music, "$digest.mid").writeBytes(byteArrayOf(0x4d, 0x54, 0x68, 0x64, 0, 0, 0, 6))
        File(music, "$digest.chords").writeText("0 Cmaj7\n1200 Am7\n", Charsets.UTF_8)
        File(music, "$digest.auto.chords").writeText("0 C\n500 G\n", Charsets.UTF_8)
        File(music, "$digest.chord-edits").writeText("1000|Dm|manual\n", Charsets.UTF_8)
    }

    private fun snapshot(): StateSnapshot {
        val settings = BackupArchiveRules.preferenceFiles.associateWith { name ->
            prefs(name).all.mapValues { (_, value) ->
                if (value is Set<*>) value.toSet() else value
            }
        }
        val music = musicDirectory().listFiles().orEmpty()
            .filter { it.isFile && BackupArchiveRules.isMusicFilename(it.name) }
            .associate { it.name to it.readBytes().toList() }
        return StateSnapshot(settings, music)
    }

    private fun mutateData() {
        assertTrue(prefs("local_library_v1").edit().clear()
            .putString("library", JSONObject().put("media", JSONArray())
                .put("playlists", JSONArray()).toString())
            .commit())
        assertTrue(prefs("player_shortcuts_v1").edit().clear()
            .putString("recent_ids", "mutated-uuid")
            .putStringSet("favorite_ids", setOf("mutated"))
            .commit())
        assertTrue(prefs("music_mode_offsets_v1").edit().clear().putInt("qa-instrument", 99).commit())
        assertTrue(prefs("freelocalplayer_appearance").edit().clear()
            .putString("palette", "SOFT_LIGHT").commit())
        assertTrue(prefs("media_discovery_v1").edit().clear().putBoolean("asked_once", false).commit())
        val music = musicDirectory()
        val original = File(music, "${sha256(songId)}.chords")
        original.writeText("BAD CHORDS", Charsets.UTF_8)
        File(music, "${sha256(songId)}.mid").delete()
        File(music, "${sha256(otherId)}.chords").writeText("TEMPORARY", Charsets.UTF_8)
    }

    private fun exportFile(name: String): File {
        val destination = File(sandbox.getFilesDir(), name)
        val report = backup.exportTo(Uri.fromFile(destination))
        assertEquals(2, report.tracks)
        assertEquals(2, report.playlists)
        assertEquals(4, report.musicFiles)
        assertTrue(report.bytes > 0L)
        assertTrue(destination.isFile && destination.length() > 100L)
        return destination
    }

    private fun expectRejected(description: String, action: () -> Unit) {
        try {
            action()
            fail("Unsafe ZIP was accepted: $description")
        } catch (expected: IOException) {
            assertTrue("Rejected $description without modifying saved data", expected.message != null)
        }
    }

    private fun rewriteZip(original: File, output: File, mutate: (String, ByteArray) -> ByteArray,
                           extraPath: String? = null) {
        ZipInputStream(FileInputStream(original)).use { input ->
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val body = input.readBytes()
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(mutate(entry.name, body))
                    zip.closeEntry()
                    input.closeEntry()
                }
                if (extraPath != null) {
                    zip.putNextEntry(ZipEntry(extraPath))
                    zip.write("DO-NOT-WRITE".toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }

    @Test
    fun fullZipRoundTripRestoresUuidLyricsPlaylistsSettingsAndMusic() {
        val before = snapshot()
        val zip = exportFile("original.zip")
        val (staged, preview) = backup.stageFromUri(Uri.fromFile(zip))
        try {
            assertEquals(sandbox.packageName, preview.sourcePackage)
            mutateData()
            assertNotEquals(before, snapshot())
            val restored = backup.restoreVerified(staged)
            assertEquals(2, restored.tracks)
            assertEquals(before, snapshot())
            assertFalse(backup.hasInterruptedRestore())
            assertFalse(backup.recoverPendingRestoreIfAny())
            assertTrue(File(sandbox.getFilesDir(), "flp-before-last-import.zip").isFile)
            assertEquals(originalLibrary(), prefs("local_library_v1").getString("library", null))
        } finally {
            staged.delete()
        }
    }

    @Test
    fun checksumTamperIsRejectedWithoutChangingExistingData() {
        val original = exportFile("valid.zip")
        val corrupt = File(sandbox.getFilesDir(), "checksum-mismatch.zip")
        rewriteZip(original, corrupt, { name, bytes ->
            if (name == "prefs.json") bytes + byteArrayOf(0x20) else bytes
        })
        mutateData()
        val before = snapshot()
        expectRejected("modified payload with original checksum") { backup.inspect(corrupt) }
        expectRejected("restore from modified payload") { backup.restoreVerified(corrupt) }
        assertEquals(before, snapshot())
        assertFalse(backup.hasInterruptedRestore())
    }

    @Test
    fun traversalEntryIsRejectedWithoutWritingOutsideSandbox() {
        val original = exportFile("safe.zip")
        val attack = File(sandbox.getFilesDir(), "path-traversal.zip")
        rewriteZip(original, attack, { _, bytes -> bytes }, "../../outside-test.txt")
        val before = snapshot()
        expectRejected("ZIP path traversal") { backup.restoreVerified(attack) }
        assertEquals(before, snapshot())
        assertFalse(File(sandbox.getCacheDir().parentFile, "outside-test.txt").exists())
        assertFalse(backup.hasInterruptedRestore())
    }

    @Test
    fun interruptedImportCanRecoverOriginalSnapshotAndClearMarker() {
        val before = snapshot()
        val saved = exportFile("pre-interruption.zip")
        saved.copyTo(File(sandbox.getFilesDir(), "flp-before-last-import.zip"), overwrite = true)
        mutateData()
        val changed = snapshot()
        assertNotEquals(before, changed)
        File(sandbox.getFilesDir(), "flp-restore-in-progress").writeText("restore-v1", Charsets.US_ASCII)
        assertTrue(backup.hasInterruptedRestore())
        assertTrue(backup.recoverPendingRestoreIfAny())
        assertEquals(before, snapshot())
        assertFalse(backup.hasInterruptedRestore())
        assertFalse(backup.recoverPendingRestoreIfAny())
    }

    @Test
    fun missingSafetyArchivePreventsUnverifiedCrashRecovery() {
        mutateData()
        val before = snapshot()
        File(sandbox.getFilesDir(), "flp-restore-in-progress").writeText("restore-v1", Charsets.US_ASCII)
        expectRejected("interrupted restore without safety archive") {
            backup.recoverPendingRestoreIfAny()
        }
        assertEquals(before, snapshot())
        assertTrue(backup.hasInterruptedRestore())
    }

    @Test
    fun failedCommitDuringImportAutomaticallyRollsBackAllPreviousWrites() {
        val original = exportFile("original-for-rollback.zip")
        mutateData()
        val beforeImport = snapshot()
        // Deliberately fail the third SharedPreferences commit, after the ZIP has
        // already replaced music files and earlier preferences. Recovery must be atomic
        // from the user's perspective even though the payload spans multiple files.
        sandbox.failNextCommitFor = "music_mode_offsets_v1"
        expectRejected("injected commit failure during apply") {
            backup.restoreVerified(original)
        }
        assertEquals(beforeImport, snapshot())
        assertFalse(backup.hasInterruptedRestore())
        assertTrue(File(sandbox.getFilesDir(), "flp-before-last-import.zip").isFile)
    }

    @Test
    fun restoredCatalogCanBeMappedToRoomWithoutChangingAnyUuid() {
        val original = exportFile("room-input.zip")
        mutateData()
        backup.restoreVerified(original)
        val text = prefs("local_library_v1").getString("library", null)
            ?: throw IOException("Restored library is missing")
        val json = JSONObject(text)
        val media = json.getJSONArray("media")
        val playlists = json.getJSONArray("playlists")
        val retired = json.getJSONArray("retiredMedia")
        val state = LibraryState(
            media = (0 until media.length()).map { index ->
                val item = media.getJSONObject(index)
                MediaEntry(
                    id = item.getString("id"), uri = item.getString("uri"),
                    title = item.getString("title"), isVideo = item.getBoolean("video"),
                    lyrics = item.optString("lyrics"), sourceKey = item.optString("sourceKey"),
                    sizeBytes = item.optLong("sizeBytes", -1L),
                    durationMs = item.optLong("durationMs", -1L)
                )
            },
            playlists = (0 until playlists.length()).map { index ->
                val item = playlists.getJSONObject(index)
                val ids = item.getJSONArray("mediaIds")
                UserPlaylist(item.getString("id"), item.getString("name"),
                    (0 until ids.length()).map { ids.getString(it) })
            },
            retiredMedia = (0 until retired.length()).map { index ->
                val item = retired.getJSONObject(index)
                val entry = item.getJSONObject("entry")
                val places = item.getJSONArray("playlistPlaces")
                RetiredMedia(
                    MediaEntry(entry.getString("id"), entry.getString("uri"),
                        entry.getString("title"), entry.getBoolean("video"),
                        entry.optString("lyrics"), entry.optString("sourceKey"),
                        entry.optLong("sizeBytes", -1L), entry.optLong("durationMs", -1L)),
                    (0 until places.length()).map { n ->
                        val place = places.getJSONObject(n)
                        PlaylistPlace(place.getString("playlistId"), place.getInt("index"))
                    }
                )
            }
        )
        val rows = ShadowRoomMapping.toRows(state)
        assertEquals(state, rows.reconstruct())
        assertEquals(listOf(songId, otherId), state.media.map { it.id })
        assertEquals(removedId, state.retiredMedia.single().entry.id)
        assertEquals(listOf(songId, otherId), state.playlists[0].mediaIds)
        assertTrue(ShadowRoomMapping.fingerprint(rows).length == 64)
    }
}
