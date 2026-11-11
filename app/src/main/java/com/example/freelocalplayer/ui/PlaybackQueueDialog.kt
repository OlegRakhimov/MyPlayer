package com.example.freelocalplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController

internal data class QueueEntry(val index: Int, val mediaId: String, val title: String)

/** No filename or identifier may be confused with a fabricated song title. */
internal fun queueTitle(rawTitle: String?, mediaId: String, index: Int): String {
    return rawTitle?.trim()?.takeIf(String::isNotEmpty)
        ?: mediaId.trim().takeIf(String::isNotEmpty)
        ?: "Запись ${index + 1}"
}

internal fun safeQueueIndex(index: Int, size: Int): Int? =
    index.takeIf { it >= 0 && it < size }

/**
 * Shows the actual queue held by Media3, not a newly generated playlist.
 * Selecting a row seeks within that queue and preserves repeat/shuffle settings.
 */
@Composable
internal fun PlaybackQueueDialog(
    controller: MediaController?,
    info: PlaybackInfo,
    onDismiss: () -> Unit,
    onTrackSelected: (String) -> Unit
) {
    val canInspect = controller != null &&
        controller.isCommandAvailable(Player.COMMAND_GET_TIMELINE)
    val rows = if (canInspect) {
        (0 until (controller?.mediaItemCount ?: 0)).map { index ->
            val item = controller!!.getMediaItemAt(index)
            QueueEntry(index, item.mediaId,
                queueTitle(item.mediaMetadata.title?.toString(), item.mediaId, index))
        }
    } else emptyList()
    val activeIndex = if (canInspect) controller!!.currentMediaItemIndex else -1
    val canSeek = controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) == true
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(rows, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) rows
        else rows.filter { it.title.contains(normalized, ignoreCase = true) }
    }
    val initial = if (activeIndex >= 0) (activeIndex - 2).coerceAtLeast(0) else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    LaunchedEffect(query) {
        if (query.isNotEmpty()) listState.scrollToItem(0)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Очередь воспроизведения",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("${rows.size} записей · ${detectPlaybackPreset(info.repeat, info.shuffle).title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрыть очередь")
                    }
                }
                if (info.shuffle) {
                    Text("Перемешивание включено: следующий трек может отличаться от порядка в списке.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск в очереди") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (rows.isEmpty()) {
                    Text("Очередь пуста или недоступна.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (results.isEmpty()) {
                    Text("Ничего не найдено.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(results, key = { it.index }) { entry ->
                            val active = entry.index == activeIndex
                            Card(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                                    .clickable(enabled = canSeek) {
                                        val validIndex = safeQueueIndex(
                                            entry.index, controller?.mediaItemCount ?: 0
                                        )
                                        if (controller != null && validIndex != null &&
                                            controller.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) &&
                                            controller.getMediaItemAt(validIndex).mediaId == entry.mediaId
                                        ) {
                                            controller.seekToDefaultPosition(validIndex)
                                            controller.play()
                                            onTrackSelected(entry.mediaId)
                                            onDismiss()
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (active)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${entry.index + 1}",
                                        modifier = Modifier.width(36.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(entry.title, modifier = Modifier.weight(1f),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                    if (active) {
                                        Spacer(Modifier.size(8.dp))
                                        Icon(Icons.Default.PlayArrow, "Сейчас играет",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
