package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.theme.isDark
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole app inside a platform window that has nothing else to set up: the theme from the
 * user's preferences, then [SpotterApp]. Nothing shows until the preferences load, so an
 * explicit light/dark choice never flashes. iOS hosts this; Android's MainActivity does the
 * same itself, plus its splash screen and system-bar colours.
 */
@Composable
fun SpotterRoot(viewModel: MainViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = (uiState as? MainUiState.Ready)?.preferences ?: return
    SpotterTheme(darkTheme = preferences.themeMode.isDark(), dynamicColor = preferences.dynamicColor) {
        SpotterApp(needsOnboarding = !preferences.onboardingCompleted)
    }
}
