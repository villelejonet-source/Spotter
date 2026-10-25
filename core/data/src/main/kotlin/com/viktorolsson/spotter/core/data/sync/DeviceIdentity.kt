package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** This build's versionCode; the app module provides it from BuildConfig. */
data class AppBuild(val versionCode: Int)

/**
 * What this install reports after each sync ([DeviceCheckIn]). The id is a random UUID made on
 * first use and kept in plain SharedPreferences, which are left out of both cloud backup and
 * device-to-device transfer (`data_extraction_rules.xml`), so a new phone gets its own id. It
 * survives sign-out and switching accounts; clearing the app's storage makes a new one.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val build: AppBuild,
    private val db: SpotterDatabase,
) {
    suspend fun checkIn(): DeviceCheckIn = withContext(Dispatchers.IO) {
        DeviceCheckIn(
            deviceId = deviceId(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)),
            versionCode = build.versionCode,
            schemaVersion = db.openHelper.readableDatabase.version,
        )
    }

    internal companion object {
        const val FILE = "spotter_device"
        private const val KEY = "device_id"

        /** The stored id, or a new random one saved first. */
        fun deviceId(prefs: SharedPreferences): String =
            prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).commit() }
    }
}
