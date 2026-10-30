package com.viktorolsson.spotter

import android.app.Application
import androidx.work.Configuration
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.di.appModules
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
        get<DataGraph>().onAppStart()
    }
}
