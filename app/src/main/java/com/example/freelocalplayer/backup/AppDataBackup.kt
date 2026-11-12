package com.example.freelocalplayer.backup

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local SAF backup of metadata, lyrics, playlists, favorites, MIDI, analysis and chord edits.
 * Media bytes are intentionally NOT included. SAF Uri grants are not portable to another app ID.
 * Do not change this format without a versioned migration.
 */
class AppDataBackup(private val context: Context) {
    private data class EntryInfo(val path: String, val size: Long, val digest: String)

    data class Inspection(
        val sourcePackage: String,
        val createdAtMs: Long,
        val tracks: Int,
        val playlists: Int,
        val musicFiles: Int,
        val bytes: Long
    )

    private data class Unpacked(val dir: File, val info: Inspection, val files: List<EntryInfo>)

    private val musicDir: File get() = File(context.filesDir, "music_mode_v1")
    private val safeBackup: File get() = File(context.filesDir, "flp-before-last-import.zip")
    private val restoreMarker: File get() = File(context.filesDir, "flp-restore-in-progress")

    private fun temporaryDirectory(prefix: String): File =
        File(context.cacheDir, "$prefix-${UUID.randomUUID()}").also {
            if (!it.mkdirs()) throw IOException("Невозможно создать временный каталог резервирования")
        }

    private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun sha256(file: File): String {
        val md = digest()
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun copyBounded(source: java.io.InputStream, destination: File, max: Long): Long {
        destination.parentFile?.mkdirs()
        var copied = 0L
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val size = source.read(buffer)
                if (size < 0) break
                if (copied > max - size) throw IOException("Превышен лимит размера данных")
                output.write(buffer, 0, size)
                copied += size
            }
            output.fd.sync()
        }
        return copied
    }

    /** Types are preserved: string sets, booleans, numeric values and original JSON text. */
    private fun snapshotPreferences(): JSONObject {
        val root = JSONObject()
        for (name in BackupArchiveRules.preferenceFiles) {
            val entries = JSONObject()
            val pref = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            for ((key, value) in pref.all) {
                val item = JSONObject()
                when (value) {
                    is String -> item.put("type", "string").put("value", value)
                    is Boolean -> item.put("type", "boolean").put("value", value)
                    is Int -> item.put("type", "int").put("value", value)
                    is Long -> item.put("type", "long").put("value", value.toString())
                    is Float -> {
                        if (!value.isFinite()) throw IOException("Неправильное значение настройки: $key")
                        item.put("type", "float").put("value", value.toDouble())
                    }
                    is Set<*> -> {
                        if (value.any { it !is String }) throw IOException("Некорректный набор: $key")
                        item.put("type", "strings").put("value", JSONArray(value.map { it as String }.sorted()))
                    }
                    null -> continue
                    else -> throw IOException("Неподдерживаемый тип настройки: $key")
                }
                entries.put(key, item)
            }
            root.put(name, entries)
        }
        return root
    }

    private fun getPrefTypes(root: JSONObject): Map<String, Map<String, Any>> {
        if (root.length() != BackupArchiveRules.preferenceFiles.size) {
            throw IOException("Неполный или неизвестный набор настроек архива")
        }
        val output = linkedMapOf<String, Map<String, Any>>()
        for (name in BackupArchiveRules.preferenceFiles) {
            val entries = root.optJSONObject(name) ?: throw IOException("Нет настроек $name")
            val values = linkedMapOf<String, Any>()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = entries.optJSONObject(key) ?: throw IOException("Повреждена настройка $key")
                val type = entry.optString("type")
                val value: Any = when (type) {
                    "string" -> entry.getString("value")
                    "boolean" -> {
                        if (entry.opt("value") !is Boolean) throw IOException("Неверный boolean $key")
                        entry.getBoolean("value")
                    }
                    "int" -> {
                        val n = entry.getLong("value")
                        if (n !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                            throw IOException("Int вне диапазона: $key")
                        }
                        n.toInt()
                    }
                    "long" -> entry.getString("value").toLongOrNull()
                        ?: throw IOException("Long вне диапазона: $key")
                    "float" -> {
                        val n = entry.getDouble("value")
                        if (!n.isFinite() || n > Float.MAX_VALUE || n < -Float.MAX_VALUE) {
                            throw IOException("Float вне диапазона: $key")
                        }
                        n.toFloat()
                    }
                    "strings" -> {
                        val array = entry.getJSONArray("value")
                        if (array.length() > 100_000) throw IOException("Слишком большой набор $key")
                        (0 until array.length()).map { array.getString(it) }.toSet()
                    }
                    else -> throw IOException("Неизвестный тип настройки: $type")
                }
                values[key] = value
            }
            output[name] = values
        }
        // Validate the library without interpreting its lyrics or changing any IDs.
        val library = (output["local_library_v1"]?.get("library") as? String) ?: "{}"
        val model = JSONObject(library)
        val media = model.optJSONArray("media") ?: JSONArray()
        val playlists = model.optJSONArray("playlists") ?: JSONArray()
        if (media.length() > 100_000 || playlists.length() > 100_000) {
            throw IOException("Недопустимый размер медиатеки в архиве")
        }
        for (index in 0 until media.length()) {
            val obj = media.optJSONObject(index) ?: throw IOException("Повреждённая запись медиатеки")
            if (obj.optString("id").isBlank() || obj.optString("uri").isBlank()) {
                throw IOException("Запись без ID или URI")
            }
        }
        for (index in 0 until playlists.length()) {
            val obj = playlists.optJSONObject(index) ?: throw IOException("Повреждённый плейлист")
            if (obj.optString("id").isBlank() || obj.optJSONArray("mediaIds") == null) {
                throw IOException("Недопустимая структура плейлиста")
            }
        }
        return output
    }

    private fun libraryCounts(prefs: JSONObject): Pair<Int, Int> {
        val text = prefs.getJSONObject("local_library_v1")
            .optJSONObject("library") // JSON entry is a typed object, not a library object
        val raw = text?.optString("value") ?: "{}"
        val lib = JSONObject(raw)
        return (lib.optJSONArray("media")?.length() ?: 0) to
            (lib.optJSONArray("playlists")?.length() ?: 0)
    }

    private fun createSnapshot(destination: File) {
        val dir = temporaryDirectory("flp-snapshot")
        try {
            val preferences = snapshotPreferences()
            getPrefTypes(preferences) // fail, rather than claiming a valid backup of corrupt data
            val prefs = File(dir, "prefs.json")
            prefs.writeText(preferences.toString(), Charsets.UTF_8)
            if (!BackupArchiveRules.isSafeDeclaredSize("prefs.json", prefs.length())) {
                throw IOException("Слишком большой каталог для формата резервной копии")
            }
            val sources = linkedMapOf("prefs.json" to prefs)
            if (musicDir.isDirectory) {
                for (source in musicDir.listFiles().orEmpty().sortedBy { it.name }) {
                    if (!source.isFile || !BackupArchiveRules.isMusicFilename(source.name)) continue
                    val path = "music/${source.name}"
                    if (!BackupArchiveRules.isSafeDeclaredSize(path, source.length())) {
                        throw IOException("Файл MIDI или аккордов слишком большой: ${source.name}")
                    }
                    val copied = File(dir, path)
                    FileInputStream(source).use { input ->
                        copyBounded(input, copied, BackupArchiveRules.MAX_FILE_BYTES)
                    }
                    sources[path] = copied
                }
            }
            if (sources.size > BackupArchiveRules.MAX_ENTRY_COUNT - 1) {
                throw IOException("Слишком много файлов для резервной копии")
            }
            val manifest = JSONObject()
                .put("format", BackupArchiveRules.FORMAT)
                .put("version", BackupArchiveRules.VERSION)
                .put("createdAtMs", System.currentTimeMillis())
                .put("sourcePackage", context.packageName)
            val entries = JSONArray()
            var total = 0L
            for ((path, file) in sources) {
                if (!BackupArchiveRules.isSafeTotal(total, file.length())) {
                    throw IOException("Слишком большой архив")
                }
                total += file.length()
                entries.put(JSONObject()
                    .put("path", path)
                    .put("size", file.length())
                    .put("sha256", sha256(file)))
            }
            manifest.put("entries", entries).put("totalBytes", total)
            val manifestFile = File(dir, "manifest.json")
            manifestFile.writeText(manifest.toString(), Charsets.UTF_8)
            if (manifestFile.length() > BackupArchiveRules.MAX_MANIFEST_BYTES) {
                throw IOException("Слишком большой manifest")
            }
            sources["manifest.json"] = manifestFile
            destination.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(destination)).use { output ->
                for ((path, file) in sources) {
                    output.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(output, 16 * 1024) }
                    output.closeEntry()
                }
            }
            if (destination.length() > BackupArchiveRules.MAX_ARCHIVE_BYTES) {
                throw IOException("Размер ZIP превышает лимит")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Creates, verifies, then copies the archive to user-selected SAF destination. */
    fun exportTo(uri: Uri): Inspection {
        val file = File.createTempFile("flp-export-", ".zip", context.cacheDir)
        try {
            createSnapshot(file)
            val inspection = inspect(file)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 16 * 1024) }
                output.flush()
            } ?: throw IOException("Нет доступа к выбранному файлу")
            // Verify the SAF provider actually stored the bytes we produced.
            val expected = sha256(file)
            val fromProvider = context.contentResolver.openInputStream(uri)?.use { input ->
                val md = digest()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    md.update(buffer, 0, count)
                }
                md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            } ?: throw IOException("SAF не дал проверить созданный ZIP")
            if (fromProvider != expected) throw IOException("Ошибка проверки ZIP после записи")
            return inspection
        } finally {
            file.delete()
        }
    }

    /** Copy untrusted SAF content once; preview and confirmed restore use the same bytes. */
    fun stageFromUri(uri: Uri): Pair<File, Inspection> {
        val file = File.createTempFile("flp-import-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copyBounded(input, file, BackupArchiveRules.MAX_ARCHIVE_BYTES)
            } ?: throw IOException("Не удалось прочесть выбранный архив")
            return file to inspect(file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun inspect(file: File): Inspection {
        val unpacked = unpackAndValidate(file)
        return try { unpacked.info } finally { unpacked.dir.deleteRecursively() }
    }

    private fun unpackAndValidate(file: File): Unpacked {
        if (!file.isFile || file.length() > BackupArchiveRules.MAX_ARCHIVE_BYTES) {
            throw IOException("Неверный размер или отсутствие ZIP")
        }
        val dir = temporaryDirectory("flp-checked")
        try {
            val actual = linkedMapOf<String, EntryInfo>()
            var total = 0L
            ZipInputStream(FileInputStream(file)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name
                    if (entry.isDirectory || (path != "manifest.json" &&
                                !BackupArchiveRules.isAllowedPayloadPath(path)) ||
                        path in actual || actual.size >= BackupArchiveRules.MAX_ENTRY_COUNT
                    ) throw IOException("Неизвестная, повторная или опасная запись ZIP: $path")
                    val destination = File(dir, path)
                    val count = copyBounded(zip, destination, BackupArchiveRules.maxEntryBytes(path))
                    if (!BackupArchiveRules.isSafeTotal(total, count)) {
                        throw IOException("Архив превышает допустимый распакованный размер")
                    }
                    total += count
                    actual[path] = EntryInfo(path, count, sha256(destination))
                    zip.closeEntry()
                }
            }
            val manifestFile = File(dir, "manifest.json")
            val prefsFile = File(dir, "prefs.json")
            if (!manifestFile.isFile || !prefsFile.isFile) {
                throw IOException("Отсутствуют обязательные файлы резервной копии")
            }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            if (manifest.optString("format") != BackupArchiveRules.FORMAT ||
                manifest.optInt("version", -1) != BackupArchiveRules.VERSION) {
                throw IOException("Неподдерживаемый тип или версия архива")
            }
            val declared = manifest.getJSONArray("entries")
            if (declared.length() != actual.size - 1) {
                throw IOException("Неверное количество записей в manifest")
            }
            val listed = hashSetOf<String>()
            var expectedTotal = 0L
            for (index in 0 until declared.length()) {
                val item = declared.getJSONObject(index)
                val path = item.getString("path")
                val size = item.getLong("size")
                val hash = item.getString("sha256")
                val found = actual[path]
                if (!BackupArchiveRules.isAllowedPayloadPath(path) || !listed.add(path) ||
                    !BackupArchiveRules.isSafeDeclaredSize(path, size) ||
                    !BackupArchiveRules.isSha256(hash) || found == null ||
                    found.size != size || found.digest != hash ||
                    !BackupArchiveRules.isSafeTotal(expectedTotal, size)
                ) throw IOException("Контрольная сумма/размер или путь не совпадают: $path")
                expectedTotal += size
            }
            if (listed != actual.keys.minus("manifest.json") ||
                expectedTotal != manifest.getLong("totalBytes")) {
                throw IOException("Manifest не соответствует содержимому ZIP")
            }
            val prefs = JSONObject(prefsFile.readText(Charsets.UTF_8))
            getPrefTypes(prefs)
            val (tracks, playlists) = libraryCounts(prefs)
            val result = Inspection(
                sourcePackage = manifest.getString("sourcePackage"),
                createdAtMs = manifest.getLong("createdAtMs"),
                tracks = tracks,
                playlists = playlists,
                musicFiles = listed.count { it.startsWith("music/") },
                bytes = expectedTotal
            )
            return Unpacked(dir, result, actual.values.toList())
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    private fun atomicReplace(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".flp-restore-new")
        try {
            FileInputStream(source).use { input -> copyBounded(input, temp, BackupArchiveRules.MAX_FILE_BYTES) }
            try {
                Files.move(temp.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    private fun applyValidatedData(checked: Unpacked) {
        val prefs = JSONObject(File(checked.dir, "prefs.json").readText(Charsets.UTF_8))
        val values = getPrefTypes(prefs)
        if (!musicDir.exists() && !musicDir.mkdirs()) throw IOException("Нет каталога MIDI и аккордов")
        val requested = checked.files.asSequence()
            .map { it.path }.filter { it.startsWith("music/") }
            .map { it.removePrefix("music/") }.toSet()
        // Write new files first; a persistent journal allows rollback after a process crash.
        for (name in requested.sorted()) {
            atomicReplace(File(checked.dir, "music/$name"), File(musicDir, name))
        }
        for (old in musicDir.listFiles().orEmpty()) {
            if (old.isFile && BackupArchiveRules.isMusicFilename(old.name) && old.name !in requested) {
                if (!old.delete()) throw IOException("Не удалось заменить старый файл ${old.name}")
            }
        }
        for (name in BackupArchiveRules.preferenceFiles) {
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for ((key, value) in values.getValue(name)) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> throw IOException("Неподдерживаемое значение настройки: $key")
                }
            }
            if (!editor.commit()) throw IOException("Не удалось записать настройки $name")
        }
    }

    private fun applyZip(file: File): Inspection {
        val checked = unpackAndValidate(file)
        return try { applyValidatedData(checked); checked.info }
        finally { checked.dir.deleteRecursively() }
    }

    fun hasInterruptedRestore(): Boolean = restoreMarker.exists()

    /** If the process died after the first write, restore original state before UI starts. */
    fun recoverPendingRestoreIfAny(): Boolean {
        if (!restoreMarker.exists()) return false
        if (!safeBackup.isFile) throw IOException("Нет защитной копии для восстановления после сбоя")
        applyZip(safeBackup)
        if (!restoreMarker.delete()) throw IOException("Не удалось завершить восстановление после сбоя")
        return true
    }

    /** Import is validated before modifying private files and keeps a full rollback snapshot. */
    fun restoreVerified(file: File): Inspection {
        if (restoreMarker.exists()) throw IOException("Не завершено прежнее восстановление")
        val imported = inspect(file)
        val snapshotTemp = File(context.filesDir, "flp-safety-tmp.zip")
        try {
            createSnapshot(snapshotTemp)
            inspect(snapshotTemp)
            try {
                Files.move(snapshotTemp.toPath(), safeBackup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(snapshotTemp.toPath(), safeBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            FileOutputStream(restoreMarker).use { out ->
                out.write("restore-v1".toByteArray(Charsets.US_ASCII))
                out.fd.sync()
            }
            try {
                val applied = applyZip(file)
                if (!restoreMarker.delete()) throw IOException("Не удалось закрыть журнал восстановления")
                return applied
            } catch (error: Exception) {
                try {
                    applyZip(safeBackup)
                    if (!restoreMarker.delete()) throw IOException("Не удалось снять маркер отката")
                } catch (rollbackError: Exception) {
                    error.addSuppressed(rollbackError)
                    // Journal remains in place, forcing rollback again on next app launch.
                }
                throw IOException("Импорт не завершён; выполнен откат или требуется восстановление: ${error.message}", error)
            }
        } finally { snapshotTemp.delete() }
    }
}
