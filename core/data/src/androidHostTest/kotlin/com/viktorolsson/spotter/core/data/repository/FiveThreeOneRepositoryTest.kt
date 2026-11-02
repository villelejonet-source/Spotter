package com.viktorolsson.spotter.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Instant

/** A 5/3/1 lift through a cycle: each week's loads, the AMRAP set, and what finishing writes to the plan. */
@RunWith(AndroidJUnit4::class)
class FiveThreeOneRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var plans: PlanRepository
    private lateinit var workouts: WorkoutRepository
    private val clock = MutableClock(Instant.parse("2026-11-02T10:00:00Z"))

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        val seed = parseExerciseSeed(File("catalog/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        plans = PlanRepository(db, db.planDao())
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(db), clock)
        val bench = PlanExercise(
            0, db.exerciseDao().getById("barbell-bench-press")!!.toModel(), 0, 3, 5, 5, null, 180, ProgressionRule.LINEAR, null, null,
            trainingMaxKg = 100.0, cycleWeek = 1,
        )
        plans.saveAsActive(Plan(0, "5/3/1", SplitType.CUSTOM, Goal.STRENGTH, clock.now(), listOf(PlanDay(0, 0, "Bench day", listOf(bench)))))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun planBench() = plans.observeActivePlan().first()!!.days.single().exercises.single()

    /** Starts the bench day and does every set as planned, the AMRAP set for [amrapReps]. */
    private suspend fun doBench(amrapReps: Int? = null, lighter: Boolean? = null): SessionExercise {
        val sessionId = workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id, lighter = lighter)
        val bench = workouts.getSession(sessionId)!!.exercises.single()
        bench.sets.forEach { set ->
            if (set.isAmrap && amrapReps != null) workouts.updateSetValues(set.id, set.weightKg, amrapReps, null)
            workouts.toggleSetCompleted(set.id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(sessionId)
        clock.advanceSeconds(86_400)
        return bench
    }

    @Test
    fun aCycleMovesWeekByWeekAndRaisesTheTrainingMax() = runTest {
        val week1 = doBench(amrapReps = 8)
        assertEquals(1, week1.cycleWeek)
        assertNull(week1.progressionReason)
        assertEquals(listOf(65.0, 75.0, 85.0), week1.sets.map { it.weightKg })
        assertEquals(listOf(false, false, true), week1.sets.map { it.isAmrap })
        assertEquals(2, planBench().cycleWeek)

        assertEquals(listOf(70.0, 80.0, 90.0), doBench(amrapReps = 5).sets.map { it.weightKg })
        assertEquals(listOf(75.0, 85.0, 95.0), doBench(amrapReps = 3).sets.map { it.weightKg })
        assertEquals(listOf(40.0, 50.0, 60.0), doBench().sets.map { it.weightKg })

        assertEquals(102.5, planBench().trainingMaxKg!!, 0.001)
        assertEquals(1, planBench().cycleWeek)
    }

    @Test
    fun aLighterSessionDoesTheDeloadLoadsWithoutUsingUpTheWeek() = runTest {
        val lighter = doBench(lighter = true)
        assertEquals(listOf(40.0, 50.0, 60.0), lighter.sets.map { it.weightKg })
        assertEquals(1, planBench().cycleWeek)
        assertEquals(100.0, planBench().trainingMaxKg!!, 0.001)
    }

    @Test
    fun missingTheAmrapLowersTheTrainingMaxAndRepeatsTheWeek() = runTest {
        doBench(amrapReps = 3) // week 1 needs 5
        assertEquals(90.0, planBench().trainingMaxKg!!, 0.001)
        assertEquals(1, planBench().cycleWeek)
    }
}
