package com.example.freelocalplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.music.ChordCue
import com.example.freelocalplayer.music.ChordCorrection
import com.example.freelocalplayer.music.ChordCorrections
import com.example.freelocalplayer.music.ChordDisplayFilter
import com.example.freelocalplayer.music.ABRepeat
import com.example.freelocalplayer.music.ChordTimeline
import com.example.freelocalplayer.music.MusicDataRepository
import com.example.freelocalplayer.music.MusicScore
import com.example.freelocalplayer.music.PartFamily
import com.example.freelocalplayer.music.ScoreNote
import com.example.freelocalplayer.music.ScorePart
import com.example.freelocalplayer.music.noteName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private data class MusicData(
    val score: MusicScore? = null,
    val chordText: String = "",
    val manualCues: List<ChordCue> = emptyList(),
    val midiCues: List<ChordCue> = emptyList(),
    val autoCues: List<ChordCue> = emptyList(),
    val corrections: List<ChordCorrection> = emptyList(),
    val offsetMs: Long = 0,
    val error: String? = null
)

/** Musical notation is generated ONLY from an imported MIDI score, never invented from audio. */
@Composable
internal fun MusicModeScreen(
    entry: MediaEntry,
    controller: MediaController?,
    playback: PlaybackInfo,
    repository: MusicDataRepository,
    revision: Int,
    analyzing: Boolean,
    analysisLocked: Boolean,
    analysisPercent: Int,
    onAnalyzeAudio: (MediaEntry) -> Unit,
    onCancelAnalysis: () -> Unit,
    onDeleteAudioAnalysis: (String) -> Unit,
    onImportLyrics: (String) -> Unit,
    onSaveLyrics: (String, String) -> Unit,
    onOpenLyricsEditor: () -> Unit,
    onOpenCover: () -> Unit,
    onDismiss: () -> Unit,
    onImportMidi: (String) -> Unit,
    onImportChords: (String) -> Unit,
    onSaveChords: (String, String) -> Unit,
    onSaveChordCorrection: (String, ChordCorrection) -> Unit,
    onRemoveChordCorrection: (String, String, Long) -> Unit,
    onOffsetChange: (String, Long) -> Unit,
    onDeleteMidi: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onOpenQueue: () -> Unit
) {
    val state by produceState(initialValue = MusicData(), key1 = entry.id, key2 = revision) {
        value = withContext(Dispatchers.IO) {
            try {
                val score = repository.loadScore(entry.id)
                val text = repository.loadChords(entry.id)
                val custom = ChordTimeline.parse(text)
                val estimated = repository.loadAudioEstimate(entry.id)
                MusicData(
                    score = score,
                    chordText = text,
                    manualCues = custom,
                    autoCues = estimated,
                    corrections = repository.loadChordCorrections(entry.id),
                    midiCues = score?.let(ChordTimeline::approximateFromMidi).orEmpty(),
                    offsetMs = repository.loadOffset(entry.id)
                )
            } catch (exception: Exception) {
                MusicData(error = exception.message ?: "Не удалось открыть партитуру")
            }
        }
    }
    var family by remember(entry.id) { mutableStateOf(PartFamily.VOCAL) }
    var partId by remember(entry.id) { mutableStateOf("") }
    var mode by remember(entry.id) { mutableStateOf("Текст + аккорды") }
    var editChords by remember(entry.id) { mutableStateOf(false) }
    var showTools by remember(entry.id) { mutableStateOf(false) }
    var controlsExpanded by remember(entry.id) { mutableStateOf(false) }
    var chordSource by remember(entry.id) { mutableStateOf("Авто") }
    // Default is stable. Raw mode is available for exceptionally fast passages.
    var stableAutoChords by remember(entry.id) { mutableStateOf(true) }
    var chordDraft by remember(entry.id, revision, state.chordText) { mutableStateOf(state.chordText) }
    var positionDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var precisePosition by remember(entry.id) { mutableLongStateOf(playback.position) }
    var loopA by remember(entry.id) { mutableStateOf<Long?>(null) }
    var loopB by remember(entry.id) { mutableStateOf<Long?>(null) }
    var lastLoopSeekAt by remember(entry.id) { mutableLongStateOf(0L) }
    var editCue by remember(entry.id) { mutableStateOf<ChordCue?>(null) }
    var chordNameDraft by remember(entry.id) { mutableStateOf("") }
    val score = state.score
    // Prefer audio analysis when available, without deleting manual or MIDI data.
    val actualChordSource = when {
        chordSource == "Авто" && state.autoCues.isNotEmpty() -> "Авто"
        chordSource == "Ручные" && state.manualCues.isNotEmpty() -> "Ручные"
        chordSource == "MIDI" && state.midiCues.isNotEmpty() -> "MIDI"
        state.autoCues.isNotEmpty() -> "Авто"
        state.manualCues.isNotEmpty() -> "Ручные"
        else -> "MIDI"
    }
    val rawCues = when (actualChordSource) {
        "Ручные" -> state.manualCues
        "MIDI" -> state.midiCues
        else -> state.autoCues
    }
    val sourceId = ChordCorrections.sourceId(actualChordSource)
    val displayBaseCues = remember(rawCues, actualChordSource, stableAutoChords) {
        if (actualChordSource == "Авто") ChordDisplayFilter.apply(rawCues, stableAutoChords)
        else rawCues
    }
    // Corrections are deliberately applied AFTER automatic stabilization:
    // user-entered chords must never be silently overwritten by the filter.
    val cues = remember(displayBaseCues, state.corrections, sourceId) {
        ChordCorrections.apply(displayBaseCues, state.corrections, sourceId)
    }
    val musicPosition = if (controller?.currentMediaItem?.mediaId == entry.id) precisePosition else playback.position
    LaunchedEffect(entry.id, controller, loopA, loopB) {
        while (isActive) {
            precisePosition = controller?.currentPosition?.coerceAtLeast(0L) ?: playback.position
            val a = loopA
            val b = loopB
            if (controller != null && a != null && b != null && ABRepeat.shouldRepeat(a, b, precisePosition, controller.isPlaying) &&
                android.os.SystemClock.elapsedRealtime() - lastLoopSeekAt > 250L
            ) {
                lastLoopSeekAt = android.os.SystemClock.elapsedRealtime()
                controller.seekTo(a)
                precisePosition = a
            }
            delay(if (controller?.isPlaying == true) 50L else 150L)
        }
    }
    val parts = score?.parts?.filter { it.family == family }.orEmpty()
    val part = parts.firstOrNull { it.id == partId } ?: parts.firstOrNull()
    val alignedPosition = musicPosition - state.offsetMs
    val active = part?.notes?.filter { alignedPosition in it.startMs until it.endMs }.orEmpty()
    val activeChord = ChordTimeline.current(cues, alignedPosition)

    LaunchedEffect(musicPosition, playback.duration, positionDragging) {
        if (!positionDragging && playback.duration > 0) {
            sliderValue = (musicPosition.toFloat() / playback.duration.toFloat()).coerceIn(0f, 1f)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // One title only. Playback controls stay collapsed until the user taps them.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(entry.title, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showTools = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Музыкальные инструменты")
                    }
                    IconButton(onClick = onOpenCover, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Image, contentDescription = "Обложка")
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Текст + аккорды", "Аккорды", "Ноты").forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = { Text(if (item == "Текст + аккорды") "Текст" else item) }
                        )
                    }
                }
                if (mode != "Текст + аккорды" && mode != "Аккорды" && score != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PartFamily.entries.forEach { candidate ->
                            val count = score.parts.count { it.family == candidate }
                            FilterChip(
                                selected = family == candidate,
                                onClick = { family = candidate; partId = "" },
                                label = { Text("${candidate.title} ($count)") }
                            )
                        }
                    }
                    if (parts.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            parts.forEach { candidate ->
                                FilterChip(
                                    selected = part?.id == candidate.id,
                                    onClick = { partId = candidate.id },
                                    label = { Text(candidate.name) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        mode == "Текст + аккорды" -> {
                            key(entry.id) {
                            LyricsChordsScreen(
                                lyrics = entry.lyrics,
                                cues = cues,
                                positionMs = musicPosition,
                                durationMs = playback.duration,
                                offsetMs = state.offsetMs,
                                onSeek = { controller?.seekTo(it) },
                                isPlaying = controller?.isPlaying == true,
                                onTogglePlayback = {
                                    if (controller?.isPlaying == true) controller.pause() else controller?.play()
                                },
                                onEditCurrentChord = { cue -> editCue = cue; chordNameDraft = cue.name },
                                onImportLyrics = { onImportLyrics(entry.id) },
                                onEditLyrics = onOpenLyricsEditor,
                                onSaveTimedLyrics = { synchronized -> onSaveLyrics(entry.id, synchronized) },
                                modifier = Modifier.fillMaxSize()
                            )
                            }
                        }
                        mode == "Аккорды" -> {
                            Column(Modifier.fillMaxSize()) {
                                val activeName = cues.getOrNull(activeChord)?.name
                                Text("Сейчас: ${activeName?.takeUnless { it == "—" } ?: "—"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp))
                                ChordsView(
                                    cues = cues,
                                    currentIndex = activeChord,
                                    offset = state.offsetMs,
                                    onSeek = { controller?.seekTo(it) },
                                    onEdit = { cue -> editCue = cue; chordNameDraft = cue.name }
                                )
                            }
                        }
                        score == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Для этой композиции не импортирован MIDI. Для текста с аккордами выбери первую вкладку.")
                        }
                        part == null && score != null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("В MIDI нет партии «${family.title}». Выбери другой инструмент.")
                            }
                        }
                        part != null && mode == "Табулатура" -> {
                            if (family == PartFamily.GUITAR || family == PartFamily.BASS) {
                                TablatureView(part, alignedPosition, family == PartFamily.BASS)
                            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Табулатура доступна для гитары и баса. Для остальных партий выбери ноты или Piano roll.")
                            }
                        }
                        part != null -> {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(part.name + " · " + part.instrument,
                                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                if (mode == "Piano roll" || family == PartFamily.DRUMS) {
                                    PianoRoll(part, alignedPosition, Modifier.fillMaxWidth().height(260.dp)) { scoreMs ->
                                        controller?.seekTo((scoreMs + state.offsetMs).coerceAtLeast(0L))
                                    }
                                } else {
                                    MusicStaff(part, alignedPosition, family == PartFamily.BASS,
                                        Modifier.fillMaxWidth().height(230.dp)) { scoreMs ->
                                        controller?.seekTo((scoreMs + state.offsetMs).coerceAtLeast(0L))
                                    }
                                }
                                Text(
                                    if (active.isEmpty()) "В этот момент в партии нет звучащих MIDI-нот"
                                    else "Звучат: " + active.take(12).joinToString(" · ") { noteName(it.pitch) },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val cue = cues.getOrNull(activeChord)
                                if (cue != null) Text("Аккорд: ${cue.name}", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Импортируй MIDI или добавь синхронизированные аккорды.")
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.weight(1f)
                                    .clickable { controlsExpanded = !controlsExpanded }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("${musicTime(musicPosition)} / ${musicTime(playback.duration)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Icon(if (controlsExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = if (controlsExpanded) "Свернуть управление" else "Раскрыть управление",
                                    modifier = Modifier.size(20.dp))
                            }
                            PlaybackModeSelector(playback, onShuffleChange, onRepeatChange)
                            IconButton(onClick = {
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            }, enabled = controller != null) {
                                Icon(if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Воспроизведение или пауза",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        if (controlsExpanded) {
                            Slider(value = sliderValue,
                                onValueChange = { sliderValue = it; positionDragging = true },
                                onValueChangeFinished = {
                                    if (playback.duration > 0L)
                                        controller?.seekTo((playback.duration * sliderValue).toLong())
                                    positionDragging = false
                                },
                                enabled = controller != null && playback.duration > 0L)
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { controller?.seekToPreviousMediaItem() },
                                    enabled = controller != null) {
                                    Icon(Icons.Default.SkipPrevious, "Предыдущая песня")
                                }
                                TextButton(onClick = {
                                    controller?.seekTo((musicPosition - 10_000L).coerceAtLeast(0L))
                                }) { Text("−10 с") }
                                TextButton(onClick = {
                                    controller?.seekTo(if (playback.duration > 0L)
                                        (musicPosition + 10_000L).coerceAtMost(playback.duration)
                                        else musicPosition + 10_000L)
                                }) { Text("+10 с") }
                                IconButton(onClick = { controller?.seekToNextMediaItem() },
                                    enabled = controller != null) {
                                    Icon(Icons.Default.SkipNext, "Следующая песня")
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onOpenQueue) { Text("Очередь") }
                                TextButton(onClick = { showTools = true }) { Text("Инструменты") }
                                TextButton(onClick = { controlsExpanded = false }) { Text("Свернуть") }
                            }
                        }
                    }
                }
            }
        }
    }
    // Advanced commands do not consume space on the lyrics/chords playback screen.
    if (showTools) {
        Dialog(
            onDismissRequest = { showTools = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().imePadding().safeDrawingPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Музыкальные инструменты", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { showTools = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Вернуться к тексту")
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Дополнительные виды нот", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Piano roll", "Табулатура").forEach { view ->
                                OutlinedButton(onClick = { mode = view; showTools = false }) { Text(view) }
                            }
                        }
                        Text("Скорость воспроизведения: ${"%.2f".format(java.util.Locale.US, playback.speed)}×",
                            style = MaterialTheme.typography.titleMedium)
                        Slider(value = playback.speed.coerceIn(0.5f, 2.0f),
                            onValueChange = { onSpeedChange((it * 20f).toInt() / 20f) },
                            valueRange = 0.5f..2.0f)
                        Text("Импорт и анализ", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onImportMidi(entry.id) }) { Text("+ MIDI") }
                            OutlinedButton(onClick = { onImportChords(entry.id) }) { Text("Аккорды TXT") }
                            OutlinedButton(onClick = { onImportLyrics(entry.id) }) { Text("Текст TXT/LRC") }
                        }
                        Button(onClick = { onAnalyzeAudio(entry) }, enabled = !analysisLocked,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (analyzing) "Идёт анализ…" else "Определить аккорды из аудио")
                        }
                        if (analyzing) {
                            Text("Обработано: $analysisPercent%")
                            TextButton(onClick = onCancelAnalysis) { Text("Отменить анализ") }
                        }
                        if (state.autoCues.isNotEmpty() && !analysisLocked) {
                            TextButton(onClick = { onDeleteAudioAnalysis(entry.id) }) { Text("Удалить автоанализ") }
                        }
                        state.error?.let {
                            Text("Ошибка чтения MIDI: $it", color = MaterialTheme.colorScheme.error)
                        }
                        Text("Источник аккордов", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.autoCues.isNotEmpty()) {
                                FilterChip(selected = actualChordSource == "Авто", onClick = { chordSource = "Авто" },
                                    label = { Text("Авто ≈") })
                            }
                            if (state.manualCues.isNotEmpty()) {
                                FilterChip(selected = actualChordSource == "Ручные", onClick = { chordSource = "Ручные" },
                                    label = { Text("Ручные") })
                            }
                            if (state.midiCues.isNotEmpty()) {
                                FilterChip(selected = actualChordSource == "MIDI", onClick = { chordSource = "MIDI" },
                                    label = { Text("MIDI") })
                            }
                        }
                        if (actualChordSource == "Авто" && state.autoCues.size > 1) {
                            Text("Смена аккордов", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = stableAutoChords, onClick = { stableAutoChords = true },
                                    label = { Text("Стабильно") })
                                FilterChip(selected = !stableAutoChords, onClick = { stableAutoChords = false },
                                    label = { Text("Быстро 0,5 с") })
                            }
                        }
                        if (state.autoCues.isNotEmpty()) {
                            Text("Анализ: ${state.autoCues.size} измерений. Результат приблизительный.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Ручные аккорды", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { editChords = !editChords }) {
                            Text(if (editChords) "Скрыть редактор аккордов" else "Редактировать файл аккордов")
                        }
                        if (editChords) {
                            Text("Повтор участка при редактировании аккордов",
                                style = MaterialTheme.typography.labelMedium)
                            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            loopA = musicPosition
                            if (loopB != null && loopB!! <= musicPosition + 250L) loopB = null
                        },
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) { Text("A") }
                    OutlinedButton(
                        onClick = { if (loopA != null && musicPosition > loopA!! + 250L) loopB = musicPosition },
                        enabled = loopA != null && musicPosition > loopA!! + 250L,
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) { Text("B") }
                    Text(
                        when {
                            loopA == null -> "Повтор A–B"
                            loopB == null -> "A: ${musicTime(loopA!!)}"
                            else -> "↻ ${musicTime(loopA!!)} – ${musicTime(loopB!!)}"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { loopA = null; loopB = null }, enabled = loopA != null) {
                        Text("Сброс")
                    }
                }
                            OutlinedTextField(
                                value = chordDraft,
                                onValueChange = { chordDraft = it },
                                label = { Text("Аккорды с метками времени") },
                                placeholder = { Text("[00:00.00] Am\n[00:04.50] F") },
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { onSaveChords(entry.id, chordDraft); editChords = false }) {
                                    Text("Сохранить")
                                }
                                TextButton(onClick = { chordDraft = state.chordText; editChords = false }) {
                                    Text("Отмена")
                                }
                            }
                        }
                        if (score != null) {
                            Text("MIDI: ${score.parts.size} партий · ${score.noteCount} нот · ≈ ${score.beatsPerMinute} BPM",
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onDeleteMidi(entry.id) }) { Text("Удалить импортированный MIDI") }
                        }
                        if (score != null || cues.isNotEmpty()) {
                            Text("Сдвиг синхронизации: ${state.offsetMs} мс",
                                style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(-1000L, -100L, 100L, 1000L).forEach { delta ->
                                    OutlinedButton(
                                        onClick = { onOffsetChange(entry.id, state.offsetMs + delta) },
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) { Text(if (delta > 0) "+$delta" else "$delta") }
                                }
                                TextButton(onClick = { onOffsetChange(entry.id, 0L) }) { Text("0") }
                            }
                        }
                        Text("Анализ не создаёт MIDI-ноты; инструментальные партии импортируются из MIDI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { showTools = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Вернуться к тексту и аккордам")
                    }
                }
            }
        }
    }
    val selectedCue = editCue
    if (selectedCue != null) {
        val existingEdit = state.corrections.any { it.source == sourceId && it.timeMs == selectedCue.timeMs }
        AlertDialog(
            onDismissRequest = { editCue = null },
            title = { Text("Исправить аккорд · ${musicTime(selectedCue.timeMs + state.offsetMs)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chordNameDraft,
                        onValueChange = { chordNameDraft = it.take(20) },
                        singleLine = true,
                        label = { Text("Аккорд (например, Am, F, G7)") }
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("C", "D", "E", "F", "G", "A", "B", "Am", "Dm", "Em", "—").forEach { example ->
                            FilterChip(selected = chordNameDraft == example,
                                onClick = { chordNameDraft = example }, label = { Text(example) })
                        }
                    }
                    Text("Исправление сохраняется отдельно и не удаляется при повторном анализе.",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ChordCorrections.isValidName(chordNameDraft.trim()),
                    onClick = {
                        onSaveChordCorrection(entry.id, ChordCorrection(sourceId, selectedCue.timeMs, chordNameDraft.trim()))
                        editCue = null
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    if (existingEdit) TextButton(onClick = {
                        onRemoveChordCorrection(entry.id, sourceId, selectedCue.timeMs)
                        editCue = null
                    }) { Text("Отменить правку") }
                    TextButton(onClick = { editCue = null }) { Text("Закрыть") }
                }
            }
        )
    }
}

@Composable
private fun ChordsView(
    cues: List<ChordCue>, currentIndex: Int, offset: Long,
    onSeek: (Long) -> Unit, onEdit: (ChordCue) -> Unit
) {
    if (cues.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пока нет аккордов") }
        return
    }
    // The analyzer samples every 500 ms, but an unchanged chord is not a new
    // musical event. Keep the full samples for time alignment; scroll changes only.
    val changes = remember(cues) { ChordDisplayFilter.changesOnly(cues) }
    val currentTimestamp = cues.getOrNull(currentIndex)?.timeMs ?: -1L
    val displayIndex = ChordTimeline.current(changes, currentTimestamp)
    val listState = rememberLazyListState()
    LaunchedEffect(displayIndex, changes) {
        if (displayIndex >= 0) listState.scrollToItem(displayIndex)
    }
    val now = changes.getOrNull(displayIndex)
    val next = changes.getOrNull(displayIndex + 1)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Сейчас", style = MaterialTheme.typography.labelSmall)
                    Text(if (now == null || now.name == "—") "Не определён" else now.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    if (now != null) Text(
                        "${musicTime(now.timeMs + offset)} – ${next?.let { musicTime(it.timeMs + offset) } ?: "конец"}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Следующий аккорд",
                        style = MaterialTheme.typography.labelSmall)
                    Text(next?.name ?: "—", style = MaterialTheme.typography.titleLarge)
                    if (next != null) Text(musicTime(next.timeMs + offset), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(changes.size) { index ->
                val cue = changes[index]
                Card(
                    onClick = { onEdit(cue) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == displayIndex)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(musicTime(cue.timeMs + offset), Modifier.width(72.dp),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(cue.name, fontWeight = if (index == displayIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == displayIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSeek((cue.timeMs + offset).coerceAtLeast(0L)) }) {
                            Text("▶", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoRoll(part: ScorePart, position: Long, modifier: Modifier, onSeek: (Long) -> Unit) {
    val start = max(0L, position - 1500L)
    val span = 7500L
    val centerPitch = remember(part.id) {
        val pitches = part.notes.map { it.pitch }.sorted()
        if (pitches.isEmpty()) 60 else pitches[pitches.size / 2]
    }
    val bottomPitch = (centerPitch - 12).coerceIn(0, 103)
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.17f)
    Canvas(
        modifier = modifier.pointerInput(part.id, position) {
            detectTapGestures { p -> onSeek(start + (p.x / size.width * span).toLong()) }
        }
    ) {
        for (i in 0..24) {
            val y = size.height * i / 24f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in 0..7) {
            val x = size.width * i / 7f
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
        }
        part.notes.forEach { n ->
            if (n.endMs < start || n.startMs > start + span || n.pitch !in bottomPitch..bottomPitch + 23) return@forEach
            val x1 = (n.startMs - start).toFloat() / span * size.width
            val x2 = (n.endMs - start).toFloat() / span * size.width
            val y = (bottomPitch + 23 - n.pitch).toFloat() / 24 * size.height
            val isNow = position in n.startMs until n.endMs
            drawRoundRect(if (isNow) primary else inactive,
                Offset(x1.coerceAtLeast(0f), y + 1f),
                Size(max(3f, x2 - x1), max(3f, size.height / 24f - 2)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
        }
        val playX = ((position - start).toFloat() / span * size.width).coerceIn(0f, size.width)
        drawLine(primary, Offset(playX, 0f), Offset(playX, size.height), 3f)
    }
}

private fun pitchDiatonic(pitch: Int): Int {
    val offsets = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
    return (pitch / 12) * 7 + offsets[pitch.mod(12)]
}

@Composable
private fun MusicStaff(part: ScorePart, position: Long, bassClef: Boolean, modifier: Modifier, onSeek: (Long) -> Unit) {
    val start = max(0L, position - 1500L)
    val span = 7500L
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val noteColor = MaterialTheme.colorScheme.secondary
    val reference = if (bassClef) 43 else 64 // bottom staff line: G2 for bass, E4 for treble
    Canvas(modifier.pointerInput(part.id, position) {
        detectTapGestures { p -> onSeek(start + (p.x / size.width * span).toLong()) }
    }) {
        val unit = size.height / 15f
        val baseline = size.height * 0.66f
        val xStart = 15f
        val xEnd = size.width - 8f
        for (line in 0..4) {
            val y = baseline - line * unit * 2
            drawLine(muted.copy(alpha = 0.50f), Offset(xStart, y), Offset(xEnd, y), strokeWidth = 1.7f)
        }
        part.notes.forEach { note ->
            if (note.endMs < start || note.startMs > start + span) return@forEach
            val x = (note.startMs - start).toFloat() / span * (xEnd - xStart) + xStart
            val staffStep = pitchDiatonic(note.pitch) - pitchDiatonic(reference)
            val y = baseline - staffStep * unit
            if (y !in -20f..size.height + 20f) return@forEach
            val isCurrent = position in note.startMs until note.endMs
            val color = if (isCurrent) primary else noteColor
            drawOval(color, topLeft = Offset(x - unit * 0.65f, y - unit * 0.42f),
                size = Size(unit * 1.3f, unit * 0.84f))
            drawLine(color, Offset(x + unit * 0.6f, y), Offset(x + unit * 0.6f, y - unit * 3.1f),
                strokeWidth = 2.2f, cap = StrokeCap.Round)
            if (staffStep < -1 || staffStep > 9) {
                val ledger = if (staffStep < 0) -2 else 10
                val direction = if (staffStep < 0) -2 else 2
                var d = ledger
                while ((if (direction < 0) d >= staffStep else d <= staffStep) && d in -12..22) {
                    val ly = baseline - d * unit
                    drawLine(color.copy(alpha = 0.8f), Offset(x - unit * 0.9f, ly),
                        Offset(x + unit * 1.0f, ly), 1.5f)
                    d += direction
                }
            }
        }
        val playX = (position - start).toFloat() / span * (xEnd - xStart) + xStart
        drawLine(primary, Offset(playX, 4f), Offset(playX, size.height - 4f), 2.5f)
    }
}

private data class TabPos(val stringIndex: Int, val fret: Int)

/** Suggests playable positions. MIDI does not encode guitar strings/fingering. */
private fun tabFor(pitch: Int, bass: Boolean): TabPos? {
    val tuning = if (bass) listOf(43, 38, 33, 28) else listOf(64, 59, 55, 50, 45, 40)
    return tuning.mapIndexedNotNull { index, open ->
        (pitch - open).takeIf { it in 0..24 }?.let { TabPos(index, it) }
    }.minWithOrNull(compareBy<TabPos> { it.fret }.thenBy { it.stringIndex })
}

@Composable
private fun TablatureView(part: ScorePart, position: Long, bass: Boolean) {
    val count = if (bass) 4 else 6
    val names = if (bass) listOf("G", "D", "A", "E") else listOf("e", "B", "G", "D", "A", "E")
    val visible = remember(part.id, position) {
        part.notes.filter { it.startMs >= position - 1000 && it.startMs < position + 6000 }.take(48)
    }
    val positions = visible.mapNotNull { note -> tabFor(note.pitch, bass)?.let { note to it } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Предполагаемые струны и лады (до 24-го). MIDI не хранит аппликатуру.", fontSize = 12.sp)
        if (positions.isEmpty()) Text("В ближайшие секунды нет подходящих нот.")
        else {
            val buffer = Array(count) { StringBuilder(names[it] + " |") }
            positions.forEach { (note, tab) ->
                for (stringIndex in 0 until count) {
                    buffer[stringIndex].append(if (stringIndex == tab.stringIndex) "-${tab.fret.toString().padStart(2, '-')}" else "---")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    buffer.forEach { Text(it.toString(), fontSize = 12.sp, lineHeight = 19.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        softWrap = false, maxLines = 1) }
                }
            }
            Text("Текущие ноты: " + positions.filter { position in it.first.startMs until it.first.endMs }
                .joinToString { noteName(it.first.pitch) + ": лад " + it.second.fret }, fontSize = 12.sp)
        }
    }
}

private fun musicTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
