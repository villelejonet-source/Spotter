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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(db), MutableClock(Instant.parse("2026-10-03T10:00:00Z")))
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

    @Test
    fun pulledRowsSkipFieldsThisVersionDoesNotKnow() {
        // A newer app or the website may send fields this schema lacks; the rest still applies.
        val plan = JsonObject(
            mapOf(
                "name" to JsonPrimitive("From the web"),
                "splitType" to JsonPrimitive("CUSTOM"),
                "goal" to JsonPrimitive("STRENGTH"),
                "createdAt" to JsonPrimitive(1L),
                "isActive" to JsonPrimitive(0),
                "colour" to JsonPrimitive("orange"),
            ),
        )
        val changed = LocalSyncStore(db).apply(listOf(SyncRow("plan", "web-plan", plan, 5L)))
        assertEquals(1, changed)
        assertEquals("From the web", scalar("SELECT name FROM plan WHERE syncId = 'web-plan'"))
    }

    private fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(
        pairs.associate { (k, v) ->
            k to when (v) {
                null -> kotlinx.serialization.json.JsonNull
                is Number -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        },
    )

    private fun planRow(id: String) =
        SyncRow("plan", id, obj("name" to "P", "splitType" to "CUSTOM", "goal" to "STRENGTH", "createdAt" to 1L, "isActive" to 0), 5L)

    private fun dayRow(id: String, plan: String) = SyncRow("plan_day", id, obj("planId" to plan, "position" to 0, "name" to id), 5L)

    private fun exerciseRow(id: String, day: String, exercise: String = "barbell-bench-press", at: Long = 5L) = SyncRow(
        "plan_exercise", id,
        obj(
            "planDayId" to day, "exerciseId" to exercise, "position" to 0, "sets" to 3, "repMin" to 8, "repMax" to 12,
            "targetRir" to 2, "restSeconds" to 90, "progressionRule" to "DOUBLE_PROGRESSION", "supersetGroup" to null, "startingWeightKg" to null,
        ),
        at,
    )

    @Test
    fun exerciseMovedOutOfADeletedDayKeepsItsRow() {
        val store = LocalSyncStore(db)
        store.apply(listOf(planRow("p"), dayRow("old", "p"), dayRow("new", "p"), exerciseRow("x", "old")))
        val localId = scalar("SELECT id FROM plan_exercise WHERE syncId = 'x'")
        // One pull: the exercise moves to "new" and "old" is deleted.
        store.apply(listOf(exerciseRow("x", "new", at = 9L), SyncRow("plan_day", "old", null, 9L, 9L)))
        assertEquals(localId, scalar("SELECT id FROM plan_exercise WHERE syncId = 'x'"))
        assertEquals(scalar("SELECT id FROM plan_day WHERE syncId = 'new'"), scalar("SELECT planDayId FROM plan_exercise WHERE syncId = 'x'"))
    }

    @Test
    fun rowsThisVersionCannotHoldAreSkippedNotFatal() {
        val changed = LocalSyncStore(db).apply(
            listOf(planRow("p"), dayRow("d", "p"), exerciseRow("future", "d", exercise = "exercise-from-a-newer-catalog"), exerciseRow("ok", "d")),
        )
        assertEquals(3, changed)
        assertEquals(null, scalar("SELECT id FROM plan_exercise WHERE syncId = 'future'"))
        assertNotNull(scalar("SELECT id FROM plan_exercise WHERE syncId = 'ok'"))
    }

    @Test
    fun sameDayBodyWeightFromTwoDevicesKeepsTheNewer() {
        val store = LocalSyncStore(db)
        val weight = { id: String, kg: Double, at: Long -> SyncRow("body_weight_entry", id, obj("date" to 20000L, "weightKg" to kg), at) }
        store.apply(listOf(weight("phone", 80.0, 10L)))
        store.apply(listOf(weight("web", 81.0, 20L))) // newer: replaces, phone's entry is tombstoned
        assertEquals(81.0, scalar("SELECT weightKg FROM body_weight_entry")!!.toDouble(), 0.0)
        assertEquals("1", scalar("SELECT COUNT(*) FROM body_weight_entry"))
        assertNotNull(scalar("SELECT deletedAt FROM sync_tombstone WHERE syncId = 'phone'"))
        store.apply(listOf(weight("older", 79.0, 15L))) // older: skipped and tombstoned
        assertEquals(81.0, scalar("SELECT weightKg FROM body_weight_entry")!!.toDouble(), 0.0)
        assertNotNull(scalar("SELECT deletedAt FROM sync_tombstone WHERE syncId = 'older'"))
    }
}

