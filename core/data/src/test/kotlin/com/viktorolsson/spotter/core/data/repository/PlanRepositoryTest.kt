package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlanRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var plans: PlanRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: UserProfileRepository
    private val clock = MutableClock(Instant.parse("2026-10-03T10:00:00Z"))

    private val profile = UserProfile(
        sex = Sex.FEMALE, heightCm = 168.0, birthDate = LocalDate.of(1998, 1, 1), bodyWeightKg = 62.0,
        units = WeightUnit.KG, experience = ExperienceLevel.INTERMEDIATE, goal = Goal.HYPERTROPHY,
        daysPerWeek = 4, sessionLengthMinutes = 60, equipment = Equipment.entries.toSet(),
        focusAreas = setOf(BodyArea.GLUTES), limitations = emptySet(),
    )

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        plans = PlanRepository(db, db.planDao())
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), clock)
        profiles = UserProfileRepository(db.userProfileDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun generatedPlan(p: UserProfile = profile): Plan {
        val library = db.exerciseDao().observeAll().first().map { it.toModel() }
        return PlanGenerator().generate(PlanInput(p, library, LocalDate.of(2026, 10, 3), clock.instant())).plan
    }

    private suspend fun completeWorkout(sessionId: Long) {
        workouts.getSession(sessionId)!!.exercises.flatMap { it.sets }.forEach { workouts.toggleSetCompleted(it.id) }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(sessionId)
        clock.advanceSeconds(86_400)
    }

    @Test
    fun savedPlanRoundTripsExactly() = runTest {
        val generated = generatedPlan()
        plans.saveAsActive(generated)
        val saved = plans.observeActivePlan().first()!!
        fun Plan.shape() = days.map { d ->
            d.name to d.exercises.map { listOf(it.exercise.id, it.sets, it.repMin, it.repMax, it.targetRir, it.restSeconds, it.progressionRule, it.supersetGroup, it.startingWeightKg) }
        }
        assertEquals(generated.shape(), saved.shape())
        assertEquals(generated.splitType, saved.splitType)
    }

    @Test
    fun onlyTheNewestPlanIsActive() = runTest {
        plans.saveAsActive(generatedPlan())
        plans.saveAsActive(generatedPlan(profile.copy(daysPerWeek = 3)))
        assertEquals(3, plans.observeActivePlan().first()!!.days.size)
    }

    @Test
    fun nextDayRotatesThroughThePlanAndWraps() = runTest {
        plans.saveAsActive(generatedPlan())
        val order = mutableListOf<String>()
        repeat(5) {
            val day = plans.observeNextDay().first()!!
            order += day.name
            completeWorkout(workouts.startPlannedWorkout(day.id))
        }
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B", "Upper A"), order)
    }

    @Test
    fun plannedWorkoutUsesCalibrationFirstThenHistory() = runTest {
        plans.saveAsActive(generatedPlan())
        val day = plans.observeNextDay().first()!!
        val bench = day.exercises.first()
        assertNotNull(bench.startingWeightKg)

        val first = workouts.getSession(workouts.startPlannedWorkout(day.id))!!
        assertEquals("Upper A", first.planDayName)
        val firstBench = first.exercises.first()
        assertEquals(bench.sets, firstBench.sets.size)
        assertTrue(firstBench.sets.all { it.weightKg == bench.startingWeightKg && it.reps == bench.repMin })
        assertEquals(bench.restSeconds, firstBench.restSeconds)
        assertEquals(bench.repMax, firstBench.target!!.repMax)

        // Log heavier than calibration, finish, and the next Upper A starts from what was lifted.
        firstBench.sets.forEach { workouts.updateSetValues(it.id, 30.0, 10, null) }
        completeWorkout(first.id)
        val upperAId = day.id
        val second = workouts.getSession(workouts.startPlannedWorkout(upperAId))!!
        assertTrue(second.exercises.first().sets.all { it.weightKg == 30.0 && it.reps == 10 })
    }

    @Test
    fun startingAPlannedWorkoutResumesOneInProgress() = runTest {
        plans.saveAsActive(generatedPlan())
        val day = plans.observeNextDay().first()!!
        val id = workouts.startPlannedWorkout(day.id)
        assertEquals(id, workouts.startPlannedWorkout(day.id))
        assertEquals(id, workouts.startEmptyWorkout())
    }

    @Test
    fun profileRoundTrips() = runTest {
        profiles.save(profile)
        assertEquals(profile, profiles.get())
    }
}
