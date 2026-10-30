package com.viktorolsson.spotter.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.di.dataModule
import com.viktorolsson.spotter.core.data.iosDataGraph
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.shell.SpotterRoot
import com.viktorolsson.spotter.shell.spotterModules
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * What the Swift app talks to; one per process. It starts Koin with the shared data layer and
 * every screen's view models, as Android's Application does, and hands Swift the shared UI.
 */
class SpotterIos(supabaseUrl: String, supabasePublishableKey: String, versionCode: Int) {
    private val graph: DataGraph = startKoin {
        modules(spotterModules(dataModule(iosDataGraph(SupabaseConfig(supabaseUrl, supabasePublishableKey), versionCode))))
    }.koin.get()

    /** Once at launch, as Android's Application does: see [DataGraph.onAppStart]. */
    fun start() = graph.onAppStart()

    /** The whole app, for the window's root view. */
    fun mainViewController(): UIViewController = ComposeUIViewController { SpotterRoot() }

    /** A URL the app was opened with; only `spotter://login?code=…` sign-in links are acted on. */
    fun openUrl(url: String) = graph.sync.handleDeeplink(url)

    /** The app came to the foreground: pick up changes made elsewhere (at most once a minute). */
    fun onForeground() = graph.sync.syncOnOpen()

    /** False when this build has no Supabase project configured; the app works offline. */
    val cloudSyncAvailable: Boolean get() = graph.accounts.isAvailable
}
