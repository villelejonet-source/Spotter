package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.SplitType
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

@RunWith(AndroidJUnit4::class)
class RecommendationRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var plans: PlanRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var recommendations: RecommendationRepository
    private lateinit var prefs: UserPreferencesRepository
    private val clock = MutableClock(Instant.parse("2026-08-01T10:00:00Z"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        prefs = testPreferences()
        plans = PlanRepository(db, db.planDao())
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), prefs, clock)
        val profiles = UserProfileRepository(db.userProfileDao())
        recommendations = RecommendationRepository(
            db, db.recommendationDao(), db.workoutDao(), db.planDao(), db.exerciseDao(), profiles,
            HistoryRepository(db.workoutDao(), clock), prefs, clock,
        )
        plans.saveAsActive(fullBodyPlan())
    }

    @After
    fun tearDown() = db.close()

    /** Two full-body days so frequency is fine and the bench plateau is the only signal. */
    private suspend fun fullBodyPlan(): Plan {
        suspend fun ex(id: String, position: Int, sets: Int = 3) = PlanExercise(
            0, db.exerciseDao().getById(id)!!.toModel(), position, sets, 6, 12, 1, 120, ProgressionRule.DOUBLE_PROGRESSION, null, 60.0,
        )
        val day = listOf(
            ex("barbell-bench-press", 0), ex("barbell-row", 1), ex("back-squat", 2), ex("romanian-deadlift", 3),
            ex("overhead-press", 4), ex("barbell-hip-thrust", 5),
        )
        return Plan(0, "Test", SplitType.FULL_BODY, Goal.HYPERTROPHY, clock.instant(), listOf(PlanDay(0, 0, "Full A", day), PlanDay(0, 1, "Full B", day)))
    }

    /**
     * One planned workout where bench is 80 × [reps] on every set and everything else is
     * skipped, plus [extraChestSets] of pec deck so weekly chest volume can be on target.
     */
    private suspend fun stalledBenchWorkout(reps: Int = 6, extraChestSets: Int = 0) {
        val day = plans.observeNextDay().first()!!
        val sessionId = workouts.startPlannedWorkout(day.id)
        if (extraChestSets > 0) workouts.addExercises(sessionId, listOf("pec-deck"), defaultSetCount = extraChestSets)
        val session = workouts.getSession(sessionId)!!
        session.exercises.filter { it.exercise.id == "barbell-bench-press" }.flatMap { it.sets }.forEach {
            workouts.updateSetValues(it.id, 80.0, reps, null)
            workouts.toggleSetCompleted(it.id)
        }
        session.exercises.filter { it.exercise.id == "pec-deck" }.flatMap { it.sets }.forEach {
            workouts.updateSetValues(it.id, 40.0, 12, null)
            workouts.toggleSetCompleted(it.id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(session.id)
        clock.advanceSeconds(7 * 86_400L - 3600)
    }

    @Test
    fun stalledBenchWithTooLittleChestVolumeAddsSetsToBothBenchSlots() = runTest {
        repeat(4) { stalledBenchWorkout() } // 3 chest sets a week, target 10–20
        recommendations.refresh()
        val rec = recommendations.observeActive().first().single { it.exerciseId == "barbell-bench-press" }
        assertEquals(RecommendationType.ADD_VOLUME, rec.type)
        recommendations.apply(rec.id)
        val benchSets = plans.observeActivePlan().first()!!.days.map { day -> day.exercises.first { it.exercise.id == "barbell-bench-press" }.sets }
        assertEquals(listOf(4, 4), benchSets)
    }

    @Test
    fun stalledBenchGetsAnAccessoryThatApplyAddsToThePlan() = runTest {
        repeat(4) { stalledBenchWorkout(extraChestSets = 8) } // 11 chest sets a week: volume is fine
        recommendations.refresh()
        val active = recommendations.observeActive().first()
        val rec = active.single { it.exerciseId == "barbell-bench-press" }
        assertEquals(RecommendationType.ADD_ACCESSORY, rec.type)
        val action = rec.payload.action as RecommendationAction.AddExercise
        assertEquals("close-grip-bench-press", action.exerciseId)
        assertEquals(3, rec.payload.evidence.sessions)

        recommendations.apply(rec.id)
        val day = plans.observeActivePlan().first()!!.days.first { it.id == action.planDayId }
        assertEquals("close-grip-bench-press", day.exercises.last().exercise.id)
        assertTrue(recommendations.observeActive().first().none { it.id == rec.id })

        // Still stalled, but just applied: not suggested again during the cooldown.
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.exerciseId == "barbell-bench-press" })
    }

    @Test
    fun dismissedSuggestionsStayDismissedAndRefreshDoesNotDuplicate() = runTest {
        repeat(4) { stalledBenchWorkout(extraChestSets = 8) }
        recommendations.refresh()
        recommendations.refresh()
        val bench = recommendations.observeActive().first().filter { it.exerciseId == "barbell-bench-press" }
        assertEquals(1, bench.size)
        recommendations.dismiss(bench.single().id)
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.exerciseId == "barbell-bench-press" })
    }

    @Test
    fun deloadWeekLightensPlannedWorkoutsAndIsIgnoredByProgression() = runTest {
        stalledBenchWorkout(reps = 8)
        prefs.setDeloadUntil(java.time.LocalDate.now(clock).plusDays(6))
        val deloadDay = plans.observeNextDay().first()!!
        val deloadSession = workouts.getSession(workouts.startPlannedWorkout(deloadDay.id))!!
        val bench = deloadSession.exercises.first { it.exercise.id == "barbell-bench-press" }
        assertEquals(ProgressionReason.DELOAD, bench.progressionReason)
        assertEquals(2, bench.sets.size) // 60 % of 3 sets
        assertTrue(bench.sets.all { it.weightKg == 70.0 && it.reps == 6 }) // 90 % of 80 = 72, rounded down to a plate
        bench.sets.forEach { workouts.toggleSetCompleted(it.id) }
        workouts.finishWorkout(deloadSession.id)

        prefs.setDeloadUntil(null)
        val next = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id))!!
            .exercises.first { it.exercise.id == "barbell-bench-press" }
        // Progression continues from the 80 × 8 session, not the lighter deload one.
        assertEquals(ProgressionReason.ADD_REPS, next.progressionReason)
        assertTrue(next.sets.all { it.weightKg == 80.0 && it.reps == 9 })
    }

    @Test
    fun applyingADeloadStartsAWeekLongDeload() = runTest {
        // Six weeks of training since the start: a scheduled deload is suggested.
        repeat(6) { stalledBenchWorkout(reps = 6 + it) }
        recommendations.refresh()
        val deload = recommendations.observeActive().first().single { it.type == RecommendationType.DELOAD }
        recommendations.apply(deload.id)
        val until = prefs.preferences.first().deloadUntil!!
        assertEquals(java.time.LocalDate.now(clock).plusDays(6), until)

        // Once the week is over, another deload isn't suggested straight away.
        clock.advanceSeconds(8 * 86_400L)
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.type == RecommendationType.DELOAD })
    }

    @Test
    fun dismissingADeloadLetsPerLiftFixesThrough() = runTest {
        repeat(6) { stalledBenchWorkout(extraChestSets = 8) }
        recommendations.refresh()
        val deload = recommendations.observeActive().first().single { it.type == RecommendationType.DELOAD }
        assertTrue(recommendations.observeActive().first().none { it.exerciseId == "barbell-bench-press" })
        recommendations.dismiss(deload.id) // re-evaluates by itself
        val active = recommendations.observeActive().first()
        assertTrue(active.none { it.type == RecommendationType.DELOAD })
        assertEquals(RecommendationType.ADD_ACCESSORY, active.single { it.exerciseId == "barbell-bench-press" }.type)
    }
}
