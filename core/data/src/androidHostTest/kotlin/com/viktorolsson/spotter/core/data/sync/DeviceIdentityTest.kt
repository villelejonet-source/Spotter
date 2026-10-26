package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.SharedPreferencesSettings
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.sql
import com.viktorolsson.spotter.core.data.testDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The check-in is a random install id that stays put, the versionCode and the schema version. */
@RunWith(AndroidJUnit4::class)
class DeviceIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = testDatabase()

    private fun deviceSettings() = SharedPreferencesSettings(context.getSharedPreferences(DeviceIdentity.FILE, Context.MODE_PRIVATE))

    @After
    fun tearDown() = db.close()

    @Test
    fun reportsTheBuildAndTheOpenSchema() = runTest {
        val checkIn = DeviceIdentity(::deviceSettings, AppBuild(versionCode = 7), db).checkIn()
        assertEquals(7, checkIn.versionCode)
        assertEquals(db.sql.version, checkIn.schemaVersion)
        UUID.fromString(checkIn.deviceId) // the server takes a UUID
    }

    @Test
    fun theIdIsMadeOnceAndKept() = runTest {
        val first = DeviceIdentity(::deviceSettings, AppBuild(7), db).checkIn().deviceId
        val again = DeviceIdentity(::deviceSettings, AppBuild(8), db).checkIn().deviceId
        assertEquals(first, again)

        // Cleared storage (or another phone) means a new install id.
        context.getSharedPreferences(DeviceIdentity.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        assertNotEquals(first, DeviceIdentity(::deviceSettings, AppBuild(8), db).checkIn().deviceId)
    }
}
