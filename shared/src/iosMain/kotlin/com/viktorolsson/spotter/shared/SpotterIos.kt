package com.viktorolsson.spotter.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.di.dataModule
import com.viktorolsson.spotter.core.data.iosDataGraph
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.shell.SpotterRoot
import com.viktorolsson.spotter.shell.uiModules
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * What the Swift app talks to; one per process. It starts Koin with the shared data layer and
 * every screen's view models, as Android's Application does, and hands Swift the shared UI.
 */
class SpotterIos(supabaseUrl: String, supabasePublishableKey: String, versionCode: Int) {
    private val graph: DataGraph = startKoin {
        modules(listOf(dataModule(iosDataGraph(SupabaseConfig(supabaseUrl, supabasePublishableKey), versionCode))) + uiModules)
    }.koin.get()

    /** Seeding, one-off conversions and daily refreshes; each returns early when there's nothing to do. */
    fun start() {
        graph.scope.launch { graph.startupMaintenance.run() }
    }

    /** The whole app, for the window's root view. */
    fun mainViewController(): UIViewController = ComposeUIViewController { SpotterRoot() }

    /** A URL the app was opened with; only `spotter://login?code=…` sign-in links are acted on. */
    fun openUrl(url: String) = graph.sync.handleDeeplink(url)

    /** The app came to the foreground: pick up changes made elsewhere (at most once a minute). */
    fun onForeground() = graph.sync.syncOnOpen()

    /** False when this build has no Supabase project configured; the app works offline. */
    val cloudSyncAvailable: Boolean get() = graph.accounts.isAvailable
}
