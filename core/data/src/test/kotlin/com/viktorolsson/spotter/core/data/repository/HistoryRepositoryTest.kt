package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var history: HistoryRepository
    private val clock = MutableClock(Instant.parse("2026-10-03T10:00:00Z"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(), clock)
        history = HistoryRepository(db.workoutDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    /** Logs one workout of [exerciseId] with the given sets (weight × reps) and finishes it. */
    private suspend fun logWorkout(exerciseId: String, vararg sets: Pair<Double?, Int>): Long {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf(exerciseId), defaultSetCount = sets.size)
        // With history, sets pre-fill to last time's count; top up to what this test logs.
        val exercise = workouts.getSession(id)!!.exercises.last()
        repeat(sets.size - exercise.sets.size) { workouts.addSet(exercise.id) }
        val logged = workouts.getSession(id)!!.exercises.last().sets
        sets.forEachIndexed { i, (w, r) ->
            workouts.updateSetValues(logged[i].id, w, r, null)
            workouts.toggleSetCompleted(logged[i].id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(id)
        clock.advanceSeconds(2 * 86_400)
        return id
    }

    @Test
    fun recordsAreSavedFromTheSecondSessionOn() = runTest {
        val first = logWorkout("barbell-bench-press", 80.0 to 8, 80.0 to 8)
        assertTrue(history.observeSessionRecords(first).first().isEmpty())

        val second = logWorkout("barbell-bench-press", 82.5 to 8, 80.0 to 9)
        val records = history.observeSessionRecords(second).first().associateBy { it.type }
        assertEquals(setOf(PersonalRecordType.ESTIMATED_1RM, PersonalRecordType.REPS_AT_WEIGHT, PersonalRecordType.VOLUME), records.keys)
        assertEquals("Barbell Bench Press", records.getValue(PersonalRecordType.ESTIMATED_1RM).exerciseName)
        assertEquals(82.5, records.getValue(PersonalRecordType.ESTIMATED_1RM).weightKg!!, 0.0)

        val third = logWorkout("barbell-bench-press", 70.0 to 8)
        assertTrue(history.observeSessionRecords(third).first().isEmpty())
        assertEquals(3, history.observeRecentRecords().first().size)
    }

    @Test
    fun exerciseLogsAndLoggedExercises() = runTest {
        logWorkout("barbell-bench-press", 80.0 to 8)
        logWorkout("back-squat", 100.0 to 5)
        logWorkout("barbell-bench-press", 82.5 to 6, 82.5 to 5)

        val logs = history.observeExerciseLogs("barbell-bench-press").first()
        assertEquals(2, logs.size)
        assertTrue(logs[0].date < logs[1].date)
        assertEquals(82.5 to 6, logs[1].bestSet!!.weightKg to logs[1].bestSet!!.reps)
        assertEquals(82.5 * 11, logs[1].volumeKg, 0.001)

        val logged = history.observeLoggedExercises().first()
        assertEquals(listOf("barbell-bench-press", "back-squat"), logged.map { it.exercise.id })
        assertEquals(2, logged.first().sessionCount)
        assertEquals(3, history.observeFinishedSessions().first().size)
    }

    @Test
    fun weeklyVolumeOnlyCountsTheLastSevenDays() = runTest {
        logWorkout("barbell-bench-press", 80.0 to 8, 80.0 to 8, 80.0 to 8)   // day 0
        clock.advanceSeconds(6 * 86_400)                                     // now day 8
        logWorkout("barbell-bench-press", 80.0 to 8, 80.0 to 8)              // day 8, counted
        val chest = history.observeWeeklyVolume(Goal.HYPERTROPHY).first().first { it.group == MuscleGroup.CHEST }
        assertEquals(2.0, chest.sets, 0.0)
        assertEquals(10, chest.targetLow)
    }

    @Test
    fun repeatWorkoutCopiesTheExercisesWithLastValues() = runTest {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("back-squat", "barbell-bench-press"), defaultSetCount = 1)
        workouts.getSession(id)!!.exercises.forEach { ex ->
            workouts.updateSetValues(ex.sets[0].id, 60.0, 10, null)
            workouts.toggleSetCompleted(ex.sets[0].id)
        }
        workouts.finishWorkout(id)

        val repeat = workouts.getSession(workouts.repeatWorkout(id))!!
        assertTrue(repeat.isActive)
        assertEquals(listOf("back-squat", "barbell-bench-press"), repeat.exercises.map { it.exercise.id })
        assertTrue(repeat.exercises.all { ex -> ex.sets.single().let { it.weightKg == 60.0 && it.reps == 10 && !it.isCompleted } })
    }

    @Test
    fun bodyWeightKeepsOneEntryPerDayAndUpdatesTheProfile() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(
                sex = Sex.MALE, heightCm = 180.0, birthDate = LocalDate.of(1990, 1, 1), bodyWeightKg = 85.0,
                units = WeightUnit.KG, experience = ExperienceLevel.INTERMEDIATE, goal = Goal.STRENGTH, daysPerWeek = 3,
                sessionLengthMinutes = 60, equipment = setOf(Equipment.BARBELL), focusAreas = emptySet(), limitations = emptySet(),
            ),
        )
        val repo = BodyWeightRepository(db, db.userProfileDao())
        val day = LocalDate.of(2026, 10, 1)
        repo.log(day, 84.0)
        repo.log(day, 83.5)
        repo.log(day.plusDays(2), 83.0)
        val trend = repo.observeTrend().first()
        assertEquals(listOf(83.5, 83.0), trend.map { it.weightKg })
        assertEquals(83.25, trend.last().sevenDayAverageKg, 0.001)
        assertEquals(83.0, db.userProfileDao().observe().first()!!.bodyWeightKg, 0.0)
    }
}
