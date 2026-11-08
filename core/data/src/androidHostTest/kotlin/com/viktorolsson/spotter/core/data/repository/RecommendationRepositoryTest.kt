package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.entity.RecommendationEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.engine.FixKind
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Instant

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
        db = testDatabase()
        val seed = parseExerciseSeed(File("catalog/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        prefs = testPreferences(db)
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

    /** Two full-body days; the bench plateau is the only signal. */
    private suspend fun fullBodyPlan(): Plan {
        suspend fun ex(id: String, position: Int, sets: Int = 3) = PlanExercise(
            0, db.exerciseDao().getById(id)!!.toModel(), position, sets, 6, 12, 1, 120, ProgressionRule.DOUBLE_PROGRESSION, null, 60.0,
        )
        val day = listOf(
            ex("barbell-bench-press", 0), ex("barbell-row", 1), ex("back-squat", 2), ex("romanian-deadlift", 3),
            ex("overhead-press", 4), ex("barbell-hip-thrust", 5),
        )
        return Plan(0, "Test", SplitType.FULL_BODY, Goal.HYPERTROPHY, clock.now(), listOf(PlanDay(0, 0, "Full A", day), PlanDay(0, 1, "Full B", day)))
    }

    /**
     * One planned workout where bench is 80 × [reps] on every set and everything else is
     * skipped, plus [extraChestSets] of pec deck so weekly chest volume can be on target.
     */
    private suspend fun stalledBenchWorkout(reps: Int = 6, extraChestSets: Int = 0, daysApart: Long = 7) {
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
        clock.advanceSeconds(daysApart * 86_400L - 3600)
    }

    /** [sessions] stalled bench workouts every 4 days, with enough chest volume. */
    private suspend fun stallBench(sessions: Int) = repeat(sessions) { stalledBenchWorkout(extraChestSets = 8, daysApart = 4) }

    private suspend fun stalledBench() = recommendations.observeProgressCheck().first()!!.stalled.singleOrNull { it.plateau.slot.exercise.id == BENCH }

    @Test
    fun stalledLiftsWaitForSixWeeksAndTwelveSessionsOnThePlan() = runTest {
        stallBench(8)
        val early = recommendations.observeProgressCheck().first()!!
        assertEquals(8, early.age.sessions)
        assertFalse(early.age.ready)
        assertTrue(early.stalled.isEmpty())
        // And no card for bench either: stalled lifts no longer make cards of their own.
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.exerciseId == BENCH })

        stallBench(5)
        assertTrue(recommendations.observeProgressCheck().first()!!.age.ready)
        val bench = stalledBench()!!
        val adds = bench.options.mapNotNull { it.action as? RecommendationAction.AddExercise }
        assertEquals(listOf("incline-barbell-bench-press", "close-grip-bench-press"), adds.take(2).map { it.exerciseId })
        assertEquals(bench.plateau.slot.planDayId, adds.first().planDayId)
    }

    @Test
    fun applyingChoicesChangesThePlanAndTheLiftCoolsDown() = runTest {
        stallBench(13)
        val bench = stalledBench()!!
        val picks = bench.options.filter { it.kind == FixKind.SUPPORT }.take(2)
        recommendations.applyOptions(bench, picks)
        val day = plans.observeActivePlan().first()!!.days.first { it.id == bench.plateau.slot.planDayId }
        assertEquals(listOf("incline-barbell-bench-press", "close-grip-bench-press"), day.exercises.takeLast(2).map { it.exercise.id })
        assertNull(stalledBench())

        // Four weeks on and still stuck: something not tried yet comes first.
        stallBench(8)
        val again = stalledBench()!!
        assertNotEquals(FixKind.SUPPORT, again.options.first().kind)
    }

    @Test
    fun notNowHidesAStalledLiftForTheCooldown() = runTest {
        stallBench(13)
        recommendations.dismissStalled(stalledBench()!!)
        assertNull(stalledBench())
        clock.advanceSeconds(27 * 86_400L)
        assertNull(stalledBench())
        clock.advanceSeconds(2 * 86_400L)
        assertNotNull(stalledBench())
    }

    @Test
    fun theReviewComesOnceThePlanIsOldEnoughAndApplyingItChangesThePlanOnce() = runTest {
        stallBench(8)
        assertNull(recommendations.observePlanReview().first()) // four weeks in, but too early for this plan
        stallBench(5)
        val review = recommendations.observePlanReview().first()!!
        val bench = review.stalled.single { it.plateau.slot.exercise.id == BENCH }
        recommendations.applyPlanReview(bench.options.filter { it.kind == FixKind.SUPPORT }.take(2).map { bench to it })
        // Reviewed today, and bench is cooling down on Progress too.
        assertNull(recommendations.observePlanReview().first())
        assertNull(stalledBench())
    }

    @Test
    fun laterHidesThePlanReviewForAWeek() = runTest {
        stallBench(13)
        assertNotNull(recommendations.observePlanReview().first())
        recommendations.snoozePlanReview()
        assertNull(recommendations.observePlanReview().first())
        clock.advanceSeconds(6 * 86_400L)
        assertNull(recommendations.observePlanReview().first())
        clock.advanceSeconds(86_400L)
        assertNotNull(recommendations.observePlanReview().first())

        recommendations.keepPlan()
        assertNull(recommendations.observePlanReview().first())
    }

    @Test
    fun anOldFrequencyCardIsRemovedOnRefresh() = runTest {
        stalledBenchWorkout()
        db.recommendationDao().insert(
            listOf(
                RecommendationEntity(
                    type = RecommendationType.INCREASE_FREQUENCY,
                    exerciseId = null,
                    payload = RecommendationPayload(
                        RecommendationAction.AddExercise(1, "Full B", "pec-deck", "Pec Deck", 3, 10, 15, 2, 90),
                        RecommendationEvidence(group = MuscleGroup.CHEST),
                    ).encode(),
                    createdAt = clock.now(),
                    status = RecommendationStatus.ACTIVE,
                    dedupKey = "frequency|CHEST",
                ),
            ),
        )
        assertEquals(1, recommendations.observeActive().first().size)
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().isEmpty())
    }

    @Test
    fun deloadWeekLightensPlannedWorkoutsAndIsIgnoredByProgression() = runTest {
        stalledBenchWorkout(reps = 8)
        prefs.setDeloadUntil(clock.todayIn(TimeZone.currentSystemDefault()).plus(6, DateTimeUnit.DAY))
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
    fun lighterCheckInLightensOnlyThatSessionAndIsIgnoredByProgression() = runTest {
        stalledBenchWorkout(reps = 8)
        val lighterSession = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id, readiness = 2, lighter = true))!!
        assertTrue(lighterSession.isLighter)
        assertEquals(2, lighterSession.readiness)
        val bench = lighterSession.exercises.first { it.exercise.id == "barbell-bench-press" }
        assertEquals(ProgressionReason.DELOAD, bench.progressionReason)
        assertTrue(bench.sets.all { it.weightKg == 70.0 && it.reps == 6 })
        bench.sets.forEach { workouts.toggleSetCompleted(it.id) }
        workouts.finishWorkout(lighterSession.id)

        // No deload week was started, and the next session builds on 80 × 8 again.
        val next = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id))!!
        assertFalse(next.isLighter)
        val nextBench = next.exercises.first { it.exercise.id == "barbell-bench-press" }
        assertEquals(ProgressionReason.ADD_REPS, nextBench.progressionReason)
        assertTrue(nextBench.sets.all { it.weightKg == 80.0 && it.reps == 9 })
    }

    /** A planned workout with bench at 80 × [reps] on every set, started with the check-in [readiness]. */
    private suspend fun benchWorkout(reps: Int, readiness: Int? = null, lighter: Boolean? = null): Long {
        val sessionId = workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id, readiness, lighter)
        workouts.getSession(sessionId)!!.exercises.filter { it.exercise.id == "barbell-bench-press" }.flatMap { it.sets }.forEach {
            if (lighter != true) workouts.updateSetValues(it.id, 80.0, reps, null)
            workouts.toggleSetCompleted(it.id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(sessionId)
        clock.advanceSeconds(2 * 86_400L)
        return sessionId
    }

    private suspend fun nextBench() = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id))!!
        .exercises.first { it.exercise.id == "barbell-bench-press" }

    @Test
    fun aMissOnARoughDayDoesNotLowerTheNextTargets() = runTest {
        benchWorkout(reps = 8)
        benchWorkout(reps = 4, readiness = 2, lighter = false) // kept as planned on a low day, and missed
        val next = nextBench()
        // Built on the normal 80 × 8, as if the rough day hadn't happened.
        assertEquals(ProgressionReason.ADD_REPS, next.progressionReason)
        assertTrue(next.sets.all { it.weightKg == 80.0 && it.reps == 9 })
    }

    @Test
    fun aMissRatedLowAfterwardsAlsoCountsAsARoughDay() = runTest {
        benchWorkout(reps = 8)
        val rough = benchWorkout(reps = 4)
        workouts.setSessionFeel(rough, 1)
        assertEquals(ProgressionReason.ADD_REPS, nextBench().progressionReason)
    }

    @Test
    fun aSingleLighterDayIsNotADeloadWeek() = runTest {
        benchWorkout(reps = 8, readiness = 1, lighter = true)
        assertNull(db.workoutDao().getLastDeloadSession())
        // Two lighter sessions within a week are a deload week.
        val second = benchWorkout(reps = 8, readiness = 1, lighter = true)
        assertEquals(workouts.getSession(second)!!.startedAt, db.workoutDao().getLastDeloadSession())
    }

    @Test
    fun feelingLowThreeTimesSuggestsALighterWeek() = runTest {
        repeat(2) { benchWorkout(reps = 8, readiness = 2, lighter = false) }
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.type == RecommendationType.DELOAD })

        benchWorkout(reps = 8, readiness = 1, lighter = false)
        recommendations.refresh()
        val deload = recommendations.observeActive().first().single { it.type == RecommendationType.DELOAD }
        assertEquals(3, deload.payload.evidence.lowDays)
    }

    @Test
    fun keepingAsPlannedDuringADeloadWeekStartsANormalSession() = runTest {
        stalledBenchWorkout(reps = 8)
        prefs.setDeloadUntil(clock.todayIn(TimeZone.currentSystemDefault()).plus(6, DateTimeUnit.DAY))
        val session = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id, readiness = 4, lighter = false))!!
        assertFalse(session.isLighter)
        assertEquals(ProgressionReason.ADD_REPS, session.exercises.first { it.exercise.id == "barbell-bench-press" }.progressionReason)
    }

    @Test
    fun ratingAFinishedSessionStoresFeelAndEffort() = runTest {
        val sessionId = workouts.startEmptyWorkout(readiness = 5)
        workouts.finishWorkout(sessionId)
        workouts.setSessionFeel(sessionId, 4)
        workouts.setSessionEffort(sessionId, 12) // out of range is clamped to 10
        val rated = workouts.getSession(sessionId)!!
        assertEquals(5, rated.readiness)
        assertEquals(4, rated.feel)
        assertEquals(10, rated.effort)
        workouts.setSessionFeel(sessionId, null)
        assertNull(workouts.getSession(sessionId)!!.feel)
    }

    @Test
    fun applyingADeloadStartsAWeekLongDeload() = runTest {
        // Six weeks of training since the start: a scheduled deload is suggested.
        repeat(6) { stalledBenchWorkout(reps = 6 + it) }
        recommendations.refresh()
        val deload = recommendations.observeActive().first().single { it.type == RecommendationType.DELOAD }
        recommendations.apply(deload.id)
        val until = prefs.preferences.first().deloadUntil!!
        assertEquals(clock.todayIn(TimeZone.currentSystemDefault()).plus(6, DateTimeUnit.DAY), until)

        // Once the week is over, another deload isn't suggested straight away.
        clock.advanceSeconds(8 * 86_400L)
        recommendations.refresh()
        assertTrue(recommendations.observeActive().first().none { it.type == RecommendationType.DELOAD })
    }

    /** Just after midnight in Sweden, a deload whose last day was yesterday is over. */
    @Test
    fun aDeloadThatEndedYesterdayIsOverAfterMidnight() = runTest {
        inStockholm {
            stalledBenchWorkout(reps = 8)
            clock.advanceToHalfPastMidnightInStockholm()
            val today = clock.todayIn(TimeZone.currentSystemDefault()) // a day ahead of the UTC date
            prefs.setDeloadUntil(today.minus(1, DateTimeUnit.DAY))

            val session = workouts.getSession(workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id))!!
            val bench = session.exercises.first { it.exercise.id == "barbell-bench-press" }
            assertNotEquals(ProgressionReason.DELOAD, bench.progressionReason)
        }
    }

    /** A deload applied just after midnight in Sweden runs a week from the phone's date. */
    @Test
    fun aDeloadAppliedAfterMidnightStartsOnThePhonesDate() = runTest {
        inStockholm {
            repeat(6) { stalledBenchWorkout(reps = 6 + it) }
            clock.advanceToHalfPastMidnightInStockholm()
            recommendations.refresh()
            val deload = recommendations.observeActive().first().single { it.type == RecommendationType.DELOAD }
            recommendations.apply(deload.id)

            val today = clock.todayIn(TimeZone.currentSystemDefault())
            assertEquals(today.plus(6, DateTimeUnit.DAY), prefs.preferences.first().deloadUntil)
        }
    }

    private companion object {
        const val BENCH = "barbell-bench-press"
    }
}
