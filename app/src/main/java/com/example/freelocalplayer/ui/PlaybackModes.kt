package com.example.freelocalplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/** User-facing playback policies. All settings use Media3 and remain persisted by MainActivity. */
internal enum class PlaybackPreset(
    val title: String,
    val explanation: String,
    val shuffle: Boolean,
    val repeat: Int
) {
    ORDER("По порядку", "Проиграть список один раз от начала до конца", false, Player.REPEAT_MODE_OFF),
    REPEAT_ALL("Повтор списка", "После последней песни начать список заново", false, Player.REPEAT_MODE_ALL),
    REPEAT_ONE("Повтор одной", "Повторять только текущую песню", false, Player.REPEAT_MODE_ONE),
    SHUFFLE("Смешано", "Проиграть песни в случайном порядке один раз", true, Player.REPEAT_MODE_OFF),
    SHUFFLE_REPEAT("Смешано + цикл", "Случайный порядок с повтором всего списка", true, Player.REPEAT_MODE_ALL)
}

internal fun detectPlaybackPreset(repeat: Int, shuffle: Boolean): PlaybackPreset = when {
    repeat == Player.REPEAT_MODE_ONE -> PlaybackPreset.REPEAT_ONE
    repeat == Player.REPEAT_MODE_ALL && shuffle -> PlaybackPreset.SHUFFLE_REPEAT
    repeat == Player.REPEAT_MODE_ALL -> PlaybackPreset.REPEAT_ALL
    shuffle -> PlaybackPreset.SHUFFLE
    else -> PlaybackPreset.ORDER
}

/** A compact but discoverable single place to view and change all queue modes. */
@Composable
internal fun PlaybackModeSelector(
    info: PlaybackInfo,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val selected = detectPlaybackPreset(info.repeat, info.shuffle)
    TextButton(onClick = { open = true }, enabled = info.count > 0, modifier = modifier) {
        Icon(
            when (selected) {
                PlaybackPreset.ORDER -> Icons.Default.FormatListNumbered
                PlaybackPreset.REPEAT_ALL -> Icons.Default.Repeat
                PlaybackPreset.REPEAT_ONE -> Icons.Default.RepeatOne
                else -> Icons.Default.Shuffle
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(selected.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Порядок воспроизведения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    PlaybackPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    onShuffleChange(preset.shuffle)
                                    onRepeatChange(preset.repeat)
                                    open = false
                                }
                                .padding(vertical = 9.dp, horizontal = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected == preset) {
                                Icon(Icons.Default.Check, contentDescription = "Выбрано",
                                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Spacer(Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.title, fontWeight = if (selected == preset) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(preset.explanation, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } }
        )
    }
}
