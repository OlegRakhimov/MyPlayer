package com.example.freelocalplayer.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ModernFusionPaletteTest {
    @Test fun allStoredPalettesRoundTrip() {
        FusionPalette.entries.forEach { palette ->
            assertEquals(palette, FusionPalette.fromStored(palette.name))
        }
    }

    @Test fun unknownOrMissingPaletteFallsBackToReadableDark() {
        assertEquals(FusionPalette.STUDIO_DARK, FusionPalette.fromStored(null))
        assertEquals(FusionPalette.STUDIO_DARK, FusionPalette.fromStored("OLD_THEME"))
    }

    @Test fun allThemesHaveReadablePrimaryAndSecondaryText() {
        FusionPalette.entries.forEach { palette ->
            val scheme = fusionColorScheme(palette)
            assertContrast(palette, "onBackground", scheme.onBackground, scheme.background, 7.0)
            assertContrast(palette, "onSurface", scheme.onSurface, scheme.surface, 7.0)
            assertContrast(palette, "onSurfaceVariant", scheme.onSurfaceVariant, scheme.surface, 4.5)
            assertContrast(palette, "primary", scheme.primary, scheme.background, 4.5)
        }
    }

    @Test fun allThemesHaveVisibleBoundaries() {
        FusionPalette.entries.forEach { palette ->
            val scheme = fusionColorScheme(palette)
            assertContrast(palette, "outline", scheme.outline, scheme.background, 3.0)
        }
    }

    private fun assertContrast(
        palette: FusionPalette,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Double
    ) {
        val a = luminance(foreground)
        val b = luminance(background)
        val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        assertTrue("${palette.name} $role ratio $ratio below $minimum", ratio >= minimum)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val v = channel.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
