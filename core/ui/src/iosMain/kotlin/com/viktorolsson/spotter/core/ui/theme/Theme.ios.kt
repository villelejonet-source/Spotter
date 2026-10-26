package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual val supportsDynamicColor: Boolean = false

@Composable
internal actual fun dynamicColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkColors else LightColors
