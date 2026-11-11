package com.example.freelocalplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
