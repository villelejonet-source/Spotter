package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.theme.isDark
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole app, on both platforms: the theme from the user's preferences, then [SpotterApp].
 * Nothing shows until the preferences load, so an explicit light/dark choice never flashes.
 * [onDarkTheme] lets the host style its own chrome to match (Android's system bars).
 */
@Composable
fun SpotterRoot(
    viewModel: MainViewModel = koinViewModel(),
    onDarkTheme: @Composable (darkTheme: Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = (uiState as? MainUiState.Ready)?.preferences ?: return
    val darkTheme = preferences.themeMode.isDark()
    onDarkTheme(darkTheme)
    SpotterTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColor) {
        SpotterApp(needsOnboarding = !preferences.onboardingCompleted)
    }
}
