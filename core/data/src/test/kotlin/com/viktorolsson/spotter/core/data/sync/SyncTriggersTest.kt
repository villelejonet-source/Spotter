package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.repository.testPreferences
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SyncTriggersTest {
    private lateinit var db: SpotterDatabase
    private lateinit var workouts: WorkoutRepository

    @Before
    fun setUp() = runTest {
        db = SpotterDatabase.configure(
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java),
        ).allowMainThreadQueries().build()
        db.exerciseDao().upsertAll(parseExerciseSeed(File("src/main/assets/exercises.json").readText()).exercises.map(SeedExercise::toEntity))
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(), MutableClock(Instant.parse("2026-10-03T10:00:00Z")))
    }

    @After
    fun tearDown() = db.close()

    private fun scalar(sql: String): String? = db.openHelper.readableDatabase.query(sql).use { if (it.moveToFirst()) it.getString(0) else null }

    @Test
    fun insertsGetSyncIdsAndUpdatesAreStamped() = runTest {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("barbell-bench-press"), defaultSetCount = 1)
        val syncId = scalar("SELECT syncId FROM workout_session WHERE id = $id")
        assertNotNull(syncId)
        val set = workouts.getSession(id)!!.exercises.single().sets.single()
        db.openHelper.writableDatabase.execSQL("UPDATE set_entry SET updatedAt = 1 WHERE id = ${set.id}")
        // A direct write is itself a change, so the trigger re-stamps it with "now".
        assertTrue(scalar("SELECT updatedAt FROM set_entry WHERE id = ${set.id}")!!.toLong() > 1)
        workouts.updateSetValues(set.id, 80.0, 8, null)
        assertTrue(scalar("SELECT updatedAt FROM set_entry WHERE id = ${set.id}")!!.toLong() > 1_700_000_000_000)
        // Seeded exercises stay out of sync; custom ones are keyed by their id.
        assertEquals(null, scalar("SELECT syncId FROM exercise WHERE id = 'barbell-bench-press'"))
    }

    @Test
    fun deletesCascadeIntoTombstones() = runTest {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("barbell-bench-press"), defaultSetCount = 3)
        workouts.discardWorkout(id) // session → exercises → sets, all cascaded
        assertEquals("1", scalar("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = 'workout_session'"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = 'session_exercise'"))
        assertEquals("3", scalar("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = 'set_entry'"))
    }

    @Test
    fun triggersStandDownWhileApplyingAPull() = runTest {
        val w = db.openHelper.writableDatabase
        w.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
        w.execSQL("INSERT INTO workout_session (startedAt, endedAt, planDayId, notes, perceivedDifficulty, isDeload, syncId, updatedAt) VALUES (0, 1, NULL, NULL, NULL, 0, 'remote-1', 42)")
        assertEquals("42", scalar("SELECT updatedAt FROM workout_session WHERE syncId = 'remote-1'"))
        w.execSQL("DELETE FROM workout_session WHERE syncId = 'remote-1'")
        assertEquals("0", scalar("SELECT COUNT(*) FROM sync_tombstone"))
        w.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
    }
}
