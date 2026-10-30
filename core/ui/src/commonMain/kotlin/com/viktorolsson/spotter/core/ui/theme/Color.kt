package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The brand palette, shared with the website (`web/src/app/globals.css`): dark-first, a near-black
// canvas with the ember accent. The website's translucent surfaces are flattened here onto the
// background, since Material components expect opaque containers. Roles map as:
//   background / surface        canvas                  surfaceContainerLow     cards (.panel)
//   surfaceContainerHigh        stat tiles, chips       surfaceContainerHighest tracks, pressed
//   onSurfaceVariant            muted text              outlineVariant          hairlines, card borders
//   outline                     input borders           primaryContainer        accent tint

internal val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF8A4B2E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBE7DC),
    onSecondaryContainer = Color(0xFF5C2410),
    tertiary = Color(0xFF8A6A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBF0C9),
    onTertiaryContainer = Color(0xFF4A3A05),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFFAF8F6),
    onBackground = Color(0xFF1C1512),
    surface = Color(0xFFFAF8F6),
    onSurface = Color(0xFF1C1512),
    surfaceVariant = Color(0xFFEFECEA),
    onSurfaceVariant = Color(0xFF6B5D56),
    surfaceTint = Color(0xFFFAF8F6),
    outline = Color(0xFF8F857F),
    outlineVariant = Color(0xFFE6E2DF),
    inverseSurface = Color(0xFF2A211D),
    inverseOnSurface = Color(0xFFF4ECE8),
    inversePrimary = Color(0xFFFF7A45),
    scrim = Color(0xFF1C1512),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEDE9E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF3F0EE),
    surfaceContainerHigh = Color(0xFFEFECEA),
    surfaceContainerHighest = Color(0xFFE9E5E2),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    // Dark text on the ember accent: white on it would miss contrast (2.6:1).
    onPrimary = Color(0xFF1C0A02),
    primaryContainer = Color(0xFF2C170E),
    onPrimaryContainer = Color(0xFFFFB38A),
    secondary = Color(0xFFE7A988),
    onSecondary = Color(0xFF2C1206),
    secondaryContainer = Color(0xFF2C170E),
    onSecondaryContainer = Color(0xFFFFB38A),
    tertiary = Color(0xFFE8C56A),
    onTertiary = Color(0xFF2A2105),
    tertiaryContainer = Color(0xFF272115),
    onTertiaryContainer = Color(0xFFF3DC9A),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF2B0605),
    errorContainer = Color(0xFF2C1613),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF0A0908),
    onBackground = Color(0xFFF4ECE8),
    surface = Color(0xFF0A0908),
    onSurface = Color(0xFFF4ECE8),
    surfaceVariant = Color(0xFF1B1816),
    onSurfaceVariant = Color(0xFFA39790),
    surfaceTint = Color(0xFF0A0908),
    outline = Color(0xFF6A615C),
    outlineVariant = Color(0xFF1F1C1B),
    inverseSurface = Color(0xFFF4ECE8),
    inverseOnSurface = Color(0xFF1C1512),
    inversePrimary = Color(0xFFC2410C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF26221F),
    surfaceDim = Color(0xFF0A0908),
    surfaceContainerLowest = Color(0xFF060505),
    surfaceContainerLow = Color(0xFF121010),
    surfaceContainer = Color(0xFF161312),
    surfaceContainerHigh = Color(0xFF1B1816),
    surfaceContainerHighest = Color(0xFF23201E),
)

/**
 * Colours Material's scheme has no role for, derived from the scheme in use so they follow
 * the wallpaper palette too when dynamic colour is on.
 */
@Immutable
data class SpotterColors(
    /** Improvements: a heavier set, a shorter rest, an up arrow on a stat. */
    val good: Color,
    /** Tertiary text: axis labels, captions under captions. */
    val faint: Color,
    /** The accent's glow, behind the primary button and the active tab. */
    val glow: Color,
    /** The second stop of the primary button's gradient. */
    val primaryStrong: Color,
)

internal fun spotterColors(scheme: ColorScheme, dark: Boolean) = SpotterColors(
    good = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
    faint = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.62f else 0.72f),
    glow = scheme.primary.copy(alpha = if (dark) 0.5f else 0.28f),
    primaryStrong = if (scheme.primary == DarkColors.primary || scheme.primary == LightColors.primary) {
        if (dark) Color(0xFFFF6B35) else Color(0xFFEA580C)
    } else {
        scheme.primary
    },
)

internal val LocalSpotterColors = staticCompositionLocalOf { spotterColors(DarkColors, dark = true) }
