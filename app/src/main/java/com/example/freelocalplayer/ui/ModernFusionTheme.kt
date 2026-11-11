package com.example.freelocalplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Palette IDs are stable because they are persisted in SharedPreferences. */
internal enum class FusionPalette(val label: String, val detail: String) {
    STUDIO_DARK("Studio Dark", "Тёмная профессиональная тема"),
    SOFT_LIGHT("Soft Light", "Светлая тема с высоким контрастом"),
    VIOLET_GLASS("Violet Glass", "Фиолетовые стеклянные акценты");

    companion object {
        fun fromStored(value: String?): FusionPalette =
            entries.firstOrNull { it.name == value } ?: STUDIO_DARK
    }
}

private val studioScheme = darkColorScheme(
    primary = Color(0xFFA9B9FF),
    onPrimary = Color(0xFF111D41),
    secondary = Color(0xFFC3CBDE),
    onSecondary = Color(0xFF242C39),
    background = Color(0xFF101318),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF20252D),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF303744),
    onSurfaceVariant = Color(0xFFCDD3DE),
    outline = Color(0xFF8693A8)
)
private val lightScheme = lightColorScheme(
    primary = Color(0xFF465FBE),
    onPrimary = Color.White,
    secondary = Color(0xFF4B5C81),
    onSecondary = Color.White,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF202532),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202532),
    surfaceVariant = Color(0xFFE6E9F1),
    onSurfaceVariant = Color(0xFF525C6D),
    outline = Color(0xFF6C7484)
)
private val violetScheme = darkColorScheme(
    primary = Color(0xFFC9B5FF),
    onPrimary = Color(0xFF261744),
    secondary = Color(0xFFD4C5EE),
    onSecondary = Color(0xFF302347),
    background = Color(0xFF171426),
    onBackground = Color(0xFFF5F0FF),
    surface = Color(0xFF2C2540),
    onSurface = Color(0xFFF5F0FF),
    surfaceVariant = Color(0xFF403653),
    onSurfaceVariant = Color(0xFFD5CCE8),
    outline = Color(0xFFA69ABC)
)

internal fun fusionColorScheme(palette: FusionPalette) = when (palette) {
    FusionPalette.STUDIO_DARK -> studioScheme
    FusionPalette.SOFT_LIGHT -> lightScheme
    FusionPalette.VIOLET_GLASS -> violetScheme
}

private val fusionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LocalFusionReducedMotion = compositionLocalOf { false }

@Composable
internal fun ModernFusionTheme(
    palette: FusionPalette,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFusionReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = fusionColorScheme(palette), shapes = fusionShapes, content = content)
    }
}

/** A readable translucent glass-style panel: no expensive real-time blur is required. */
@Composable
internal fun FusionGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
        ),
        content = content
    )
}

/** Subtle neumorphic shadow, but the button itself stays high-contrast. */
@Composable
internal fun FusionPlayPauseButton(
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 60.dp
) {
    val reducedMotion = LocalFusionReducedMotion.current
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(size).shadow(8.dp, CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Crossfade(
            targetState = playing,
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 160),
            label = "play-pause"
        ) { isPlaying ->
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}

@Composable
internal fun FusionSettingsDialog(
    selected: FusionPalette,
    reducedMotion: Boolean,
    onPaletteSelected: (FusionPalette) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внешний вид FreeLocalPlayer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FusionPalette.entries.forEach { palette ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selected == palette,
                            onClick = { onPaletteSelected(palette) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(palette.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                palette.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Уменьшить анимации")
                        Text(
                            "Мгновенно переключать декоративные эффекты",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = reducedMotion, onCheckedChange = onReducedMotionChanged)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}
