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
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlinx.coroutines.withTimeoutOrNull
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
    @Volatile var failing = false
    /** Ids the store refuses, however often they're tried. */
    var refused = emptySet<String>()
    val saved = MutableStateFlow<Map<String, HealthWorkout>>(emptyMap())
    var writes = 0

    override suspend fun availability() = availability
    override suspend fun canWriteWorkouts() = canWrite
    override suspend fun writeWorkout(workout: HealthWorkout) {
        if (failing || workout.id in refused) error("Health store unavailable")
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
    private var restoring = false

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
        health = HealthRepository(store, db.healthExportDao(), db.workoutDao(), profiles, preferences, clock, AppErrors(), restoring = { restoring })
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
        reconcile()
        assertTrue(store.saved.value.isEmpty())

        store.failing = false
        reconcile()

        assertEquals(setOf(syncId(id)), store.saved.value.keys)
    }

    /** One workout the health store keeps refusing must not stop the others, or the deletes. */
    @Test
    fun aRefusedWorkoutDoesNotHoldUpTheRest() = runTest {
        health.setSavingWorkouts(true)
        val gone = benchWorkout()
        reconcile()
        val refused = benchWorkout()
        val fine = benchWorkout()
        store.refused = setOf(syncId(refused))
        workouts.deleteFinishedWorkout(gone)
        reconcile()

        assertEquals(setOf(syncId(fine)), store.saved.value.keys)

        store.refused = emptySet()
        reconcile()
        assertEquals(setOf(syncId(refused), syncId(fine)), store.saved.value.keys)
    }

    /**
     * Restoring a backup empties the workouts and pulls the same ones back. That must not take
     * them out of the health store: they ended before the switch's "since" and would never return.
     */
    @Test
    fun restoringABackupLeavesSavedWorkoutsInTheHealthStore() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()
        val saved = syncId(id)
        val rows = db.sql.query("SELECT startedAt, endedAt, updatedAt FROM workout_session WHERE id = $id").use {
            it.moveToFirst(); Triple(it.getLong(0), it.getLong(1), it.getLong(2))
        }

        restoring = true
        db.sql.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
        db.sql.execSQL("DELETE FROM workout_session")
        reconcile()
        assertEquals(setOf(saved), store.saved.value.keys)

        // The session is back before its sets: still nothing is taken out.
        db.sql.execSQL(
            "INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty, syncId, updatedAt) " +
                "VALUES ($id, ${rows.first}, ${rows.second}, NULL, NULL, NULL, '$saved', ${rows.third})",
        )
        reconcile()
        assertEquals(setOf(saved), store.saved.value.keys)

        // Its sets follow, and the restore ends.
        db.sql.execSQL(
            "INSERT INTO session_exercise (id, sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes) " +
                "VALUES (90, $id, 'barbell-bench-press', 0, NULL, NULL, NULL)",
        )
        db.sql.execSQL(
            "INSERT INTO set_entry (sessionExerciseId, position, weightKg, reps, rir, rpe, setType, completedAt, notes) " +
                "VALUES (90, 0, 80.0, 8, NULL, NULL, 'WORKING', ${rows.second - 60_000}, NULL)",
        )
        db.sql.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
        restoring = false
        reconcile()

        assertEquals(setOf(saved), store.saved.value.keys)
    }

    /** A workout deleted while a restore was running is still removed once it's over. */
    @Test
    fun aDeleteDuringARestoreIsCaughtUpAfterwards() = runTest {
        health.setSavingWorkouts(true)
        val id = benchWorkout()
        reconcile()

        restoring = true
        workouts.deleteFinishedWorkout(id)
        reconcile()
        assertEquals(1, store.saved.value.size)

        restoring = false
        reconcile()
        assertTrue(store.saved.value.isEmpty())
    }

    /** A saved workout that later has nothing ticked (its sets were removed elsewhere) is taken back out. */
    @Test
    fun aSavedWorkoutThatLosesItsSetsIsRemoved() = runTest {
        health.setSavingWorkouts(true)
        benchWorkout()
        reconcile()

        db.sql.execSQL("DELETE FROM set_entry")
        reconcile()

        assertTrue(store.saved.value.isEmpty())
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
        var stopped: Throwable? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> stopped = e })
        try {
            val running = scope.launch { health.run() }
            // Says what it saw when the store never gets there, since this only fails on slower machines.
            suspend fun awaitSaved(ids: Set<String>) {
                withContext(Dispatchers.Default) { withTimeoutOrNull(30.seconds) { store.saved.first { it.keys == ids } } }
                    ?: error(
                        "Health store has ${store.saved.value.keys}, expected $ids. Recorded: ${db.healthExportDao().getAll()}. " +
                            "Watching: ${running.isActive}, stopped by: $stopped",
                    )
            }
            health.setSavingWorkouts(true)

            store.failing = true
            val first = benchWorkout()
            store.failing = false
            val second = benchWorkout()
            awaitSaved(setOf(syncId(first), syncId(second)))

            workouts.deleteFinishedWorkout(first)
            awaitSaved(setOf(syncId(second)))
        } finally {
            scope.cancel()
        }
    }
}
