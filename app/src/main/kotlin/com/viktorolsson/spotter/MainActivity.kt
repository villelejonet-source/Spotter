package com.viktorolsson.spotter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until preferences load, so an explicit light/dark choice never flashes.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) syncRepository.handleDeeplink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences = (uiState as? MainUiState.Ready)?.preferences ?: return@setContent
            val darkTheme = preferences.themeMode.isDark()

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            SpotterTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColor) {
                SpotterApp(needsOnboarding = !preferences.onboardingCompleted)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        syncRepository.handleDeeplink(intent)
    }

    private companion object {
        val LightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
