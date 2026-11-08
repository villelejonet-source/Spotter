package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.Fixtures.library
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanReviewTest {
    private val today = LocalDate(2026, 10, 31)

    private fun slot(id: String, planExerciseId: Long, dayId: Long = 1, dayName: String = "Upper A") =
        PlanSlot(planExerciseId, dayId, dayName, byId.getValue(id), 3, 6, 12, 1, ProgressionRule.DOUBLE_PROGRESSION)

    /** Weekly sessions over [weeks], ending today; [step] kg added each week (0 is a stall). */
    private fun weekly(start: Double, step: Double, weeks: Int = 5) = List(weeks) { i ->
        ExerciseExposure(today.minus((weeks - 1 - i).toLong(), DateTimeUnit.WEEK), List(3) { LoggedSet(start + i * step, 8) })
    }

    private val upper = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("overhead-press", 3))

    /** [sessions] spread every 3 days back from today; the plan started [planWeeks] ago. */
    private fun ctx(
        plan: List<PlanSlot>,
        history: Map<String, List<ExerciseExposure>>,
        sessions: Int = 14,
        planWeeks: Int = 7,
        equipment: Set<Equipment> = Fixtures.fullGym,
        goal: Goal? = null,
    ) = RecommendationContext(
        today = today,
        plan = plan,
        history = history,
        trainingDates = List(sessions) { today.minus(it * 3L, DateTimeUnit.DAY) },
        lastDeloadEnd = null,
        weeklyVolume = MuscleBalance.weekly(emptyList(), Goal.HYPERTROPHY).map { MuscleVolume(it.group, 12.0, it.targetLow, it.targetHigh) },
        recentRir = emptyList(),
        library = library,
        equipment = equipment,
        limitations = emptySet(),
        goal = goal,
        planStarted = today.minus(planWeeks.toLong(), DateTimeUnit.WEEK),
    )

    private val stalledBench = mapOf(
        "barbell-bench-press" to weekly(80.0, 0.0),
        "barbell-row" to weekly(60.0, 2.5),
        "overhead-press" to weekly(40.0, 1.25),
    )

    @Test
    fun `a stalled bench gets incline work - triceps and shoulders - none already in the plan`() {
        val c = ctx(upper, stalledBench)
        val supports = SupportingLifts.suggest(upper[0], c)
        assertEquals(listOf(SupportReason.UPPER_CHEST, SupportReason.TRICEPS, SupportReason.SHOULDERS), supports.map { it.reason })
        assertEquals("incline-barbell-bench-press", supports[0].exercise.id)
        assertEquals("close-grip-bench-press", supports[1].exercise.id)
        // Overhead press is already in the plan, so the shoulder role takes the next one.
        assertTrue(supports.none { it.exercise.id in upper.map { s -> s.exercise.id } })
    }

    @Test
    fun `supporting exercises fit the equipment and every named one exists`() {
        val dumbbells = ctx(upper, stalledBench, equipment = setOf(Equipment.DUMBBELL, Equipment.BENCH))
        val supports = SupportingLifts.suggest(upper[0], dumbbells)
        assertEquals("incline-dumbbell-press", supports.first().exercise.id)
        assertTrue(supports.all { dumbbells.equipment.containsAll(it.exercise.equipment) })
        assertEquals(emptySet(), SupportingLifts.namedExerciseIds - byId.keys)
        assertEquals(supports, SupportingLifts.suggest(upper[0], dumbbells))
    }

    @Test
    fun `nothing is suggested before six weeks and twelve sessions on the plan`() {
        assertEquals(PlanAge(sessions = 14, weeks = 7), PlanReviewEngine.planAge(ctx(upper, stalledBench)))
        assertTrue(PlanReviewEngine.stalledLifts(ctx(upper, stalledBench, planWeeks = 5)).isEmpty())
        assertTrue(PlanReviewEngine.stalledLifts(ctx(upper, stalledBench, sessions = 11)).isEmpty())
        assertEquals(listOf("barbell-bench-press"), PlanReviewEngine.stalledLifts(ctx(upper, stalledBench)).map { it.plateau.slot.exercise.id })
    }

    @Test
    fun `a stalled bench is offered incline - triceps and shoulder work on its own day and a variation`() {
        val options = PlanReviewEngine.stalledLifts(ctx(upper, stalledBench)).single().options
        val adds = options.mapNotNull { it.action as? RecommendationAction.AddExercise }
        assertEquals(listOf("incline-barbell-bench-press", "close-grip-bench-press"), adds.take(2).map { it.exerciseId })
        assertTrue(adds.all { it.planDayId == 1L })
        assertEquals(listOf(SupportReason.UPPER_CHEST, SupportReason.TRICEPS, SupportReason.SHOULDERS), options.mapNotNull { it.reason })
        assertTrue(options.any { it.kind == FixKind.VARIATION })
    }

    @Test
    fun `the goal decides what comes first - and what was tried goes last`() {
        // Seven weeks at 6–12 reps: a rep-range block is on offer too.
        val history = stalledBench + ("barbell-bench-press" to weekly(80.0, 0.0, weeks = 7))
        fun first(c: RecommendationContext) = PlanReviewEngine.stalledLifts(c).single().options.first().kind
        assertEquals(FixKind.SUPPORT, first(ctx(upper, history)))
        assertEquals(FixKind.REP_RANGE, first(ctx(upper, history, goal = Goal.STRENGTH)))
        val triedRange = ctx(upper, history, goal = Goal.STRENGTH).copy(
            tried = mapOf("barbell-bench-press" to listOf(TriedFix(FixKind.REP_RANGE, today.minus(10, DateTimeUnit.WEEK)))),
        )
        assertEquals(FixKind.VARIATION, first(triedRange))
        assertEquals(FixKind.REP_RANGE, PlanReviewEngine.stalledLifts(triedRange).single().options.last().kind)
        // Changed eight weeks ago or less: no new range yet.
        val recently = triedRange.copy(tried = mapOf("barbell-bench-press" to listOf(TriedFix(FixKind.REP_RANGE, today.minus(6, DateTimeUnit.WEEK)))))
        assertTrue(PlanReviewEngine.stalledLifts(recently).single().options.none { it.kind == FixKind.REP_RANGE })
    }

    @Test
    fun `a new lifter keeps the lift - a cooling lift waits - focus areas come first`() {
        val c = ctx(upper, stalledBench)
        assertTrue(PlanReviewEngine.stalledLifts(c.copy(experience = ExperienceLevel.NEW)).single().options.none { it.kind == FixKind.VARIATION })
        assertTrue(PlanReviewEngine.stalledLifts(c.copy(cooling = setOf("barbell-bench-press"))).isEmpty())

        val plan = upper + slot("back-squat", 4, dayId = 2, dayName = "Lower A")
        val history = stalledBench + ("back-squat" to weekly(100.0, 0.0))
        fun order(c: RecommendationContext) = PlanReviewEngine.stalledLifts(c).map { it.plateau.slot.exercise.id }
        assertEquals(listOf("barbell-bench-press", "back-squat"), order(ctx(plan, history)))
        assertEquals(listOf("back-squat", "barbell-bench-press"), order(ctx(plan, history).copy(focusAreas = setOf(BodyArea.LEGS))))
    }

    @Test
    fun `the review comes four weeks after the last one - with no suggestion twice`() {
        val plan = upper + listOf(slot("back-squat", 4, dayId = 2, dayName = "Lower A"))
        val history = stalledBench + ("overhead-press" to weekly(40.0, 0.0)) + ("back-squat" to weekly(100.0, 0.0))
        assertNull(PlanReviewEngine.review(ctx(plan, history), since = today.minus(20, DateTimeUnit.DAY)))
        val review = assertNotNull(PlanReviewEngine.review(ctx(plan, history), since = today.minus(28, DateTimeUnit.DAY)))
        assertEquals(3, review.stalled.size)
        val ids = review.stalled.flatMap { s ->
            s.options.mapNotNull { (it.action as? RecommendationAction.AddExercise)?.exerciseId ?: (it.action as? RecommendationAction.SwapVariation)?.toExerciseId }
        }
        assertEquals(ids.distinct(), ids)
        assertTrue(review.trends.single { it.exercise.id == "barbell-row" }.change > 0.1)
        // Not before the plan is old enough, and not when everything moves.
        assertNull(PlanReviewEngine.review(ctx(plan, history, planWeeks = 5), since = today.minus(35, DateTimeUnit.DAY)))
        val rising = mapOf("barbell-bench-press" to weekly(80.0, 2.5), "overhead-press" to weekly(40.0, 1.25), "back-squat" to weekly(100.0, 2.5))
        assertNull(PlanReviewEngine.review(ctx(plan, rising), since = today.minus(35, DateTimeUnit.DAY)))
    }

    @Test
    fun `upper and lower body by the lift's muscles`() {
        assertEquals(
            listOf(false, false, false, true, true, true),
            listOf("barbell-bench-press", "overhead-press", "pull-up", "back-squat", "deadlift", "barbell-hip-thrust").map { byId.getValue(it).isLowerBody },
        )
    }

    @Test
    fun `snoozing holds the review back until the day after`() {
        val c = ctx(upper, stalledBench)
        val since = today.minus(30, DateTimeUnit.DAY)
        assertNull(PlanReviewEngine.review(c, since, snoozedUntil = today))
        assertNotNull(PlanReviewEngine.review(c, since, snoozedUntil = today.minus(1, DateTimeUnit.DAY)))
    }
}
