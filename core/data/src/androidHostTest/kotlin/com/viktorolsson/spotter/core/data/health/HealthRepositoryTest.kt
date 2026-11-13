package com.viktorolsson.spotter.core.data.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.repository.testPreferences
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.sql
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A health store in memory: what's saved, how often, and switches for access and failure. */
class FakeHealthStore : HealthStore {
    var availability = HealthAvailability.AVAILABLE
    var canWrite = true
    var failing = false
    val saved = MutableStateFlow<Map<String, HealthWorkout>>(emptyMap())
    var writes = 0

    override suspend fun availability() = availability
    override suspend fun canWriteWorkouts() = canWrite
    override suspend fun writeWorkout(workout: HealthWorkout) {
        if (failing) error("Health store unavailable")
        writes++
        saved.update { it + (workout.id to workout) }
    }

    override suspend fun deleteWorkout(id: String) {
        if (failing) error("Health store unavailable")
        saved.update { it - id }
    }
}

@RunWith(AndroidJUnit4::class)
class HealthRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var health: HealthRepository
    private val store = FakeHealthStore()
    private val start = Instant.parse("2026-11-13T17:00:00Z")
    private val clock = MutableClock(start)

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        val seed = parseExerciseSeed(File("catalog/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        preferences = testPreferences(db)
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), preferences, clock)
        val profiles = UserProfileRepository(db.userProfileDao())
        profiles.save(
            UserProfile(
                sex = Sex.MALE, heightCm = 180.0, birthDate = LocalDate(1998, 1, 1), bodyWeightKg = 80.0, units = WeightUnit.KG,
                experience = ExperienceLevel.INTERMEDIATE, goal = Goal.HYPERTROPHY, daysPerWeek = 4, sessionLengthMinutes = 60,
                equipment = emptySet(), focusAreas = emptySet(), limitations = emptySet(),
            ),
        )
        health = HealthRepository(store, db.healthExportDao(), db.workoutDao(), profiles, preferences, clock, AppErrors())
    }

    @After
    fun tearDown() = db.close()

    /** A finished hour of bench press, [sets] sets; returns its id. */
    private suspend fun benchWorkout(sets: Int = 15): Long {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("barbell-bench-press"), defaultSetCount = sets)
        val setsToDo = workouts.getSession(id)!!.exercises.single().sets
        setsToDo.forEach {
            clock.advanceSeconds(3600L / (setsToDo.size + 1))
            workouts.updateSetValues(it.id, 80.0, 8, null)
            workouts.toggleSetCompleted(it.id)
        }
        clock.advanceSeconds(3600L / (setsToDo.size + 1))
        workouts.finishWorkout(id)
        clock.advanceSeconds(60)
        return id
    }

    private fun syncId(sessionId: Long): String =
        db.sql.query("SELECT syncId FROM workout_session WHERE id = $sessionId").use { it.moveToFirst(); it.getString(0)!! }

    private suspend fun reconcile() =
        health.reconcile(db.healthExportDao().observeFinishedSessions(preferences.preferences.first().healthExportSince!!).first())

    @Test
    fun aFinishedWorkoutIsSavedOnceWithItsTimeAndCalories() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()
        reconcile()

        val workout = store.saved.value.getValue(syncId(id))
        assertEquals(start, workout.start)
        assertEquals(start + 60.minutes, workout.end)
        // 28 years old, 1790 kcal a day at rest, an hour at moderate intensity.
        assertEquals(298, workout.activeKcal)
        assertNull(workout.title)
        assertEquals(1, store.writes)
    }

    /** Feel, effort and notes aren't in the health store, so rating the workout writes nothing new. */
    @Test
    fun ratingTheWorkoutAfterwardsDoesNotSaveItAgain() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()

        workouts.setSessionFeel(id, 4)
        workouts.setSessionEffort(id, 8)
        workouts.setSessionNotes(id, "Felt strong")
        reconcile()

        assertEquals(1, store.writes)
    }

    @Test
    fun aWorkoutWhoseSetsChangeIsSavedAgainAsANewerVersion() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()
        val first = store.saved.value.getValue(syncId(id))

        clock.advanceSeconds(60)
        db.sql.execSQL("UPDATE set_entry SET reps = 10 WHERE id = (SELECT MIN(id) FROM set_entry)")
        reconcile()

        assertEquals(2, store.writes)
        assertEquals(1, store.saved.value.size)
        assertTrue(store.saved.value.getValue(syncId(id)).version > first.version)
    }

    /** A sync can deliver a workout's session before its sets: it's saved once they're there, not half. */
    @Test
    fun aSyncedWorkoutIsSavedWhenItsSetsArrive() = runTest {
        health.setSavingWorkouts(true)
        clock.advanceSeconds(60)
        val ended = clock.now().toEpochMilliseconds() + 3_600_000
        db.sql.execSQL(
            "INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty) " +
                "VALUES (50, ${clock.now().toEpochMilliseconds()}, $ended, NULL, NULL, NULL)",
        )
        reconcile()
        assertTrue(store.saved.value.isEmpty())

        db.sql.execSQL(
            "INSERT INTO session_exercise (id, sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes) " +
                "VALUES (50, 50, 'barbell-bench-press', 0, NULL, NULL, NULL)",
        )
        db.sql.execSQL(
            "INSERT INTO set_entry (sessionExerciseId, position, weightKg, reps, rir, rpe, setType, completedAt, notes) " +
                "VALUES (50, 0, 80.0, 8, NULL, NULL, 'WORKING', ${ended - 60_000}, NULL)",
        )
        reconcile()

        assertEquals(setOf(syncId(50)), store.saved.value.keys)
    }

    @Test
    fun deletingAWorkoutRemovesItFromTheHealthStore() = runTest {
        health.setSavingWorkouts(true)
        val kept = benchWorkout()
        val deleted = benchWorkout()
        reconcile()
        assertEquals(2, store.saved.value.size)

        workouts.deleteFinishedWorkout(deleted)
        reconcile()

        assertEquals(setOf(syncId(kept)), store.saved.value.keys)
    }

    /** A delete made on the website or another phone arrives as a plain row delete, past the repository. */
    @Test
    fun aWorkoutDeletedBySyncIsRemovedToo() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()

        db.sql.execSQL("DELETE FROM workout_session WHERE id = $id")
        reconcile()

        assertTrue(store.saved.value.isEmpty())
    }

    @Test
    fun workoutsFromBeforeItWasTurnedOnStayOut() = runTest {
        benchWorkout()
        health.setSavingWorkouts(true)
        val after = benchWorkout()
        reconcile()

        assertEquals(setOf(syncId(after)), store.saved.value.keys)
    }

    /** Turning it off and on again must not take earlier workouts back out of the health store. */
    @Test
    fun turningItOffAndOnKeepsWhatWasSaved() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()

        health.setSavingWorkouts(false)
        health.setSavingWorkouts(true)
        reconcile()

        assertEquals(setOf(syncId(id)), store.saved.value.keys)
    }

    @Test
    fun nothingIsSavedWithoutAccess() = runTest {
        health.setSavingWorkouts(true)
        store.canWrite = false
        benchWorkout()
        reconcile()

        assertTrue(store.saved.value.isEmpty())
        health.refresh()
        assertFalse(health.state.first().savingWorkouts)
    }

    @Test
    fun aFailedSaveIsTriedAgain() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        store.failing = true
        assertTrue(runCatching { reconcile() }.isFailure)

        store.failing = false
        reconcile()

        assertEquals(setOf(syncId(id)), store.saved.value.keys)
    }

    @Test
    fun aWorkoutWithNothingTickedIsNotSaved() = runTest {
        health.setSavingWorkouts(true)
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("barbell-bench-press"))
        clock.advanceSeconds(600)
        workouts.finishWorkout(id)
        reconcile()

        assertTrue(store.saved.value.isEmpty())
    }

    @Test
    fun theStateFollowsTheSwitchAndTheStore() = runTest {
        health.refresh()
        assertEquals(HealthState(HealthAvailability.AVAILABLE, savingWorkouts = false), health.state.first())

        health.setSavingWorkouts(true)
        assertEquals(HealthState(HealthAvailability.AVAILABLE, savingWorkouts = true), health.state.first())

        health.setSavingWorkouts(false)
        assertFalse(health.state.first().savingWorkouts)
    }

    /** The whole loop as the app runs it: finish a workout and it turns up, delete it and it goes, a failure in between is survived. */
    @Test
    fun runningKeepsTheHealthStoreInStepByItself() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            scope.launch { health.run() }
            health.setSavingWorkouts(true)

            store.failing = true
            val first = benchWorkout()
            store.failing = false
            val second = benchWorkout()
            val both = setOf(syncId(first), syncId(second))
            withContext(Dispatchers.Default) { withTimeout(10.seconds) { store.saved.first { it.keys == both } } }

            workouts.deleteFinishedWorkout(first)
            withContext(Dispatchers.Default) { withTimeout(10.seconds) { store.saved.first { it.keys == setOf(syncId(second)) } } }
        } finally {
            scope.cancel()
        }
    }
}
