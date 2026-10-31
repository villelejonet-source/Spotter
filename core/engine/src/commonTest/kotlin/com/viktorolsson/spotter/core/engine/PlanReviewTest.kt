package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.Fixtures.library
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.ProgressionRule
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

    private fun ctx(
        plan: List<PlanSlot>,
        history: Map<String, List<ExerciseExposure>>,
        sessions: Int = 10,
        equipment: Set<Equipment> = Fixtures.fullGym,
    ) = RecommendationContext(
        today = today,
        plan = plan,
        planDays = plan.map { it.planDayId to it.dayName }.distinct(),
        history = history,
        trainingDates = List(sessions) { today.minus(it * 3L, DateTimeUnit.DAY) },
        lastDeloadEnd = null,
        weeklyVolume = MuscleBalance.weekly(emptyList(), Goal.HYPERTROPHY).map { MuscleVolume(it.group, 12.0, it.targetLow, it.targetHigh) },
        recentRir = emptyList(),
        library = library,
        equipment = equipment,
        limitations = emptySet(),
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
    fun `no review before four weeks - one at four weeks with a stall`() {
        val c = ctx(upper, stalledBench)
        assertNull(PlanReviewEngine.review(c, since = today.minus(20, DateTimeUnit.DAY)))
        val review = assertNotNull(PlanReviewEngine.review(c, since = today.minus(28, DateTimeUnit.DAY)))
        assertEquals(listOf("barbell-bench-press"), review.stalled.map { it.plateau.slot.exercise.id })
        assertEquals(3, review.stalled.single().supports.size)
        assertEquals(1L, review.stalled.single().supports.first().second.planDayId)
        val row = review.trends.single { it.exercise.id == "barbell-row" }
        assertTrue(row.change > 0.1)
    }

    @Test
    fun `no review when everything progresses or too few sessions`() {
        val rising = stalledBench + ("barbell-bench-press" to weekly(80.0, 2.5))
        assertNull(PlanReviewEngine.review(ctx(upper, rising), since = today.minus(35, DateTimeUnit.DAY)))
        assertNull(PlanReviewEngine.review(ctx(upper, stalledBench, sessions = 5), since = today.minus(35, DateTimeUnit.DAY)))
    }

    @Test
    fun `two stalled main lifts bring the review forward - suggestions never repeat`() {
        val plan = upper + listOf(slot("back-squat", 4, dayId = 2, dayName = "Lower A"))
        val history = stalledBench + ("overhead-press" to weekly(40.0, 0.0)) + ("back-squat" to weekly(100.0, 0.0))
        val review = assertNotNull(PlanReviewEngine.review(ctx(plan, history, sessions = 5), since = today.minus(15, DateTimeUnit.DAY)))
        assertEquals(3, review.stalled.size)
        val ids = review.stalled.flatMap { s -> s.supports.map { it.first.exercise.id } + listOfNotNull(s.variation?.toExerciseId) }
        assertEquals(ids.distinct(), ids)
        assertNull(PlanReviewEngine.review(ctx(plan, history, sessions = 5), since = today.minus(10, DateTimeUnit.DAY)))
    }

    @Test
    fun `snoozing holds the review back until the day after`() {
        val c = ctx(upper, stalledBench)
        val since = today.minus(30, DateTimeUnit.DAY)
        assertNull(PlanReviewEngine.review(c, since, snoozedUntil = today))
        assertNotNull(PlanReviewEngine.review(c, since, snoozedUntil = today.minus(1, DateTimeUnit.DAY)))
    }
}
