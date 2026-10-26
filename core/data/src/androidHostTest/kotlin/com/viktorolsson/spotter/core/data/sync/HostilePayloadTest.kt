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
import com.viktorolsson.spotter.core.data.sql
import com.viktorolsson.spotter.core.data.testDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Instant

/** Pulled rows come from the server as-is; none of them may touch rows they don't own. */
@RunWith(AndroidJUnit4::class)
class HostilePayloadTest {
    private lateinit var db: SpotterDatabase
    private lateinit var store: LocalSyncStore

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        db.exerciseDao().upsertAll(parseExerciseSeed(File("catalog/exercises.json").readText()).exercises.map(SeedExercise::toEntity))
        store = LocalSyncStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun scalar(sql: String): String? = db.sql.query(sql).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(
        pairs.associate { (k, v) ->
            k to when (v) {
                null -> JsonNull
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        },
    )

    private fun exerciseRow(syncId: String, vararg extra: Pair<String, Any?>) = SyncRow(
        "exercise", syncId,
        obj(
            "id" to syncId, "name" to "Evil", "primaryMuscles" to "CHEST", "secondaryMuscles" to "", "movementPattern" to "HORIZONTAL_PUSH",
            "equipment" to "", "mechanics" to "COMPOUND", "difficulty" to "BEGINNER", "unilateral" to 0, "instructions" to null, "isCustom" to 1,
            *extra,
        ),
        Long.MAX_VALUE,
    )

    @Test
    fun aRemoteExerciseCannotOverwriteASeededOne() = runTest {
        assertEquals(0, store.apply(listOf(exerciseRow("barbell-bench-press"))))
        assertEquals("Barbell Bench Press", scalar("SELECT name FROM exercise WHERE id = 'barbell-bench-press'"))
        assertEquals("0", scalar("SELECT isCustom FROM exercise WHERE id = 'barbell-bench-press'"))
        // Nor can it smuggle a seeded id in under a custom sync id.
        assertEquals(0, store.apply(listOf(exerciseRow("custom-x", "id" to "barbell-bench-press"))))
        assertEquals("Barbell Bench Press", scalar("SELECT name FROM exercise WHERE id = 'barbell-bench-press'"))
    }

    @Test
    fun aRemoteExerciseMustBeCustom() = runTest {
        assertEquals(0, store.apply(listOf(exerciseRow("custom-1", "isCustom" to 0))))
        assertEquals(0, store.apply(listOf(exerciseRow("custom-1", "isCustom" to false))))
        assertNull(scalar("SELECT id FROM exercise WHERE id = 'custom-1'"))
        assertEquals(1, store.apply(listOf(exerciseRow("custom-1", "isCustom" to true))))
        assertEquals("Evil", scalar("SELECT name FROM exercise WHERE id = 'custom-1'"))
    }

    @Test
    fun localIdsInPayloadsAreIgnored() = runTest {
        // A real local row with id 1 (the trigger stamps it).
        val workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(db), MutableClock(Instant.parse("2026-10-03T10:00:00Z")))
        val own = workouts.startEmptyWorkout()
        assertEquals(1L, own)
        val remote = SyncRow(
            "workout_session", "remote-1",
            obj("id" to 1, "startedAt" to 5, "endedAt" to 6, "planDayId" to null, "notes" to "hijack", "perceivedDifficulty" to null, "isDeload" to 0),
            Long.MAX_VALUE,
        )
        assertEquals(1, store.apply(listOf(remote)))
        assertNull(scalar("SELECT notes FROM workout_session WHERE id = 1"))
        assertEquals("2", scalar("SELECT COUNT(*) FROM workout_session"))
        assertNotNull(scalar("SELECT id FROM workout_session WHERE syncId = 'remote-1' AND id <> 1"))
    }

    @Test
    fun theProfileIsAlwaysTheSingletonRow() = runTest {
        val profile = SyncRow(
            "user_profile", "user-profile",
            obj(
                "id" to 99, "sex" to "MALE", "heightCm" to 180.0, "birthDate" to 7000, "bodyWeightKg" to 80.0, "units" to "LB",
                "experience" to "NEW", "goal" to "STRENGTH", "daysPerWeek" to 3, "sessionLengthMinutes" to 45,
                "equipment" to "", "focusAreas" to "", "limitations" to "",
            ),
            5L,
        )
        assertEquals(1, store.apply(listOf(profile)))
        assertEquals("1", scalar("SELECT id FROM user_profile"))
        assertEquals("LB", scalar("SELECT units FROM user_profile WHERE id = 1"))
    }

    @Test
    fun syncColumnsComeFromTheRowNotThePayload() = runTest {
        val plan = SyncRow(
            "plan", "p",
            obj("name" to "P", "splitType" to "CUSTOM", "goal" to "STRENGTH", "createdAt" to 1, "isActive" to 0, "syncId" to "other", "updatedAt" to 1),
            5L,
        )
        assertEquals(1, store.apply(listOf(plan)))
        assertEquals("5", scalar("SELECT updatedAt FROM plan WHERE syncId = 'p'"))
        assertNull(scalar("SELECT id FROM plan WHERE syncId = 'other'"))
    }

    @Test
    fun aChildWhoseParentArrivesInALaterPageIsDeferredThenApplied() = runTest {
        val day = SyncRow("plan_day", "d", obj("planId" to "p", "position" to 0, "name" to "A"), 5L)
        val plan = SyncRow("plan", "p", obj("name" to "P", "splitType" to "CUSTOM", "goal" to "STRENGTH", "createdAt" to 1, "isActive" to 0), 5L)
        val first = store.applyAll(listOf(day))
        assertEquals(0, first.changed)
        assertEquals(listOf(day), first.deferred)
        store.apply(listOf(plan))
        assertEquals(1, store.apply(first.deferred))
        assertEquals(scalar("SELECT id FROM plan WHERE syncId = 'p'"), scalar("SELECT planId FROM plan_day WHERE syncId = 'd'"))
    }
}
