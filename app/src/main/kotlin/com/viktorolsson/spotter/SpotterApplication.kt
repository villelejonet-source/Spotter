package com.viktorolsson.spotter

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.viktorolsson.spotter.core.data.StartupMaintenance
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpotterApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Injected eagerly so it starts syncing as soon as a signed-in session is restored. The
    // Supabase client it needs is built lazily on IO, so this costs nothing on the main thread.
    @Inject lateinit var syncRepository: SyncRepository

    /** Seeding, one-off conversions and daily refreshes; each returns early when there's nothing to do. */
    @Inject lateinit var maintenance: dagger.Lazy<StartupMaintenance>

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { maintenance.get().run() }
    }
}
