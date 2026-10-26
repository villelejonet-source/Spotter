package com.viktorolsson.spotter.core.data

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.sync.LocalSyncStore
import com.viktorolsson.spotter.core.data.sync.SyncRow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The database on iOS (bundled SQLite): the schema, sync triggers, the shared transaction
 * helper and the sync store work the same as on Android, where the full suite runs.
 */
class IosDatabaseTest {
    private val db = SpotterDatabase.configure(Room.inMemoryDatabaseBuilder<SpotterDatabase>(), BundledSQLiteDriver()).build()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun localWritesAreStampedForSyncAndDeletionsLeaveTombstones() = runTest {
        val weights = BodyWeightRepository(db, db.userProfileDao())
        weights.log(LocalDate(2026, 10, 3), 80.0)

        val store = LocalSyncStore(db)
        val row = store.changesSince(0).single { it.tableName == "body_weight_entry" }
        assertEquals(32, row.syncId.length)
        assertTrue(row.updatedAt > 0)
        assertEquals(JsonPrimitive(80.0), row.payload!!["weightKg"])

        db.useWriterConnection { it.usePrepared("DELETE FROM body_weight_entry") { st -> st.step() } }
        val deleted = store.changesSince(0).single { it.tableName == "body_weight_entry" }
        assertEquals(row.syncId, deleted.syncId)
        assertTrue(deleted.deletedAt != null)
    }

    @Test
    fun pulledRowsAreApplied() = runTest {
        val store = LocalSyncStore(db)
        val pulled = SyncRow(
            tableName = "body_weight_entry",
            syncId = "remote-1",
            payload = buildJsonObject {
                put("date", JsonPrimitive(LocalDate(2026, 10, 4).toEpochDays()))
                put("weightKg", JsonPrimitive(81.5))
            },
            updatedAt = 100,
        )
        assertEquals(1, store.apply(listOf(pulled)))
        assertEquals(81.5, db.userProfileDao().getBodyWeight(LocalDate(2026, 10, 4))!!.weightKg)
        // Applied rows don't come back out as local changes.
        assertTrue(store.changesSince(100).none { it.syncId == "remote-1" })
    }
}
