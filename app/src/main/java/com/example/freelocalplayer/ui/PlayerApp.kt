package com.example.freelocalplayer.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.UserPlaylist
import com.example.freelocalplayer.data.selectLyricsEntry
import com.example.freelocalplayer.lyrics.LrcParser
import com.example.freelocalplayer.music.MusicDataRepository
import com.example.freelocalplayer.music.ChordCorrection
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


internal data class PlaybackInfo(
    val mediaId: String? = null,
    val playing: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val speed: Float = 1f,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val count: Int = 0,
    val ended: Boolean = false
)

@Composable
fun PlayerApp(
    library: LibraryState,
    controller: MediaController?,
    scanning: Boolean,
    onScanPhone: () -> Unit,
    musicRepository: MusicDataRepository,
    musicRevision: Int,
    analyzingMediaId: String?,
    audioAnalysisPercent: Int,
    onAnalyzeChords: (MediaEntry) -> Unit,
    onCancelAnalyze: () -> Unit,
    onDeleteAutoChords: (String) -> Unit,
    launcherDestination: String,
    launcherRequestId: Int,
    recentIds: List<String>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onImportMidiScore: (String) -> Unit,
    onImportChordChart: (String) -> Unit,
    onSaveChordChart: (String, String) -> Unit,
    onSaveChordCorrection: (String, ChordCorrection) -> Unit,
    onRemoveChordCorrection: (String, String, Long) -> Unit,
    onMusicOffsetChange: (String, Long) -> Unit,
    onDeleteMidiScore: (String) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onImportLyrics: (String) -> Unit,
    onSaveLyrics: (String, String) -> Unit,
    onPlay: (String, String) -> Unit,
    onNewPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onAddManyToPlaylist: (String, List<String>) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onMoveTrack: (String, String, Int) -> Unit,
    onRemoveMedia: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit
) {
    // Appearance settings are local-only; media and playlist storage are untouched.
    val fusionContext = LocalContext.current
    val fusionPrefs = remember(fusionContext) {
        fusionContext.applicationContext.getSharedPreferences("freelocalplayer_appearance", Context.MODE_PRIVATE)
    }
    var paletteKey by remember { mutableStateOf(fusionPrefs.getString("palette", "STUDIO_DARK")) }
    var reducedMotion by remember { mutableStateOf(fusionPrefs.getBoolean("reduced_motion", false)) }
    var fusionSettingsOpen by remember { mutableStateOf(false) }
    val fusionPalette = FusionPalette.fromStored(paletteKey)
    var playback by remember { mutableStateOf(PlaybackInfo()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selection by rememberSaveable { mutableStateOf("all") }
    var chosenTrackId by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var addTrackToPlaylistId by remember { mutableStateOf<String?>(null) }
    var trackToDelete by remember { mutableStateOf<String?>(null) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var musicModeOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsEditTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) {
        while (true) {
            val player = controller
            playback = if (player == null) PlaybackInfo() else {
                val d = player.duration
                PlaybackInfo(
                    mediaId = player.currentMediaItem?.mediaId,
                    playing = player.isPlaying,
                    position = player.currentPosition.coerceAtLeast(0L),
                    duration = if (d == C.TIME_UNSET || d <= 0) 0 else d,
                    speed = player.playbackParameters.speed,
                    repeat = player.repeatMode,
                    shuffle = player.shuffleModeEnabled,
                    count = player.mediaItemCount,
                    ended = player.playbackState == Player.STATE_ENDED
                )
            }
            delay(400)
        }
    }

    // Re-handle the same launcher shortcut after a second long-press invocation.
    LaunchedEffect(launcherRequestId) {
        when (launcherDestination) {
            "playlists" -> { tab = 2; nowPlayingOpen = false; musicModeOpen = false }
            "recent" -> { tab = 4; nowPlayingOpen = false; musicModeOpen = false }
            "favorites" -> { tab = 5; nowPlayingOpen = false; musicModeOpen = false }
        }
    }

    // Always bind lyrics to the actual playing track, not the previously selected track.
    // The selection is still used when playback is stopped.
    val current = library.media.find { it.id == playback.mediaId }
    val textEntry = selectLyricsEntry(library.media, playback.mediaId, chosenTrackId)
    val isTrackPlaying = playback.mediaId != null
    LaunchedEffect(playback.mediaId) {
        playback.mediaId?.let { playingId ->
            if (chosenTrackId != playingId) chosenTrackId = playingId
        }
    }
    val playAndOpen: (String, String) -> Unit = { id, source ->
        chosenTrackId = id
        onPlay(id, source)
        if (library.media.firstOrNull { it.id == id }?.isVideo == true) {
            nowPlayingOpen = true
            musicModeOpen = false
        } else {
            nowPlayingOpen = false
            musicModeOpen = true
        }
    }

    ModernFusionTheme(palette = fusionPalette, reducedMotion = reducedMotion) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("FreeLocalPlayer", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text("${library.media.size}", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                IconButton(onClick = { fusionSettingsOpen = true }) {
                    Icon(Icons.Default.Palette, "Выбрать оформление",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            val tabs = listOf("Музыка", "Видео", "Списки", "Тексты", "Недавние", "Избранное")
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        text = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            when (tab) {
                0, 1, 4, 5 -> {
                    val source = when (tab) {
                        0 -> "audio"
                        1 -> "video"
                        4 -> "recent"
                        else -> "favorites"
                    }
                    val allById = library.media.associateBy { it.id }
                    val media = when (source) {
                        "recent" -> recentIds.mapNotNull(allById::get)
                        "favorites" -> library.media.filter { it.id in favoriteIds }
                        else -> library.tracksFor(source)
                    }.filter { it.title.contains(search.trim(), ignoreCase = true) }
                    Column(modifier = Modifier.weight(1f)) {
                        if (tab == 0 || tab == 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onImportFiles, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Добавить файлы")
                            }
                            OutlinedButton(onClick = onImportFolder, enabled = !scanning,
                                contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (scanning) "Сканирую…" else "Папка")
                            }
                        }
                        TextButton(onClick = onScanPhone, enabled = !scanning) {
                            Icon(Icons.Default.Refresh, "Сканировать память телефона", Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (scanning) "Сканирование…" else "Найти музыку и видео на телефоне")
                        }
                        }
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Поиск по названию") }
                        )
                        Spacer(Modifier.height(5.dp))
                        if (media.isEmpty()) {
                            EmptyHint(if (search.isNotBlank()) "По запросу ничего не найдено."
                                else when (tab) {
                                    0 -> "Добавь музыку через кнопку выше."
                                    1 -> "Добавь видео через кнопку выше."
                                    4 -> "История прослушивания пока пуста."
                                    else -> "Добавь песни в избранное кнопкой звёздочки."
                                })
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(media, key = { it.id }) { entry ->
                                    TrackRow(
                                        entry = entry,
                                        active = playback.mediaId == entry.id,
                                        onClick = { chosenTrackId = entry.id; playAndOpen(entry.id, source) },
                                        onAdd = { addTrackToPlaylistId = entry.id },
                                        onText = { chosenTrackId = entry.id; tab = 3 },
                                        onDelete = { trackToDelete = entry.id },
                                        favorite = entry.id in favoriteIds,
                                        onFavorite = { onToggleFavorite(entry.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> PlaylistPanel(
                    library = library,
                    selection = selection,
                    playingId = playback.mediaId,
                    onSelectionChange = { selection = it },
                    onCreate = onNewPlaylist,
                    onDelete = onDeletePlaylist,
                    onAdd = onAddToPlaylist,
                    onAddMany = onAddManyToPlaylist,
                    onRemove = onRemoveFromPlaylist,
                    onMove = onMoveTrack,
                    onPlay = { mediaId ->
                        chosenTrackId = mediaId
                        playAndOpen(mediaId, selection)
                    },
                    modifier = Modifier.weight(1f)
                )
                3 -> LyricsPanel(
                    library = library,
                    entry = textEntry,
                    currentId = playback.mediaId,
                    position = playback.position,
                    onPick = { id ->
                        chosenTrackId = id
                        // The lyrics tab must not keep showing a different song while audio plays.
                        if (playback.mediaId != id) onPlay(id, "all")
                    },
                    editRequestedForId = lyricsEditTargetId,
                    onEditRequestConsumed = { lyricsEditTargetId = null },
                    onImport = onImportLyrics,
                    onSave = onSaveLyrics,
                    onSeek = { millis ->
                        if (controller != null && textEntry?.id == playback.mediaId) controller.seekTo(millis)
                        else if (textEntry != null) {
                            onPlay(textEntry.id, "all")
                            controller?.seekTo(millis)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // One consistent mini-player on every library tab. The title, seek and
            // +/-10 second actions stay available without dominating the library.
            if (current != null) PlayerPanel(
                controller = controller,
                current = current,
                info = playback,
                expanded = nowPlayingOpen,
                onExpand = {
                    if (current != null) {
                        if (current.isVideo) nowPlayingOpen = true else musicModeOpen = true
                    }
                },
                onSpeedChange = onSpeedChange,
                onShuffleChange = onShuffleChange,
                onRepeatChange = onRepeatChange,
                onMusicMode = { musicModeOpen = true },
                onOpenQueue = { queueOpen = true }
            )

        }

        if (fusionSettingsOpen) {
            FusionSettingsDialog(
                selected = fusionPalette,
                reducedMotion = reducedMotion,
                onPaletteSelected = { selected ->
                    paletteKey = selected.name
                    fusionPrefs.edit().putString("palette", selected.name).apply()
                },
                onReducedMotionChanged = { enabled ->
                    reducedMotion = enabled
                    fusionPrefs.edit().putBoolean("reduced_motion", enabled).apply()
                },
                onDismiss = { fusionSettingsOpen = false }
            )
        }
        if (nowPlayingOpen && current != null && current.isVideo) {
            FullscreenVideoScreen(
                entry = current,
                controller = controller,
                info = playback,
                onClose = { nowPlayingOpen = false },
                onOpenQueue = { queueOpen = true },
                onShuffleChange = onShuffleChange,
                onRepeatChange = onRepeatChange
            )
        }
        if (nowPlayingOpen && current != null && !current.isVideo) {
            NowPlayingScreen(
                entry = current,
                controller = controller,
                info = playback,
                onClose = { nowPlayingOpen = false },
                onMusicMode = {
                    nowPlayingOpen = false
                    musicModeOpen = true
                },
                onEditLyrics = {
                    chosenTrackId = current.id
                    tab = 3
                    nowPlayingOpen = false
                },
                onSpeedChange = onSpeedChange,
                onShuffleChange = onShuffleChange,
                onRepeatChange = onRepeatChange,
                onOpenQueue = { queueOpen = true }
            )
        }
        if (musicModeOpen && current != null) {
            MusicModeScreen(
                entry = current,
                controller = controller,
                playback = playback,
                repository = musicRepository,
                revision = musicRevision,
                analyzing = analyzingMediaId == current.id,
                analysisLocked = analyzingMediaId != null,
                analysisPercent = audioAnalysisPercent,
                onAnalyzeAudio = onAnalyzeChords,
                onCancelAnalysis = onCancelAnalyze,
                onDeleteAudioAnalysis = onDeleteAutoChords,
                onImportLyrics = onImportLyrics,
                onSaveLyrics = onSaveLyrics,
                onOpenLyricsEditor = {
                    chosenTrackId = current.id
                    tab = 3
                    lyricsEditTargetId = current.id
                    musicModeOpen = false
                },
                onOpenCover = {
                    musicModeOpen = false
                    nowPlayingOpen = true
                },
                onDismiss = { musicModeOpen = false },
                onImportMidi = onImportMidiScore,
                onImportChords = onImportChordChart,
                onSaveChords = onSaveChordChart,
                onSaveChordCorrection = onSaveChordCorrection,
                onRemoveChordCorrection = onRemoveChordCorrection,
                onOffsetChange = onMusicOffsetChange,
                onDeleteMidi = onDeleteMidiScore,
                onSpeedChange = onSpeedChange,
                onShuffleChange = onShuffleChange,
                onRepeatChange = onRepeatChange,
                onOpenQueue = { queueOpen = true }
            )
        }
        if (queueOpen) {
            PlaybackQueueDialog(
                controller = controller,
                info = playback,
                onDismiss = { queueOpen = false },
                onTrackSelected = { selectedId ->
                    // The Media3 queue remains unchanged. Only the active item changes.
                    if (nowPlayingOpen || musicModeOpen) {
                        val video = library.media.firstOrNull { it.id == selectedId }?.isVideo == true
                        if (video) {
                            nowPlayingOpen = true
                            musicModeOpen = false
                        } else if (musicModeOpen) {
                            nowPlayingOpen = false
                        } else {
                            nowPlayingOpen = true
                        }
                    }
                }
            )
        }
        if (addTrackToPlaylistId != null) {
            val entryId = addTrackToPlaylistId!!
            AlertDialog(
                onDismissRequest = { addTrackToPlaylistId = null },
                title = { Text("Добавить в плейлист") },
                text = {
                    if (library.playlists.isEmpty()) Text("Сначала создай плейлист на вкладке «Списки».")
                    else Column {
                        library.playlists.forEach { playlist ->
                            TextButton(onClick = {
                                onAddToPlaylist(playlist.id, entryId)
                                addTrackToPlaylistId = null
                            }) { Text(playlist.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { addTrackToPlaylistId = null }) { Text("Закрыть") } }
            )
        }
        if (trackToDelete != null) {
            val id = trackToDelete!!
            AlertDialog(
                onDismissRequest = { trackToDelete = null },
                title = { Text("Удалить из библиотеки?") },
                text = { Text("Файл на телефоне не удалится. Он исчезнет только из плеера и его плейлистов.") },
                confirmButton = {
                    TextButton(onClick = { onRemoveMedia(id); trackToDelete = null }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { trackToDelete = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    controller: MediaController?,
    current: MediaEntry?,
    info: PlaybackInfo,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onMusicMode: () -> Unit,
    onOpenQueue: () -> Unit
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val enabled = controller != null && info.count > 0 && current != null
    val progress = if (info.duration > 0L)
        (info.position.toFloat() / info.duration.toFloat()).coerceIn(0f, 1f) else 0f

    FusionGlassCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (current?.isVideo == true) Icons.Default.VideoFile else Icons.Default.MusicNote,
                    null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(current?.title ?: "Нет воспроизведения",
                    modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onExpand),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium)
                FusionPlayPauseButton(
                    playing = info.playing,
                    enabled = enabled,
                    size = 42.dp,
                    onClick = {
                        if (info.playing) controller?.pause() else {
                            if (info.ended) controller?.seekTo(0L)
                            controller?.play()
                        }
                    }
                )
                IconButton(onClick = { controller?.seekToNextMediaItem() },
                    enabled = enabled && (controller?.hasNextMediaItem() == true || info.count > 1),
                    modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipNext, "Следующая песня", Modifier.size(25.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onExpand, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInFull, if (current?.isVideo == true)
                        "Полноэкранное видео" else "Полноэкранный плеер", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            // A discreet progress line is enough for the library mini-player.
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                PlaybackModeSelector(info, onShuffleChange, onRepeatChange)
                IconButton(
                    onClick = onOpenQueue,
                    enabled = enabled,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.QueueMusic, "Очередь воспроизведения", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("${time(info.position)} / ${time(info.duration)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { settingsOpen = true }, enabled = enabled,
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "Дополнительные действия", Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Управление воспроизведением") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Скорость: ${"%.2f".format(java.util.Locale.US, info.speed)}×")
                    Slider(value = info.speed.coerceIn(0.5f, 2f),
                        onValueChange = { onSpeedChange((it * 20f).roundToInt() / 20f) },
                        valueRange = 0.5f..2f)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { controller?.seekTo((info.position - 10_000L).coerceAtLeast(0L)) },
                            modifier = Modifier.weight(1f)) { Text("−10 с") }
                        OutlinedButton(onClick = { controller?.seekTo(if (info.duration > 0L)
                            (info.position + 10_000L).coerceAtMost(info.duration) else info.position + 10_000L) },
                            modifier = Modifier.weight(1f)) { Text("+10 с") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { controller?.seekToPreviousMediaItem() },
                            modifier = Modifier.weight(1f)) { Text("Предыдущая") }
                        OutlinedButton(onClick = { controller?.seekToNextMediaItem() },
                            modifier = Modifier.weight(1f)) { Text("Следующая") }
                    }
                    if (current?.isVideo == false) {
                        TextButton(onClick = { settingsOpen = false; onMusicMode() }) {
                            Text("Текст, ноты и аккорды")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Готово") } }
        )
    }
}

@Composable
private fun TrackRow(
    entry: MediaEntry,
    active: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onText: () -> Unit,
    onDelete: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor =
        if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaThumbnail(entry)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(if (entry.lyrics.isBlank()) "Без текста" else "Текст добавлен",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    if (favorite) "Удалить из избранного" else "Добавить в избранное",
                    Modifier.size(20.dp),
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onText, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.NoteAlt, "Показать/добавить текст", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlaylistAdd, "В плейлист", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, "Убрать из библиотеки", Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun PlaylistPanel(
    library: LibraryState,
    selection: String,
    playingId: String?,
    onSelectionChange: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onAddMany: (String, List<String>) -> Unit,
    onRemove: (String, String) -> Unit,
    onMove: (String, String, Int) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var createError by remember { mutableStateOf("") }
    var pendingNewName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(library.playlists, pendingNewName) {
        val created = library.playlists.find { it.name.equals(pendingNewName, ignoreCase = true) }
        if (created != null && pendingNewName != null) {
            onSelectionChange("playlist:${created.id}")
            pendingNewName = null
        }
    }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val activePlaylist = library.playlists.find { "playlist:${it.id}" == selection }
    val tracks = library.tracksFor(selection)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                activePlaylist?.name ?: "Плейлисты",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Плейлист")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = selection == "all", onClick = { onSelectionChange("all") },
                    label = { Text("Все файлы") })
            }
            items(library.playlists, key = { it.id }) { playlist ->
                FilterChip(selected = selection == "playlist:${playlist.id}",
                    onClick = { onSelectionChange("playlist:${playlist.id}") },
                    label = { Text(playlist.name) })
            }
        }
        if (activePlaylist != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${tracks.size} записей", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("Добавить")
                }
                IconButton(onClick = { deleting = true }) {
                    Icon(Icons.Default.DeleteOutline, "Удалить плейлист")
                }
            }
        } else Text("Библиотека: ${tracks.size} файлов", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (tracks.isEmpty()) EmptyHint(if (activePlaylist != null) "Плейлист пуст. Нажми «Добавить»." else "Файлов пока нет.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks, key = { it.id }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Row(modifier = Modifier.weight(1f).clickable { onPlay(entry.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            MediaThumbnail(entry)
                            Spacer(Modifier.width(10.dp))
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (activePlaylist != null) {
                            var actionsExpanded by remember(entry.id) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { actionsExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, "Действия с песней")
                                }
                                DropdownMenu(
                                    expanded = actionsExpanded,
                                    onDismissRequest = { actionsExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Поднять выше") },
                                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                        onClick = {
                                            actionsExpanded = false
                                            onMove(activePlaylist.id, entry.id, -1)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Опустить ниже") },
                                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                        onClick = {
                                            actionsExpanded = false
                                            onMove(activePlaylist.id, entry.id, 1)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Убрать из плейлиста") },
                                        leadingIcon = { Icon(Icons.Default.Close, null) },
                                        onClick = {
                                            actionsExpanded = false
                                            onRemove(activePlaylist.id, entry.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false; createError = "" },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; createError = "" },
                    label = { Text("Название") },
                    singleLine = true,
                    isError = createError.isNotEmpty(),
                    supportingText = if (createError.isEmpty()) null else ({ Text(createError) })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    when {
                        name.isEmpty() -> createError = "Введи название"
                        library.playlists.any { it.name.equals(name, ignoreCase = true) } ->
                            createError = "Такой плейлист уже есть"
                        else -> {
                            pendingNewName = name
                            onCreate(name)
                            newName = ""
                            createError = ""
                            creating = false
                        }
                    }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; createError = "" }) { Text("Отмена") }
            }
        )
    }
    if (adding && activePlaylist != null) {
        FullScreenPlaylistMediaPicker(
            playlist = activePlaylist,
            media = library.media,
            onAddMedia = { mediaId -> onAdd(activePlaylist.id, mediaId) },
            onAddMany = { mediaIds -> onAddMany(activePlaylist.id, mediaIds) },
            onClose = { adding = false }
        )
    }
    if (deleting && activePlaylist != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("Исходные аудио- и видеофайлы останутся в библиотеке.") },
            confirmButton = { TextButton(onClick = {
                onDelete(activePlaylist.id)
                onSelectionChange("all")
                deleting = false
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } }
        )
    }
}

/**
 * Full-screen replacement for the small AlertDialog. Every media row remains visible
 * after an addition, so the next row does not jump underneath the user's finger.
 * The bulk action only adds the files matching the current search/filter.
 */
@Composable
private fun FullScreenPlaylistMediaPicker(
    playlist: UserPlaylist,
    media: List<MediaEntry>,
    onAddMedia: (String) -> Unit,
    onAddMany: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    var typeFilter by rememberSaveable(playlist.id) { mutableIntStateOf(0) }
    val includedIds = remember(playlist.mediaIds) { playlist.mediaIds.toHashSet() }
    val visibleMedia = remember(media, query, typeFilter) {
        val cleanedQuery = query.trim()
        media.filter { entry ->
            val matchesType = when (typeFilter) {
                1 -> !entry.isVideo
                2 -> entry.isVideo
                else -> true
            }
            matchesType && (cleanedQuery.isEmpty() ||
                entry.title.contains(cleanedQuery, ignoreCase = true))
        }
    }
    val missingIds = visibleMedia.filterNot { it.id in includedIds }.map { it.id }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Добавить файлы", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("В плейлист «${playlist.name}»",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть выбор файлов")
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Найти файл") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                            }
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Все" to 0, "Музыка" to 1, "Видео" to 2).forEach { (name, index) ->
                        FilterChip(
                            selected = typeFilter == index,
                            onClick = { typeFilter = index },
                            label = { Text(name) }
                        )
                    }
                }
                Text(
                    "Найдено: ${visibleMedia.size} · Уже в списке: ${playlist.mediaIds.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                if (visibleMedia.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (media.isEmpty()) "Сначала добавь музыку или видео в медиатеку."
                            else "Файлы по такому запросу не найдены.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(visibleMedia, key = { it.id }) { entry ->
                            val alreadyAdded = entry.id in includedIds
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (alreadyAdded)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyAdded) {
                                            onAddMedia(entry.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MediaThumbnail(entry)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (alreadyAdded) "Уже добавлено"
                                            else if (entry.isVideo) "Добавить видео"
                                            else "Добавить аудио",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        if (alreadyAdded) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (alreadyAdded) "Добавлено"
                                            else "Добавить в плейлист",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Button(
                    onClick = { onAddMany(missingIds) },
                    enabled = missingIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Добавить все найденные (${missingIds.size})")
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Готово")
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    library: LibraryState,
    entry: MediaEntry?,
    currentId: String?,
    position: Long,
    onPick: (String) -> Unit,
    editRequestedForId: String?,
    onEditRequestConsumed: () -> Unit,
    onImport: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var choosing by remember { mutableStateOf(false) }
    var editing by remember(entry?.id) { mutableStateOf(false) }
    var draft by remember(entry?.id, entry?.lyrics) { mutableStateOf(entry?.lyrics.orEmpty()) }
    LaunchedEffect(editRequestedForId, entry?.id) {
        if (editRequestedForId != null && editRequestedForId == entry?.id) {
            draft = entry.lyrics
            editing = true
            onEditRequestConsumed()
        }
    }
    val timed = remember(entry?.lyrics) { LrcParser.parse(entry?.lyrics.orEmpty()) }
    val currentIndex = if (currentId == entry?.id) LrcParser.currentLine(timed, position) else -1
    val scrollState = rememberLazyListState()

    LaunchedEffect(entry?.id, currentIndex) {
        if (currentIndex >= 0) scrollState.animateScrollToItem(currentIndex.coerceAtLeast(0))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry == null) {
            EmptyHint("Сначала добавь аудио или видео. После этого можно привязать к нему текст.")
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (entry.id != currentId) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (timed.isNotEmpty()) "LRC" else "TXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onImport(entry.id) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.NoteAlt, contentDescription = "Импорт TXT или LRC")
            }
            IconButton(onClick = { draft = entry.lyrics; editing = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать текст")
            }
            IconButton(onClick = { choosing = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "Выбрать другую песню")
            }
        }
        if (entry.lyrics.isBlank()) {
            EmptyHint("Здесь будет текст песни. Добавь файл .txt/.lrc или нажми «Изменить» и вставь текст.")
        } else if (timed.isNotEmpty()) {
            LazyColumn(state = scrollState, modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(timed.size) { index ->
                    val line = timed[index]
                    Card(onClick = { onSeek(line.timeMs) },
                        colors = CardDefaults.cardColors(containerColor =
                            if (index == currentIndex) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(time(line.timeMs), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(line.text,
                                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == currentIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    Text(entry.lyrics, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (editing && entry != null) {
        FullscreenLyricsEditor(
            trackTitle = entry.title,
            text = draft,
            onTextChange = { draft = it },
            onSave = { onSave(entry.id, draft); editing = false },
            onDismiss = { editing = false }
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Для какого файла показать текст?") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(library.media, key = { it.id }) { media ->
                        TextButton(onClick = { onPick(media.id); choosing = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(media.title, modifier = Modifier.fillMaxWidth(), maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Закрыть") } }
        )
    }
}

/** Full-height editor which respects the IME and never gets buried behind player controls. */
@Composable
private fun FullscreenLyricsEditor(
    trackTitle: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().imePadding().safeDrawingPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Редактор текста песни", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(trackTitle, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть без сохранения") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Текст TXT или LRC") },
                    placeholder = { Text("Вставь текст песни или строки LRC [00:12.50]") },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(value: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

private fun time(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0L)
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(java.util.Locale.US, seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    } else {
        "%d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)
    }
}
