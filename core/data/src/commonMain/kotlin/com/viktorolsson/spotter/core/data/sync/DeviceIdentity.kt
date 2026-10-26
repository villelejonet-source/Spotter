package com.viktorolsson.spotter.core.data.sync

import androidx.room.useReaderConnection
import com.russhwolf.settings.Settings
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** This build's versionCode; the app module provides it from BuildConfig. */
data class AppBuild(val versionCode: Int)

/**
 * What this install reports after each sync ([DeviceCheckIn]). The id is a random UUID made on
 * first use and kept in [store]: on Android plain SharedPreferences, which are left out of both
 * cloud backup and device-to-device transfer (`data_extraction_rules.xml`), so a new phone gets
 * its own id. It survives sign-out and switching accounts; clearing the app's storage makes a
 * new one. [store] is opened off the main thread, on first check-in.
 */
class DeviceIdentity(
    private val store: () -> Settings,
    private val build: AppBuild,
    private val db: SpotterDatabase,
) {
    suspend fun checkIn(): DeviceCheckIn = withContext(Dispatchers.IO) {
        DeviceCheckIn(
            deviceId = deviceId(store()),
            versionCode = build.versionCode,
            schemaVersion = db.useReaderConnection { connection ->
                connection.usePrepared("PRAGMA user_version") { it.step(); it.getLong(0).toInt() }
            },
        )
    }

    companion object {
        /** The SharedPreferences file (Android) or defaults suite (iOS) holding the id. */
        const val FILE = "spotter_device"
        private const val KEY = "device_id"

        /** The stored id, or a new random one saved first. */
        internal fun deviceId(settings: Settings): String =
            settings.getStringOrNull(KEY) ?: Uuid.random().toString().also { settings.putString(KEY, it) }
    }
}
