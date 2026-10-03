package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA contrast for the branded light and dark palettes: 4.5:1 for text
 * pairs, 3:1 for large/bold text and UI parts. (Dynamic color comes from the
 * system and is Material's responsibility.)
 */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun ColorScheme.textPairs() = mapOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onErrorContainer/errorContainer" to (onErrorContainer to errorContainer),
        "onError/error" to (onError to error),
        "onSurface/surface" to (onSurface to surface),
        "onSurfaceVariant/surface" to (onSurfaceVariant to surface),
        "onSurfaceVariant/surfaceContainerLow" to (onSurfaceVariant to surfaceContainerLow),
        "onSurface/surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
        // Exercise names and section headers are primary-coloured text on cards.
        "primary/surfaceContainerLow" to (primary to surfaceContainerLow),
        "error/surfaceContainerLow" to (error to surfaceContainerLow),
        "tertiary/surfaceContainerLow" to (tertiary to surfaceContainerLow),
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failures = scheme.textPairs().mapNotNull { (pair, colors) ->
            val r = ratio(colors.first, colors.second)
            if (r < 4.5) "$name $pair = ${"%.2f".format(r)}" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // Outline borders (unselected chips, text fields) need 3:1 against the surface.
        assertTrue("$name outline", ratio(scheme.outline, scheme.surface) >= 3.0)
    }

    @Test
    fun lightPaletteMeetsAa() = check("light", LightColors)

    @Test
    fun darkPaletteMeetsAa() = check("dark", DarkColors)
}
