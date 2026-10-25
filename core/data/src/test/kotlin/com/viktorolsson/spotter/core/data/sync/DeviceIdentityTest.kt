package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
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
    private val db = SpotterDatabase.configure(Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java))
        .allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    @Test
    fun reportsTheBuildAndTheOpenSchema() = runTest {
        val checkIn = DeviceIdentity(context, AppBuild(versionCode = 7), db).checkIn()
        assertEquals(7, checkIn.versionCode)
        assertEquals(db.openHelper.readableDatabase.version, checkIn.schemaVersion)
        UUID.fromString(checkIn.deviceId) // the server takes a UUID
    }

    @Test
    fun theIdIsMadeOnceAndKept() = runTest {
        val first = DeviceIdentity(context, AppBuild(7), db).checkIn().deviceId
        val again = DeviceIdentity(context, AppBuild(8), db).checkIn().deviceId
        assertEquals(first, again)

        // Cleared storage (or another phone) means a new install id.
        context.getSharedPreferences(DeviceIdentity.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        assertNotEquals(first, DeviceIdentity(context, AppBuild(8), db).checkIn().deviceId)
    }
}
