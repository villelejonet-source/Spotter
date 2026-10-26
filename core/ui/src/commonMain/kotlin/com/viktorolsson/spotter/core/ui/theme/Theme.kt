package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> dynamicColorScheme(darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = SpotterShapes,
        content = content,
    )
}
