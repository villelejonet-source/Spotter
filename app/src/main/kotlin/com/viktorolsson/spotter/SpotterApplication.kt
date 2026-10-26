package com.viktorolsson.spotter

import android.app.Application
import androidx.work.Configuration
import com.viktorolsson.spotter.core.data.StartupMaintenance
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin

class SpotterApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SpotterApplication)
            modules(appModules(this@SpotterApplication))
        }
        // Made eagerly so it starts syncing as soon as a signed-in session is restored. The
        // Supabase client it needs is built lazily on IO, so this costs nothing on the main thread.
        get<SyncRepository>()
        // Seeding, one-off conversions and daily refreshes; each returns early when there's nothing to do.
        get<CoroutineScope>(ApplicationScope).launch { get<StartupMaintenance>().run() }
    }
}
