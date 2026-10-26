package com.viktorolsson.spotter.shared

import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.iosDataGraph
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Swift app talks to; one per process. The screens move to Compose Multiplatform
 * in phase 4 (docs/platform/ios-port.md); until then this proves the data layer runs.
 */
class SpotterIos(supabaseUrl: String, supabasePublishableKey: String, versionCode: Int) {
    private val graph: DataGraph = iosDataGraph(SupabaseConfig(supabaseUrl, supabasePublishableKey), versionCode)

    /** Seeds the catalog and runs start-up maintenance, then reports the library size. */
    fun start(onReady: (exerciseCount: Int) -> Unit) {
        graph.scope.launch {
            graph.startupMaintenance.run()
            val count = graph.exercises.observeAll().first().size
            withContext(Dispatchers.Main) { onReady(count) }
        }
    }

    /** A URL the app was opened with; only `spotter://login?code=…` sign-in links are acted on. */
    fun openUrl(url: String) = graph.sync.handleDeeplink(url)

    /** The app came to the foreground: pick up changes made elsewhere (at most once a minute). */
    fun onForeground() = graph.sync.syncOnOpen()

    /** False when this build has no Supabase project configured; the app works offline. */
    val cloudSyncAvailable: Boolean get() = graph.accounts.isAvailable
}
