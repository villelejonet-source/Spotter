package com.viktorolsson.spotter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.shell.MainUiState
import com.viktorolsson.spotter.shell.MainViewModel
import com.viktorolsson.spotter.shell.SpotterRoot
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    private val syncRepository: SyncRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until preferences load, so an explicit light/dark choice never flashes.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) syncRepository.handleDeeplink(intent.data?.toString())

        setContent {
            // The same root as iOS; Android adds only system bars that follow the theme.
            SpotterRoot(viewModel) { darkTheme ->
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
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncRepository.syncOnOpen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        syncRepository.handleDeeplink(intent.data?.toString())
    }

    private companion object {
        val LightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
