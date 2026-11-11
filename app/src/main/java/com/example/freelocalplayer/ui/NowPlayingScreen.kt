package com.example.freelocalplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.lyrics.LrcParser
import com.example.freelocalplayer.lyrics.TimedLine
import com.example.freelocalplayer.media.ArtworkReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** A true full-screen 'now playing' experience, with embedded artwork or lyrics. */
@Composable
internal fun NowPlayingScreen(
    entry: MediaEntry,
    controller: MediaController?,
    info: PlaybackInfo,
    onClose: () -> Unit,
    onEditLyrics: () -> Unit,
    onMusicMode: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onOpenQueue: () -> Unit
) {
    var showLyrics by remember(entry.id) { mutableStateOf(entry.lyrics.isNotBlank()) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )))
                    .safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сейчас играет", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onMusicMode) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ноты")
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Default.QueueMusic, "Очередь воспроизведения")
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть экран") }
                }
                if (!entry.isVideo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = !showLyrics, onClick = { showLyrics = false },
                            label = { Text("Обложка") }
                        )
                        FilterChip(
                            selected = showLyrics, onClick = { showLyrics = true },
                            label = { Text("Текст песни") }
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        entry.isVideo && controller != null -> AndroidView(
                            factory = { context -> PlayerView(context).apply { useController = false } },
                            update = { it.player = controller },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize()
                        )
                        showLyrics -> FullscreenLyrics(
                            lyrics = entry.lyrics,
                            positionMs = info.position,
                            onSeek = { controller?.seekTo(it) },
                            onEdit = onEditLyrics,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> FullscreenArtwork(entry, Modifier.fillMaxSize())
                    }
                }
                FullscreenTransport(controller, info, onSpeedChange, onShuffleChange, onRepeatChange)
            }
        }
    }
}

@Composable
private fun FullscreenArtwork(entry: MediaEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.uri) {
        value = withContext(Dispatchers.IO) { ArtworkReader.read(context, entry.uri) }
    }
    FusionGlassCard(modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(), contentDescription = "Обложка: ${entry.title}",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null,
                        modifier = Modifier.size(110.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Обложка не найдена в файле", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun FullscreenLyrics(
    lyrics: String,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isBlank()) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Для этой песни пока нет текста", textAlign = TextAlign.Center)
            TextButton(onClick = onEdit) { Text("Добавить текст TXT / LRC") }
        }
        return
    }
    val lines = remember(lyrics) { LrcParser.parse(lyrics) }
    if (lines.isEmpty()) {
        Box(modifier.verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.TopCenter) {
            Text(lyrics, fontSize = 20.sp, lineHeight = 31.sp, textAlign = TextAlign.Center)
        }
    } else {
        val activeIndex = LrcParser.currentLine(lines, positionMs)
        val state = rememberLazyListState()
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) state.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
        LazyColumn(
            state = state, modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == activeIndex
                Text(
                    text = if (active && line.words.isNotEmpty()) highlightWords(line, positionMs) else
                        buildAnnotatedString { append(line.text) },
                    fontSize = if (active) 25.sp else 21.sp,
                    lineHeight = if (active) 35.sp else 30.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        active && line.words.isEmpty() -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun highlightWords(line: TimedLine, positionMs: Long) = buildAnnotatedString {
    val highlightedColor = MaterialTheme.colorScheme.primary
    val pastColor = MaterialTheme.colorScheme.secondary
    val futureColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeWord = LrcParser.currentWord(line.words, positionMs)
    line.words.forEachIndexed { index, word ->
        val color = when {
            index == activeWord -> highlightedColor
            index < activeWord -> pastColor
            else -> futureColor
        }
        pushStyle(SpanStyle(color = color, fontWeight = if (index == activeWord) FontWeight.ExtraBold else FontWeight.Normal))
        append(word.text)
        pop()
    }
}

@Composable
private fun FullscreenTransport(
    controller: MediaController?,
    info: PlaybackInfo,
    onSpeedChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit
) {
    val enabled = controller != null && info.count > 0
    var fraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(info.position, info.duration, dragging) {
        if (!dragging) fraction = if (info.duration > 0) {
            (info.position.toFloat() / info.duration).coerceIn(0f, 1f)
        } else 0f
    }
    Slider(
        value = fraction,
        onValueChange = { fraction = it; dragging = true },
        onValueChangeFinished = {
            if (info.duration > 0L) controller?.seekTo((fraction * info.duration).toLong())
            dragging = false
        }, enabled = enabled && info.duration > 0
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatMs(if (dragging) (fraction * info.duration).toLong() else info.position))
        Text(formatMs(info.duration))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = enabled) {
            Icon(Icons.Default.SkipPrevious, "Предыдущая песня")
        }
        IconButton(onClick = { controller?.seekTo((info.position - 10_000L).coerceAtLeast(0L)) },
            enabled = enabled) { Icon(Icons.Default.FastRewind, "−10 секунд") }
        FusionPlayPauseButton(
            playing = info.playing,
            enabled = enabled,
            onClick = {
                if (info.playing) controller?.pause() else {
                    if (info.ended) controller?.seekTo(0L)
                    controller?.play()
                }
            }
        )
        IconButton(onClick = { controller?.seekTo(info.position + 10_000L) },
            enabled = enabled) { Icon(Icons.Default.FastForward, "+10 секунд") }
        IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = enabled) {
            Icon(Icons.Default.SkipNext, "Следующая песня")
        }
    }
    var speedOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        PlaybackModeSelector(info, onShuffleChange, onRepeatChange)
        TextButton(onClick = { speedOpen = true }) {
            Text(String.format(Locale.US, "%.2f×", info.speed) + " · Скорость")
        }
    }
    if (speedOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { speedOpen = false },
            title = { Text("Скорость воспроизведения") },
            text = {
                Column {
                    Text(String.format(Locale.US, "%.2f×", info.speed))
                    Slider(value = info.speed.coerceIn(0.5f, 2f),
                        onValueChange = { onSpeedChange((it * 20f).roundToInt() / 20f) },
                        valueRange = 0.5f..2f)
                    TextButton(onClick = { onSpeedChange(1f) }) { Text("Обычная скорость 1×") }
                }
            },
            confirmButton = { TextButton(onClick = { speedOpen = false }) { Text("Готово") } }
        )
    }
}

private fun formatMs(value: Long): String {
    val seconds = value.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600,
        (seconds % 3600) / 60, seconds % 60) else
        String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
