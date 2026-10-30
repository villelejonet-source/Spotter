package com.viktorolsson.spotter.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.DeviceIdentity
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/**
 * The data graph for the iOS app: the database and preferences in the app's Documents
 * directory, the session in the Keychain, and the catalog from the app bundle
 * (`exercises.json`, copied from `core/data/catalog` by the Xcode build). Syncs run in the
 * app's process when asked for (on opening, after a workout); scheduled background sync
 * (BGAppRefreshTask) comes later, see docs/platform/ios-port.md.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
fun iosDataGraph(supabaseConfig: SupabaseConfig, versionCode: Int): DataGraph {
    val documents = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
        ?.path ?: error("No Documents directory")
    val database = SpotterDatabase.configure(
        Room.databaseBuilder<SpotterDatabase>(name = "$documents/${SpotterDatabase.NAME}"),
        BundledSQLiteDriver(),
    ).build()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { "$documents/user_preferences.preferences_pb".toPath() })
    lateinit var graph: DataGraph
    val scheduler = object : SyncScheduler {
        override fun schedulePeriodic() = Unit

        override fun syncSoon() {
            graph.scope.launch { graph.sync.syncNow() }
        }
    }
    graph = DataGraph(
        database = database,
        dataStore = dataStore,
        supabaseConfig = supabaseConfig,
        // On this device only (never in backups or iCloud Keychain), and readable after the
        // first unlock so a background sync can use the session.
        sessionStore = {
            KeychainSettings(
                kSecAttrService to CFBridgingRetain("spotter_auth"),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
        },
        deviceStore = { NSUserDefaultsSettings(NSUserDefaults(suiteName = DeviceIdentity.FILE)) },
        appBuild = AppBuild(versionCode),
        catalog = {
            val path = NSBundle.mainBundle.pathForResource("exercises", "json") ?: error("exercises.json missing from the app bundle")
            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("Can't read exercises.json")
        },
        syncScheduler = scheduler,
    )
    return graph
}
