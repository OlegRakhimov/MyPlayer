package com.example.freelocalplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import com.example.freelocalplayer.lyrics.LrcParser
import com.example.freelocalplayer.lyrics.LyricLineSync
import com.example.freelocalplayer.lyrics.TimedLine
import com.example.freelocalplayer.music.ChordCue
import com.example.freelocalplayer.music.ChordOnLyricLine
import com.example.freelocalplayer.music.InlineLyricToken
import com.example.freelocalplayer.music.LyricChordTimeline

/** Lyrics and real chord estimates share one scrollable view. No generated/fake lyrics. */
@Composable
internal fun LyricsChordsScreen(
    lyrics: String,
    cues: List<ChordCue>,
    positionMs: Long,
    durationMs: Long,
    offsetMs: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onEditCurrentChord: (ChordCue) -> Unit,
    onImportLyrics: () -> Unit,
    onEditLyrics: () -> Unit,
    onSaveTimedLyrics: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timed = remember(lyrics) { LrcParser.parse(lyrics) }
    var isTimingTxt by remember(lyrics) { mutableStateOf(false) }
    var lineMarks by remember(lyrics) { mutableStateOf<List<Long>>(emptyList()) }
    val plainLines = remember(lyrics) { LyricLineSync.lines(lyrics) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // One action row only when there is text. The empty state has its own import button.
        if (lyrics.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onImportLyrics) { Text("Заменить TXT/LRC") }
                TextButton(onClick = onEditLyrics) { Text("Изменить") }
            }
        }
        if (lyrics.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Текст к этой песне ещё не добавлен.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onImportLyrics) { Text("Добавить текст TXT или LRC") }
                }
            }
            return@Column
        }
        if (timed.isNotEmpty()) {
            val bound = remember(timed, cues, offsetMs, durationMs) {
                LyricChordTimeline.bind(timed, cues, offsetMs, durationMs)
            }
            val activeIndex = LrcParser.currentLine(timed, positionMs)
            val listState = rememberLazyListState()
            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(bound) { index, item ->
                    val active = activeIndex == index
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSeek(item.lyric.timeMs) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (item.lyric.words.isNotEmpty()) {
                                // Enhanced LRC contains actual word timestamps. The
                                // chord labels follow those timestamps, not guessed word positions.
                                EnhancedLine(item.lyric, cues, positionMs, offsetMs, active)
                            } else if (LyricChordTimeline.containsInlineChords(item.lyric.text)) {
                                InlineChordLine(LyricChordTimeline.parseInline(item.lyric.text), active)
                            } else {
                                ChordsForLine(item.chords, positionMs,
                                    timed.getOrNull(index + 1)?.timeMs ?: durationMs)
                                Text(
                                    item.lyric.text,
                                    style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    lineHeight = 27.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Open dedicated full-screen line timing so the player/chord controls
            // cannot crowd out the currently sung line and the MARK button.
            Button(onClick = { isTimingTxt = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Синхронизировать строки с музыкой")
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(lyrics.lines()) { _, raw ->
                    if (raw.isBlank()) {
                        Spacer(Modifier.height(7.dp))
                    } else if (LyricChordTimeline.containsInlineChords(raw)) {
                        Card(Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(10.dp)) {
                                InlineChordLine(LyricChordTimeline.parseInline(raw), false)
                            }
                        }
                    } else {
                        Text(raw, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface, lineHeight = 27.sp)
                    }
                }
            }
        }
    }
    if (isTimingTxt && timed.isEmpty() && plainLines.isNotEmpty()) {
        Dialog(
            onDismissRequest = { isTimingTxt = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FullScreenLyricTiming(
                    lines = plainLines,
                    marks = lineMarks,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    onTogglePlayback = onTogglePlayback,
                    onSeek = onSeek,
                    onMark = { time -> lineMarks = LyricLineSync.mark(lineMarks, time, plainLines.size) },
                    onUndo = {
                        val last = lineMarks.lastOrNull()
                        lineMarks = LyricLineSync.undo(lineMarks)
                        if (last != null) onSeek((last - 1500L).coerceAtLeast(0L))
                    },
                    onAdjust = { delta ->
                        lineMarks = LyricLineSync.adjustLast(lineMarks, delta, durationMs)
                    },
                    onRestart = { lineMarks = emptyList() },
                    onSave = {
                        if (LyricLineSync.ready(plainLines, lineMarks)) {
                            onSaveTimedLyrics(LyricLineSync.toLrc(plainLines, lineMarks))
                            isTimingTxt = false
                        }
                    },
                    onClose = { isTimingTxt = false }
                )
            }
        }
    }
}

/** Clean, full-screen timing workspace; parent player controls stay behind the dialog. */
@Composable
private fun FullScreenLyricTiming(
    lines: List<String>,
    marks: List<Long>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onMark: (Long) -> Unit,
    onUndo: () -> Unit,
    onAdjust: (Long) -> Unit,
    onRestart: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val next = marks.size
    // A dedicated, always-visible seek control is essential when a user is
    // synchronizing spoken lyrics with playback. Keep a pending seek position
    // until the player's asynchronous position update catches up.
    val safeDurationMs = durationMs.coerceAtLeast(1L)
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(positionMs, durationMs, seeking) {
        val pending = pendingSeekMs
        if (pending != null && abs(positionMs - pending) <= 350L) pendingSeekMs = null
        if (!seeking && pendingSeekMs == null) {
            seekFraction = (positionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
    fun jumpTo(targetMs: Long) {
        val clamped = if (durationMs > 0L) targetMs.coerceIn(0L, durationMs)
                      else targetMs.coerceAtLeast(0L)
        pendingSeekMs = clamped
        seekFraction = (clamped.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
        seeking = false
        onSeek(clamped)
    }
    val markedPositionMs = when {
        seeking -> (seekFraction * safeDurationMs).toLong()
        pendingSeekMs != null -> pendingSeekMs!!
        else -> positionMs
    }
    val canMark = !seeking && LyricLineSync.canMark(marks, markedPositionMs, lines.size)
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Синхронизация текста", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Отмечай начало каждой пропеваемой строки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть синхронизацию")
            }
        }
        LinearProgressIndicator(
            progress = { next.toFloat() / lines.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${next.coerceAtMost(lines.size)} / ${lines.size} строк",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(LyricLineSync.formatTime(markedPositionMs).removePrefix("[").removeSuffix("]"),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        // Scrubbing follows the whole track, not just +/-3 seconds. The
        // target is committed on release, like standard audio/video players.
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { jumpTo(0L) }) { Text("0:00 ↤") }
            Slider(
                value = seekFraction,
                onValueChange = { seekFraction = it; seeking = true },
                onValueChangeFinished = {
                    jumpTo((seekFraction * safeDurationMs).toLong())
                },
                enabled = durationMs > 0L,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(LyricLineSync.formatTime(durationMs.coerceAtLeast(0L))
                .removePrefix("[").removeSuffix("]"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (next > 0) {
            Text("Предыдущая: ${lines[next - 1]}", maxLines = 2,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Отмечена: ${LyricLineSync.formatTime(marks.last())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (next < lines.size) "СЕЙЧАС ОТМЕЧАЕМ" else "ВСЕ СТРОКИ ОТМЕЧЕНЫ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                Text(
                    lines.getOrNull(next) ?: "Проверь время последней строки и сохрани LRC",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val upcoming = lines.getOrNull(next + 1)
                if (upcoming != null) {
                    Spacer(Modifier.height(24.dp))
                    Text("Дальше:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text(upcoming, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        lineHeight = 25.sp)
                }
            }
        }
        if (next > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Сдвиг метки:", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = { onAdjust(-100L) }) { Text("−0,1 с") }
                OutlinedButton(onClick = { onAdjust(100L) }) { Text("+0,1 с") }
            }
        }
        Button(
            onClick = { onMark(markedPositionMs) },
            enabled = canMark,
            modifier = Modifier.fillMaxWidth().height(66.dp)
        ) {
            Text(if (next == lines.size) "Все строки отмечены" else "ОТМЕТИТЬ НАЧАЛО СТРОКИ",
                style = MaterialTheme.typography.titleMedium)
        }
        if (!canMark && next < lines.size) {
            Text("Позиция раньше предыдущей отметки. Перемотай вперёд или отмени отметку.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { jumpTo((markedPositionMs - 3000L).coerceAtLeast(0L)) }) {
                Text("−3 с")
            }
            Button(onClick = onTogglePlayback) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (isPlaying) "Пауза" else "Играть")
            }
            OutlinedButton(onClick = {
                jumpTo(markedPositionMs + 3000L)
            }) { Text("+3 с") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onUndo, enabled = marks.isNotEmpty()) {
                Text("Отменить последнюю")
            }
            TextButton(onClick = onRestart, enabled = marks.isNotEmpty()) {
                Text("Сначала")
            }
        }
        Button(
            onClick = onSave,
            enabled = LyricLineSync.ready(lines, marks),
            modifier = Modifier.fillMaxWidth()
        ) { Text("СОХРАНИТЬ СИНХРОННЫЙ LRC") }
    }
}

@Composable
private fun ChordsForLine(chords: List<ChordOnLyricLine>, positionMs: Long, lineEndMs: Long) {
    if (chords.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        chords.take(8).forEachIndexed { index, item ->
            val until = chords.getOrNull(index + 1)?.audioTimeMs ?: lineEndMs
            val active = positionMs >= item.audioTimeMs && positionMs < until
            Text(
                if (item.name == "—") "?" else item.name,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    Spacer(Modifier.height(3.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedLine(
    line: TimedLine,
    cues: List<ChordCue>,
    positionMs: Long,
    offsetMs: Long,
    active: Boolean
) {
    var previous: String? = null
    val aligned = line.words.map { word ->
        val chord = LyricChordTimeline.chordAt(cues, word.timeMs, offsetMs)
        val mark = chord?.takeIf { it != "—" && it != previous }
        previous = chord
        Triple(word.text, mark, word.timeMs)
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        aligned.forEach { (syllable, chord, time) ->
            Column {
                Text(
                    chord ?: " ", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 17.sp
                )
                Text(
                    syllable, fontSize = 18.sp, lineHeight = 25.sp,
                    color = if (active && time <= positionMs) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (active && time <= positionMs) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineChordLine(tokens: List<InlineLyricToken>, active: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tokens.forEach { token ->
            Column {
                Text(
                    token.chord ?: " ", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    token.text, fontSize = 18.sp, lineHeight = 25.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
