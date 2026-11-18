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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.freelocalplayer.lyrics.LrcTimingAdjuster
import com.example.freelocalplayer.lyrics.EnhancedLrcWordAdjuster
import com.example.freelocalplayer.lyrics.LyricLineSync
import com.example.freelocalplayer.lyrics.TimedLine
import com.example.freelocalplayer.music.ChordCue
import com.example.freelocalplayer.music.ChordOnLyricLine
import com.example.freelocalplayer.music.InlineLyricToken
import com.example.freelocalplayer.music.LyricChordTimeline

/** Lyrics and real chord estimates share one scrollable view. No generated/fake lyrics. */
@OptIn(ExperimentalLayoutApi::class)
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
    onSaveTimedLyrics: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val timed = remember(lyrics) { LrcParser.parse(lyrics) }
    // The parent wraps this composable in key(entry.id). Keep in-flight edits alive
    // when MainActivity optimistically updates entry.lyrics before the disk receipt.
    var isTimingTxt by remember { mutableStateOf(false) }
    var autoFollow by remember { mutableStateOf(true) }
    var editTimingMode by remember { mutableStateOf(false) }
    var selectedTimingIndex by remember { mutableStateOf<Int?>(null) }
    var selectedWordLineIndex by remember { mutableStateOf<Int?>(null) }
    var lineReceipt by remember { mutableStateOf<TimingSaveReceiptState?>(null) }
    var wordReceipt by remember { mutableStateOf<TimingSaveReceiptState?>(null) }
    var txtReceipt by remember { mutableStateOf<TimingSaveReceiptState?>(null) }
    val editableCues = remember(lyrics) { LrcTimingAdjuster.cues(lyrics) }
    var lineMarks by remember { mutableStateOf<List<Long>>(emptyList()) }
    val plainLines = remember(lyrics) { LyricLineSync.lines(lyrics) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // One action row only when there is text. The empty state has its own import button.
        if (lyrics.isNotBlank()) {
            // FlowRow wraps on narrow screens instead of hiding edit actions.
            FlowRow(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (timed.isNotEmpty()) {
                    FilterChip(
                        selected = autoFollow,
                        onClick = { autoFollow = !autoFollow },
                        label = { Text(if (autoFollow) "Авто: вкл" else "Авто: выкл") }
                    )
                    FilterChip(
                        selected = editTimingMode,
                        onClick = {
                            editTimingMode = !editTimingMode
                            if (editTimingMode) autoFollow = false
                            selectedTimingIndex = null
                        },
                        label = { Text("Метки") }
                    )
                }
                TextButton(onClick = onImportLyrics) { Text("Заменить") }
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
            // During manual reading users can disable follow; enabling it again jumps
            // to the current timestamp without altering the LRC or playback state.
            LaunchedEffect(activeIndex, autoFollow, timed.size) {
                val target = lyricScrollTarget(activeIndex, timed.size, autoFollow)
                if (target != null) listState.animateScrollToItem(target)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(bound) { index, item ->
                    val active = activeIndex == index
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (editTimingMode && editableCues.size == timed.size) {
                                lineReceipt = TimingSaveReceiptState(lyrics)
                                selectedTimingIndex = index
                            } else {
                                onSeek(item.lyric.timeMs)
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (editTimingMode) {
                                Text(
                                    "${LyricLineSync.formatTime(item.lyric.timeMs)} · Нажми для настройки времени",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                if (item.lyric.words.isNotEmpty()) {
                                    val canEditWords = EnhancedLrcWordAdjuster.words(lyrics, index).isNotEmpty()
                                    TextButton(
                                        onClick = {
                                            selectedTimingIndex = null
                                            wordReceipt = TimingSaveReceiptState(lyrics)
                                            selectedWordLineIndex = index
                                        },
                                        enabled = canEditWords
                                    ) { Text("Настроить слова") }
                                    if (!canEditWords) {
                                        Text("Общие метки слов: доступно ручное редактирование LRC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (item.lyric.words.isNotEmpty()) {
                                // Enhanced LRC contains actual word timestamps. The
                                // chord labels follow those timestamps, not guessed word positions.
                                EnhancedLine(item.lyric, cues, positionMs, offsetMs, active)
                            } else if (LyricChordTimeline.containsInlineChords(item.lyric.text)) {
                                InlineChordLine(LyricChordTimeline.parseInline(item.lyric.text), active)
                            } else {
                                ChordsForLine(item.chords, positionMs,
                                    timed.getOrNull(index + 1)?.timeMs ?: durationMs, active)
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
            Button(onClick = {
                txtReceipt = TimingSaveReceiptState(lyrics)
                lineMarks = emptyList()
                isTimingTxt = true
            }, modifier = Modifier.fillMaxWidth()) {
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
    // Every edit previews a new LRC string without overwriting the original.
    // Saving is only enabled when all time/word-order checks pass.
    val selectedIndex = selectedTimingIndex
    val lineBase = lineReceipt?.sourceLyrics ?: lyrics
    val sourceCues = remember(lineBase) { LrcTimingAdjuster.cues(lineBase) }
    if (selectedIndex != null && selectedIndex in sourceCues.indices) {
        val cue = sourceCues[selectedIndex]
        val lineSaving = lineReceipt?.saving == true
        var candidateMs by remember(lineBase, selectedIndex) { mutableLongStateOf(cue.timeMs) }
        val changedLyrics = remember(lineBase, selectedIndex, candidateMs, durationMs) {
            LrcTimingAdjuster.retime(lineBase, selectedIndex, candidateMs, durationMs)
        }
        val validOffsets = remember(lineBase, selectedIndex, candidateMs, durationMs) {
            listOf(-500L, -100L, 100L, 500L).associateWith { delta ->
                LrcTimingAdjuster.retime(lineBase, selectedIndex, candidateMs + delta, durationMs) != null
            }
        }
        AlertDialog(
            onDismissRequest = { if (!lineSaving) selectedTimingIndex = null },
            title = { Text("Время строки LRC") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cue.text, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Было: ${LyricLineSync.formatTime(cue.timeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Новое: ${LyricLineSync.formatTime(candidateMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                            val proposalMs = candidateMs + delta
                            OutlinedButton(
                                enabled = !lineSaving && validOffsets[delta] == true,
                                onClick = { candidateMs = proposalMs }
                            ) {
                                Text(when (delta) {
                                    -500L -> "−0,5 с"
                                    -100L -> "−0,1 с"
                                    100L -> "+0,1 с"
                                    else -> "+0,5 с"
                                })
                            }
                        }
                    }
                    val atPlayback = positionMs.coerceAtLeast(0L)
                    TextButton(
                        onClick = { candidateMs = atPlayback },
                        enabled = !lineSaving && LrcTimingAdjuster.retime(
                            lineBase, selectedIndex, atPlayback, durationMs
                        ) != null
                    ) { Text("По текущей позиции песни") }
                    if (cue.hasWordTiming && cue.hasMultipleLineTags) {
                        Text("В этой строке несколько временных меток и общие метки слов. " +
                            "Чтобы не испортить караоке, открой ручное редактирование LRC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    } else if (cue.hasWordTiming) {
                        Text("Метки слов сдвигаются на тот же интервал — пословная синхронизация сохраняется.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lineSaving) Text("Сохраняю время строки…")
                    if (lineReceipt?.failed == true) Text(
                        "Не удалось сохранить. Метка сохранена в редакторе — попробуй ещё раз.",
                        color = MaterialTheme.colorScheme.error)
                    if (changedLyrics == null && !(cue.hasWordTiming && cue.hasMultipleLineTags)) {
                        Text("Время должно оставаться между соседними строками, " +
                            "не выходить за пределы песни и не делать метки слов отрицательными.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!lineSaving && changedLyrics != null && changedLyrics != lineBase) {
                            lineReceipt = (lineReceipt ?: TimingSaveReceiptState(lineBase)).start()
                            onSaveTimedLyrics(changedLyrics) { confirmed ->
                                lineReceipt = lineReceipt?.complete(confirmed)
                                if (confirmed) {
                                    selectedTimingIndex = null
                                    lineReceipt = null
                                    editTimingMode = false
                                }
                            }
                        }
                    },
                    enabled = !lineSaving && changedLyrics != null && changedLyrics != lineBase
                ) { Text(if (lineSaving) "Сохраняю…" else "Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { selectedTimingIndex = null }, enabled = !lineSaving) {
                    Text("Отмена")
                }
            }
        )
    }
    // Word timing is edited independently; existing line timing remains unchanged.
    val editWordLine = selectedWordLineIndex
    val wordBase = wordReceipt?.sourceLyrics ?: lyrics
    if (editWordLine != null) {
        val wordSaving = wordReceipt?.saving == true
        val lineWords = remember(wordBase, editWordLine) {
            EnhancedLrcWordAdjuster.words(wordBase, editWordLine)
        }
        if (lineWords.isNotEmpty()) {
            var selectedWordIndex by remember(wordBase, editWordLine) { mutableIntStateOf(0) }
            val selectedWord = lineWords.getOrNull(selectedWordIndex)
            if (selectedWord != null) {
                var wordCandidateMs by remember(wordBase, editWordLine, selectedWordIndex) {
                    mutableLongStateOf(selectedWord.timeMs)
                }
                val updatedLyrics = remember(wordBase, editWordLine, selectedWordIndex,
                    wordCandidateMs, durationMs) {
                    EnhancedLrcWordAdjuster.retimeWord(wordBase, editWordLine, selectedWordIndex,
                        wordCandidateMs, durationMs)
                }
                AlertDialog(
                    onDismissRequest = { if (!wordSaving) selectedWordLineIndex = null },
                    title = { Text("Время слова Enhanced LRC") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Выбери слово:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                lineWords.forEachIndexed { index, word ->
                                    FilterChip(
                                        selected = selectedWordIndex == index,
                                        onClick = { selectedWordIndex = index },
                                        enabled = !wordSaving,
                                        label = { Text(word.text.take(26)) }
                                    )
                                }
                            }
                            Text("Было: ${LyricLineSync.formatTime(selectedWord.timeMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Новое: ${LyricLineSync.formatTime(wordCandidateMs)}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                                    val proposal = wordCandidateMs + delta
                                    OutlinedButton(
                                        enabled = !wordSaving && EnhancedLrcWordAdjuster.retimeWord(
                                            wordBase, editWordLine, selectedWordIndex, proposal,
                                            durationMs) != null,
                                        onClick = { wordCandidateMs = proposal }
                                    ) {
                                        Text(when (delta) {
                                            -500L -> "−0,5 с"
                                            -100L -> "−0,1 с"
                                            100L -> "+0,1 с"
                                            else -> "+0,5 с"
                                        })
                                    }
                                }
                            }
                            val playbackTarget = positionMs.coerceAtLeast(0L)
                            TextButton(
                                onClick = { wordCandidateMs = playbackTarget },
                                enabled = !wordSaving && EnhancedLrcWordAdjuster.retimeWord(
                                    wordBase, editWordLine, selectedWordIndex, playbackTarget,
                                    durationMs) != null
                            ) { Text("По текущей позиции песни") }
                            Text("Другие слова и начало строки не изменяются.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (wordSaving) Text("Сохраняю время слова…")
                            if (wordReceipt?.failed == true) Text(
                                "Не удалось сохранить. Метка сохранена в редакторе — попробуй ещё раз.",
                                color = MaterialTheme.colorScheme.error)
                            if (updatedLyrics == null) {
                                Text("Время должно оставаться между соседними метками, " +
                                    "внутри строки и длительности песни.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (!wordSaving && updatedLyrics != null && updatedLyrics != wordBase) {
                                    wordReceipt = (wordReceipt ?: TimingSaveReceiptState(wordBase)).start()
                                    onSaveTimedLyrics(updatedLyrics) { confirmed ->
                                        wordReceipt = wordReceipt?.complete(confirmed)
                                        if (confirmed) {
                                            selectedWordLineIndex = null
                                            wordReceipt = null
                                            editTimingMode = false
                                        }
                                    }
                                }
                            },
                            enabled = !wordSaving && updatedLyrics != null && updatedLyrics != wordBase
                        ) { Text(if (wordSaving) "Сохраняю…" else "Сохранить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedWordLineIndex = null }, enabled = !wordSaving) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
    // Do not hide the TXT timing dialog merely because the optimistic in-memory
    // entry.lyrics has already changed to LRC while commit is still pending.
    val txtBase = txtReceipt?.sourceLyrics ?: lyrics
    val txtLines = remember(txtBase) { LyricLineSync.lines(txtBase) }
    if (isTimingTxt && txtLines.isNotEmpty()) {
        val txtSaving = txtReceipt?.saving == true
        Dialog(
            onDismissRequest = { if (!txtSaving) isTimingTxt = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FullScreenLyricTiming(
                    lines = txtLines,
                    marks = lineMarks,
                    saving = txtSaving,
                    saveFailed = txtReceipt?.failed == true,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    onTogglePlayback = onTogglePlayback,
                    onSeek = onSeek,
                    onMark = { time ->
                        if (!txtSaving) lineMarks = LyricLineSync.mark(lineMarks, time, txtLines.size)
                    },
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
                        if (!txtSaving && LyricLineSync.ready(txtLines, lineMarks)) {
                            txtReceipt = (txtReceipt ?: TimingSaveReceiptState(txtBase)).start()
                            onSaveTimedLyrics(LyricLineSync.toLrc(txtLines, lineMarks)) { confirmed ->
                                txtReceipt = txtReceipt?.complete(confirmed)
                                if (confirmed) {
                                    isTimingTxt = false
                                    txtReceipt = null
                                    lineMarks = emptyList()
                                }
                            }
                        }
                    },
                    onClose = { if (!txtSaving) isTimingTxt = false }
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
    saving: Boolean,
    saveFailed: Boolean,
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
            IconButton(onClick = onClose, enabled = !saving) {
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
                enabled = durationMs > 0L && !saving,
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
                OutlinedButton(onClick = { onAdjust(-100L) }, enabled = !saving) { Text("−0,1 с") }
                OutlinedButton(onClick = { onAdjust(100L) }, enabled = !saving) { Text("+0,1 с") }
            }
        }
        Button(
            onClick = { onMark(markedPositionMs) },
            enabled = canMark && !saving,
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
            OutlinedButton(onClick = onUndo, enabled = !saving && marks.isNotEmpty()) {
                Text("Отменить последнюю")
            }
            TextButton(onClick = onRestart, enabled = !saving && marks.isNotEmpty()) {
                Text("Сначала")
            }
        }
        if (saveFailed) Text(
            "Не удалось сохранить LRC. Все отметки сохранены в редакторе — попробуй ещё раз.",
            color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onSave,
            enabled = !saving && LyricLineSync.ready(lines, marks),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Сохраняю…" else "СОХРАНИТЬ СИНХРОННЫЙ LRC") }
    }
}

@Composable
private fun ChordsForLine(chords: List<ChordOnLyricLine>, positionMs: Long, lineEndMs: Long,
                          lineActive: Boolean) {
    if (chords.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        chords.take(8).forEachIndexed { index, item ->
            val until = chords.getOrNull(index + 1)?.audioTimeMs ?: lineEndMs
            val active = positionMs >= item.audioTimeMs && positionMs < until
            Text(
                if (item.name == "—") "?" else item.name,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                color = if (lineActive) MaterialTheme.colorScheme.onSecondaryContainer
                        else if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
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
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.secondary,
                    lineHeight = 17.sp
                )
                Text(
                    syllable, fontSize = 18.sp, lineHeight = 25.sp,
                    color = when {
                        active && time <= positionMs -> MaterialTheme.colorScheme.onSecondaryContainer
                        active -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
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
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.secondary
                )
                Text(
                    token.text, fontSize = 18.sp, lineHeight = 25.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
