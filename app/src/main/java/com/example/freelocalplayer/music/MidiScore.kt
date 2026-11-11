package com.example.freelocalplayer.music

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.util.ArrayDeque
import kotlin.math.max

/** Standard MIDI File (SMF) 0/1, PPQ division. All times in milliseconds. */
data class ScoreNote(
    val pitch: Int,
    val startMs: Long,
    val endMs: Long,
    val velocity: Int,
    val channel: Int
)

data class ScorePart(
    val id: String,
    val name: String,
    val instrument: String,
    val family: PartFamily,
    val notes: List<ScoreNote>
)

enum class PartFamily(val title: String) {
    VOCAL("Вокал"), GUITAR("Гитара"), BASS("Бас"), KEYS("Клавиши"), DRUMS("Ударные"), OTHER("Другие")
}

data class MusicScore(val parts: List<ScorePart>, val durationMs: Long, val beatsPerMinute: Int) {
    val noteCount: Int get() = parts.sumOf { it.notes.size }
}

private data class RawNote(val pitch: Int, val startTick: Long, val endTick: Long, val velocity: Int, val channel: Int)
private data class RawPart(val name: String, val program: Int, val channel: Int, val notes: List<RawNote>)
private data class Tempo(val tick: Long, val microsecondsPerQuarter: Long, val order: Int)
private data class TempoPoint(val tick: Long, val micros: Long, val accumulatedMicros: Long)

/** No external MIDI library, no upload or network access. */
object MidiScoreParser {
    private const val MAX_FILE = 10 * 1024 * 1024
    private const val MAX_EVENTS_PER_TRACK = 300_000
    private const val MAX_NOTES = 120_000

    @Throws(IOException::class)
    fun parse(bytes: ByteArray): MusicScore {
        if (bytes.size > MAX_FILE) throw IOException("MIDI больше 10 МБ")
        val source = DataInputStream(ByteArrayInputStream(bytes))
        if (readFourcc(source) != "MThd") throw IOException("Это не стандартный MIDI-файл")
        val headerSize = source.readInt()
        if (headerSize < 6 || headerSize > 1024) throw IOException("Некорректный MIDI-заголовок")
        val format = source.readUnsignedShort()
        val trackCount = source.readUnsignedShort()
        val division = source.readUnsignedShort()
        if (format !in 0..1 || trackCount !in 1..128) throw IOException("Поддерживаются MIDI формата 0 и 1")
        if (division == 0 || (division and 0x8000) != 0) throw IOException("MIDI с SMPTE-временем пока не поддерживается")
        if (headerSize > 6) skipExactly(source, headerSize - 6)

        val tempos = mutableListOf(Tempo(0, 500_000L, -1))
        val raw = mutableListOf<RawPart>()
        var globalEvents = 0
        for (trackNo in 0 until trackCount) {
            if (readFourcc(source) != "MTrk") throw IOException("Не найден MIDI-трек №${trackNo + 1}")
            val len = source.readInt()
            if (len < 0 || len > MAX_FILE || len > source.available()) throw IOException("Повреждён MIDI-трек")
            val reader = MidiReader(ByteArray(len).also { source.readFully(it) })
            var tick = 0L
            var status = 0
            var trackName = ""
            var events = 0
            val program = IntArray(16)
            val noteStarts = HashMap<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
            val byChannel = HashMap<Int, MutableList<RawNote>>()
            while (reader.hasRemaining()) {
                if (++events > MAX_EVENTS_PER_TRACK || ++globalEvents > 500_000) {
                    throw IOException("Слишком много MIDI-событий")
                }
                val delta = reader.varLen()
                tick += delta
                if (tick > 10_000_000_000L) throw IOException("Некорректная длительность MIDI")
                val first = reader.byte()
                val command = if (first >= 0x80) { status = first; first } else {
                    if (status !in 0x80..0xEF) throw IOException("Повреждён MIDI: running status")
                    reader.rewindOne(); status
                }
                when {
                    command == 0xFF -> {
                        val kind = reader.byte()
                        val length = reader.varLen().toInt()
                        if (length < 0 || length > reader.left()) throw IOException("Повреждён MIDI meta event")
                        val data = reader.bytes(length)
                        if (kind == 0x03 && trackName.isBlank()) trackName = data.toString(Charsets.UTF_8).trim().take(90)
                        if (kind == 0x51 && length == 3) {
                            val value = ((data[0].toInt() and 255) shl 16) or
                                ((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)
                            if (value in 10000..10_000_000) tempos.add(Tempo(tick, value.toLong(), globalEvents))
                        }
                        if (kind == 0x2F) break
                        status = 0
                    }
                    command == 0xF0 || command == 0xF7 -> {
                        val length = reader.varLen().toInt()
                        reader.skip(length)
                        status = 0
                    }
                    command in 0x80..0xEF -> {
                        val channel = command and 0x0F
                        val operation = command and 0xF0
                        val p1 = reader.byte()
                        val p2 = if (operation == 0xC0 || operation == 0xD0) 0 else reader.byte()
                        when (operation) {
                            0xC0 -> program[channel] = p1
                            0x90 -> if (p2 > 0) {
                                noteStarts.getOrPut(channel to p1) { ArrayDeque() }.addLast(tick to p2)
                            } else completeNote(noteStarts, byChannel, channel, p1, tick)
                            0x80 -> completeNote(noteStarts, byChannel, channel, p1, tick)
                        }
                    }
                    else -> throw IOException("Неизвестное MIDI-событие $command")
                }
            }
            noteStarts.forEach { (key, starts) ->
                while (starts.isNotEmpty()) {
                    val (start, velocity) = starts.removeFirst()
                    if (tick > start) byChannel.getOrPut(key.first) { mutableListOf() }
                        .add(RawNote(key.second, start, tick, velocity, key.first))
                }
            }
            byChannel.toSortedMap().forEach { (channel, notes) ->
                val name = if (trackName.isBlank()) "Дорожка ${trackNo + 1}" else trackName
                raw += RawPart(name, program[channel], channel, notes)
            }
        }
        if (raw.sumOf { it.notes.size } > MAX_NOTES) throw IOException("Слишком много MIDI-нот")
        val sortedTempo = tempos.sortedWith(compareBy<Tempo> { it.tick }.thenBy { it.order })
        val points = mutableListOf<TempoPoint>()
        var previousTick = 0L
        var previousTempo = 500_000L
        var accumulated = 0L
        for (tempo in sortedTempo) {
            accumulated += (tempo.tick - previousTick) * previousTempo / division
            if (points.lastOrNull()?.tick == tempo.tick) points.removeAt(points.lastIndex)
            points.add(TempoPoint(tempo.tick, tempo.microsecondsPerQuarter, accumulated))
            previousTick = tempo.tick
            previousTempo = tempo.microsecondsPerQuarter
        }
        fun time(tick: Long): Long {
            val index = points.binarySearch { it.tick.compareTo(tick) }
            val point = points[if (index >= 0) index else max(0, -index - 2)]
            return (point.accumulatedMicros + (tick - point.tick) * point.micros / division) / 1000
        }
        val parts = raw.mapIndexed { index, p ->
            val mapped = p.notes.map { n ->
                ScoreNote(n.pitch, time(n.startTick), max(time(n.startTick) + 1, time(n.endTick)), n.velocity, n.channel)
            }.sortedWith(compareBy<ScoreNote> { it.startMs }.thenBy { it.pitch })
            ScorePart("part-${index + 1}", p.name, gmName(p.program, p.channel), identify(p.name, p.program, p.channel), mapped)
        }
        return MusicScore(parts, parts.flatMap { it.notes }.maxOfOrNull { it.endMs } ?: 0L,
            (60_000_000 / points.first().micros).toInt())
    }

    private fun completeNote(
        active: MutableMap<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>,
        out: MutableMap<Int, MutableList<RawNote>>, channel: Int, pitch: Int, tick: Long
    ) {
        val queue = active[channel to pitch] ?: return
        if (queue.isEmpty()) return
        val (start, velocity) = queue.removeFirst()
        if (tick > start) out.getOrPut(channel) { mutableListOf() }.add(RawNote(pitch, start, tick, velocity, channel))
    }

    private fun identify(name: String, program: Int, channel: Int): PartFamily {
        val lower = name.lowercase()
        return when {
            channel == 9 || listOf("drum", "perc", "удар").any(lower::contains) -> PartFamily.DRUMS
            listOf("vocal", "voice", "vox", "singer", "вокал").any(lower::contains) || program in 52..55 -> PartFamily.VOCAL
            listOf("bass", "бас").any(lower::contains) || program in 32..39 -> PartFamily.BASS
            listOf("guitar", "гитар").any(lower::contains) || program in 24..31 -> PartFamily.GUITAR
            listOf("piano", "keyboard", "keys", "organ", "фортепиано", "клавиш").any(lower::contains) || program in 0..23 -> PartFamily.KEYS
            else -> PartFamily.OTHER
        }
    }

    private fun gmName(program: Int, channel: Int): String = when {
        channel == 9 -> "Ударные MIDI (канал 10)"
        program in 0..7 -> "Фортепиано"
        program in 8..15 -> "Клавишные / перкуссия"
        program in 16..23 -> "Орган"
        program in 24..31 -> "Гитара"
        program in 32..39 -> "Бас"
        program in 40..47 -> "Струнные"
        program in 48..55 -> "Струнные / голос"
        program in 56..63 -> "Духовые"
        program in 64..79 -> "Духовые / флейта"
        program in 80..103 -> "Синтезатор"
        else -> "Инструмент GM ${program + 1}"
    }

    private fun readFourcc(stream: DataInputStream): String {
        val tag = ByteArray(4)
        stream.readFully(tag)
        return tag.toString(Charsets.US_ASCII)
    }

    private fun skipExactly(stream: DataInputStream, length: Int) {
        val data = ByteArray(length)
        stream.readFully(data)
    }
}

private class MidiReader(private val bytes: ByteArray) {
    private var pos = 0
    fun left(): Int = bytes.size - pos
    fun hasRemaining(): Boolean = pos < bytes.size
    fun byte(): Int = if (hasRemaining()) bytes[pos++].toInt() and 255 else throw EOFException("Обрезанный MIDI-файл")
    fun rewindOne() { if (pos <= 0) throw IOException("MIDI position underflow"); pos-- }
    fun bytes(length: Int): ByteArray {
        if (length < 0 || length > left()) throw EOFException("Обрезанный MIDI-блок")
        val data = bytes.copyOfRange(pos, pos + length)
        pos += length
        return data
    }
    fun skip(length: Int) { if (length < 0 || length > left()) throw EOFException("Обрезанный MIDI-блок"); pos += length }
    fun varLen(): Long {
        var value = 0L
        repeat(4) { i ->
            val b = byte()
            value = (value shl 7) or (b and 127).toLong()
            if (b and 128 == 0) return value
            if (i == 3) throw IOException("Слишком длинное MIDI-число")
        }
        return value
    }
}

/** Label only: same pitch can have several enharmonic spellings. */
fun noteName(pitch: Int): String {
    val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    return names[pitch.mod(12)] + (pitch / 12 - 1)
}
