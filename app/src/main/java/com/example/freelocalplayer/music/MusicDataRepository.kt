package com.example.freelocalplayer.music

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** Stores user-selected score files ONLY in app-private storage. No cloud/API calls. */
class MusicDataRepository(private val context: Context) {
    private val directory: File get() = File(context.filesDir, "music_mode_v1").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("music_mode_offsets_v1", Context.MODE_PRIVATE)

    private fun safeId(mediaId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(mediaId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun path(mediaId: String, suffix: String) = File(directory, safeId(mediaId) + suffix)

    fun loadScore(mediaId: String): MusicScore? {
        val file = path(mediaId, ".mid")
        return if (file.isFile) MidiScoreParser.parse(file.readBytes()) else null
    }

    fun loadChords(mediaId: String): String = path(mediaId, ".chords").takeIf { it.isFile }
        ?.readText(Charsets.UTF_8).orEmpty()

    /** Separate automatically estimated chords from authoritative user edits. */
    fun loadAudioEstimate(mediaId: String): List<ChordCue> =
        path(mediaId, ".auto.chords").takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.let(ChordTimeline::parse)
            ?.map { it.copy(source = "audio-estimate") }
            .orEmpty()

    fun saveAudioEstimate(mediaId: String, cues: List<ChordCue>) {
        if (cues.isEmpty()) throw IOException("Не удалось распознать аккорды")
        val content = cues.sortedBy { it.timeMs }.take(3000).joinToString("\n") { cue ->
            val milliseconds = cue.timeMs.coerceIn(0L, 59_999_999L)
            val minutes = milliseconds / 60_000
            val seconds = (milliseconds / 1000) % 60
            val ms = milliseconds % 1000
            "[%02d:%02d.%03d] %s".format(java.util.Locale.ROOT, minutes, seconds, ms, cue.name)
        }
        atomicWrite(path(mediaId, ".auto.chords"), content.toByteArray(Charsets.UTF_8))
    }

    fun deleteAudioEstimate(mediaId: String) { path(mediaId, ".auto.chords").delete() }

    /** Corrections are independent of MIDI, manual charts and audio estimates. */
    fun loadChordCorrections(mediaId: String): List<ChordCorrection> =
        path(mediaId, ".chord-edits").takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)?.let(ChordCorrections::decode).orEmpty()

    @Synchronized
    fun saveChordCorrection(mediaId: String, correction: ChordCorrection) {
        val changed = ChordCorrections.upsert(loadChordCorrections(mediaId), correction)
        atomicWrite(path(mediaId, ".chord-edits"), ChordCorrections.encode(changed).toByteArray(Charsets.UTF_8))
    }

    @Synchronized
    fun removeChordCorrection(mediaId: String, source: String, timeMs: Long) {
        val changed = ChordCorrections.remove(loadChordCorrections(mediaId), source, timeMs)
        atomicWrite(path(mediaId, ".chord-edits"), ChordCorrections.encode(changed).toByteArray(Charsets.UTF_8))
    }

    fun loadOffset(mediaId: String): Long = preferences.getLong(safeId(mediaId), 0L)

    fun saveOffset(mediaId: String, offset: Long) {
        preferences.edit().putLong(safeId(mediaId), offset.coerceIn(-3_600_000, 3_600_000)).apply()
    }

    fun importMidi(mediaId: String, uri: Uri): MusicScore {
        val bytes = readLimited(uri, 10 * 1024 * 1024)
        val score = MidiScoreParser.parse(bytes)
        if (score.noteCount == 0) throw IOException("В MIDI-файле не обнаружены ноты")
        atomicWrite(path(mediaId, ".mid"), bytes)
        return score
    }

    fun importChords(mediaId: String, uri: Uri): Int {
        val text = readLimited(uri, 100_000).toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return saveChords(mediaId, text)
    }

    fun saveChords(mediaId: String, text: String): Int {
        val cues = ChordTimeline.parse(text)
        if (text.isNotBlank() && cues.isEmpty()) {
            throw IOException("Формат аккордов: [00:12.50] Am — одна строка на аккорд")
        }
        atomicWrite(path(mediaId, ".chords"), text.toByteArray(Charsets.UTF_8))
        return cues.size
    }

    fun deleteScore(mediaId: String) { path(mediaId, ".mid").delete() }

    private fun readLimited(uri: Uri, maxBytes: Int): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val len = stream.read(block)
                if (len < 0) break
                if (out.size() + len > maxBytes) throw IOException("Файл слишком большой (лимит ${maxBytes / 1024} КБ)")
                out.write(block, 0, len)
            }
            out.toByteArray()
        } ?: throw IOException("Не получилось открыть файл")
    }

    private fun atomicWrite(destination: File, bytes: ByteArray) {
        val temporary = File(directory, destination.name + ".tmp")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }
}
