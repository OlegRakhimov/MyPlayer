package com.example.freelocalplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.freelocalplayer.data.MediaEntry
import kotlinx.coroutines.delay
import java.util.Locale

/** Full-screen video: tap anywhere on the picture to show/hide the overlay. */
@Composable
internal fun FullscreenVideoScreen(
    entry: MediaEntry,
    controller: MediaController?,
    info: PlaybackInfo,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit
) {
    var controlsVisible by remember(entry.id) { mutableStateOf(true) }
    var slider by remember(entry.id) { mutableFloatStateOf(0f) }
    var dragging by remember(entry.id) { mutableStateOf(false) }

    LaunchedEffect(info.position, info.duration, dragging) {
        if (!dragging) slider = if (info.duration > 0L) {
            (info.position.toFloat() / info.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }
    // Do not key this effect by position: playback ticks would prevent auto-hide.
    LaunchedEffect(controlsVisible, info.playing) {
        if (controlsVisible && info.playing) {
            delay(3_000L)
            controlsVisible = false
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (window != null) {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (controller != null) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        update = { playerView -> playerView.player = controller },
                        onRelease = { playerView -> playerView.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Transparent tap target overlays the video, not the other way around.
                Box(Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible })
                if (controlsVisible) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(
                                    Color.Black.copy(alpha = 0.88f),
                                    Color.Black.copy(alpha = 0.45f)
                                )))
                                .padding(start = 14.dp, top = 18.dp, end = 6.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = entry.title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            ModernFusionTheme(FusionPalette.STUDIO_DARK) {
                                PlaybackModeSelector(info, onShuffleChange, onRepeatChange)
                            }
                            IconButton(onClick = onOpenQueue) {
                                Icon(Icons.Default.QueueMusic, "Очередь воспроизведения", tint = Color.White)
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, "Закрыть видео", tint = Color.White)
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Black.copy(alpha = 0.9f)
                                )))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(onClick = {
                                    controller?.seekTo((info.position - 10_000L).coerceAtLeast(0L))
                                    controlsVisible = true
                                }, modifier = Modifier.size(56.dp)) {
                                    Icon(Icons.Default.FastRewind, "Назад на 10 секунд",
                                        modifier = Modifier.size(30.dp), tint = Color.White)
                                }
                                Spacer(Modifier.size(24.dp))
                                IconButton(onClick = {
                                    if (info.playing) controller?.pause() else controller?.play()
                                    controlsVisible = true
                                }, modifier = Modifier.size(64.dp)) {
                                    Icon(if (info.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (info.playing) "Пауза" else "Воспроизвести",
                                        modifier = Modifier.size(45.dp), tint = Color.White)
                                }
                                Spacer(Modifier.size(24.dp))
                                IconButton(onClick = {
                                    controller?.seekTo(if (info.duration > 0L)
                                        (info.position + 10_000L).coerceAtMost(info.duration)
                                        else info.position + 10_000L)
                                    controlsVisible = true
                                }, modifier = Modifier.size(56.dp)) {
                                    Icon(Icons.Default.FastForward, "Вперёд на 10 секунд",
                                        modifier = Modifier.size(30.dp), tint = Color.White)
                                }
                            }
                            Slider(
                                value = slider,
                                onValueChange = { slider = it; dragging = true },
                                onValueChangeFinished = {
                                    if (info.duration > 0L) controller?.seekTo((slider * info.duration).toLong())
                                    dragging = false
                                },
                                enabled = controller != null && info.duration > 0L,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(videoTime(if (dragging) (slider * info.duration).toLong() else info.position),
                                    color = Color.White, fontSize = 12.sp)
                                Text(videoTime(info.duration), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun videoTime(valueMs: Long): String {
    val total = valueMs.coerceAtLeast(0L) / 1000L
    return if (total >= 3600L) String.format(Locale.US, "%d:%02d:%02d", total / 3600L,
        (total % 3600L) / 60L, total % 60L)
    else String.format(Locale.US, "%d:%02d", total / 60L, total % 60L)
}
