package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.viktorolsson.spotter.core.model.ThemeMode

/** Whether the system offers a wallpaper-based palette: Android 12 and later; never on iOS. */
expect val supportsDynamicColor: Boolean

/** The system's wallpaper-based palette; only called when [supportsDynamicColor]. */
@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SpotterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> dynamicColorScheme(darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalSpotterColors provides spotterColors(colorScheme, darkTheme),
        LocalSpotterTypography provides spotterNumberTypography(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = spotterTypography(),
            shapes = SpotterShapes,
            content = content,
        )
    }
}

/** Spotter's additions to [MaterialTheme]: extra colours and the number styles. */
object SpotterTheme {
    val colors: SpotterColors
        @Composable @ReadOnlyComposable get() = LocalSpotterColors.current

    val type: SpotterTypography
        @Composable @ReadOnlyComposable get() = LocalSpotterTypography.current
}
