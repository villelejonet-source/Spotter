package com.viktorolsson.spotter.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.SharedPreferencesSettings
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.sync.AndroidSessionStore
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.DeviceIdentity
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncWorker
import com.viktorolsson.spotter.core.data.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * The shared [DataGraph], built from Android's storage, Keystore session store and WorkManager.
 * Make one per process (the app does, in `Application.onCreate`).
 */
fun androidDataGraph(context: Context, supabaseConfig: SupabaseConfig, appBuild: AppBuild): DataGraph = DataGraph(
    database = SpotterDatabase.configure(
        Room.databaseBuilder<SpotterDatabase>(context, context.getDatabasePath(SpotterDatabase.NAME).absolutePath),
        AndroidSQLiteDriver(),
    ).build(),
    dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") },
    clock = Clock.System,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    supabaseConfig = supabaseConfig,
    sessionStore = AndroidSessionStore(context),
    deviceStore = { SharedPreferencesSettings(context.getSharedPreferences(DeviceIdentity.FILE, Context.MODE_PRIVATE), commit = true) },
    appBuild = appBuild,
    catalog = { context.assets.open("exercises.json").bufferedReader().use { it.readText() } },
    syncScheduler = WorkManagerSyncScheduler(context),
)

/** [dataModule] for [graph], plus the WorkManager worker that syncs in the background. */
fun androidDataModule(graph: DataGraph): Module = module {
    includes(dataModule(graph))
    workerOf(::SyncWorker)
}
