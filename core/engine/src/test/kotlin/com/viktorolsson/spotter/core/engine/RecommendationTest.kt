package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.Fixtures.library
import com.viktorolsson.spotter.core.model.DeloadReason
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecommendationTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun slot(
        id: String,
        planExerciseId: Long,
        dayId: Long = 1,
        dayName: String = "Upper A",
        sets: Int = 3,
        repMin: Int = 6,
        repMax: Int = 12,
        rule: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION,
    ) = PlanSlot(planExerciseId, dayId, dayName, byId.getValue(id), sets, repMin, repMax, 1, rule)

    /** [sessions] of the same top set, [daysApart] days apart, ending today. */
    private fun flat(weight: Double?, reps: Int, sessions: Int, daysApart: Long = 7) =
        List(sessions) { i -> ExerciseExposure(today.minusDays((sessions - 1 - i) * daysApart), List(3) { LoggedSet(weight, reps) }) }

    private fun volumes(vararg under: MuscleGroup) = MuscleBalance.weekly(emptyList(), Goal.HYPERTROPHY).map {
        if (it.group in under) it else MuscleVolume(it.group, 12.0, it.targetLow, it.targetHigh)
    }

    private fun ctx(
        plan: List<PlanSlot>,
        history: Map<String, List<ExerciseExposure>>,
        volume: List<MuscleVolume> = volumes(),
        trainingDates: List<LocalDate> = history.values.flatten().map { it.date }.distinct(),
        equipment: Set<Equipment> = Fixtures.fullGym,
        limitations: Set<Limitation> = emptySet(),
        recentRir: List<Int> = emptyList(),
        planDays: List<Pair<Long, String>> = plan.map { it.planDayId to it.dayName }.distinct(),
        lastDeloadEnd: LocalDate? = today.minusDays(10),
        applied: List<AppliedVariation> = emptyList(),
    ) = RecommendationContext(
        today, plan, planDays, history, trainingDates, lastDeloadEnd, volume, recentRir, library, equipment, limitations, applied,
    )

    @Test
    fun `a lift that keeps improving is not a plateau`() {
        val bench = slot("barbell-bench-press", 1)
        val rising = (0 until 5).map { i ->
            ExerciseExposure(today.minusWeeks(4L - i), List(3) { LoggedSet(80.0 + i * 2.5, 8) })
        }
        assertNull(RecommendationEngine.detectPlateau(bench, rising))
    }

    @Test
    fun `stalled for three sessions over three weeks is a plateau, shorter stalls are not`() {
        val bench = slot("barbell-bench-press", 1)
        val plateau = RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 4))
        assertNotNull(plateau)
        assertEquals(3, plateau!!.sessions)
        assertEquals(3, plateau.weeks)
        assertEquals(80.0 to 8, plateau.latest!!.weightKg to plateau.latest!!.reps)

        assertNull(RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 3))) // only 2 after the best
        assertNull(RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 5, daysApart = 3))) // 12 days
    }

    @Test
    fun `missing the target twice in a row is a plateau straight away`() {
        val bench = slot("barbell-bench-press", 1, repMin = 6)
        val missed = listOf(
            ExerciseExposure(today.minusDays(4), List(3) { LoggedSet(85.0, 4) }),
            ExerciseExposure(today, List(3) { LoggedSet(85.0, 5) }),
        )
        assertTrue(RecommendationEngine.detectPlateau(bench, missed)!!.missedTwice)
    }

    /** The PLAN.md example: bench stuck → weak-point accessory on the same day, with the evidence. */
    @Test
    fun `stalled bench with enough volume gets a weak-point accessory on its day`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("back-squat", 3, dayId = 2, dayName = "Lower A"))
        val drafts = RecommendationEngine.evaluate(ctx(plan, mapOf("barbell-bench-press" to flat(80.0, 5, sessions = 5))))
        // Chest, back and legs each sit on one day here, so frequency cards come too; this is the bench card.
        val rec = drafts.single { it.exerciseId == "barbell-bench-press" }
        assertEquals(RecommendationType.ADD_ACCESSORY, rec.type)
        val action = rec.payload.action as RecommendationAction.AddExercise
        assertEquals("close-grip-bench-press", action.exerciseId)
        assertEquals(1L to "Upper A", action.planDayId to action.dayName)
        val evidence = rec.payload.evidence
        assertEquals(listOf<Any?>("Barbell Bench Press", 80.0, 5, 4), listOf(evidence.exerciseName, evidence.weightKg, evidence.reps, evidence.sessions))
    }

    @Test
    fun `accessories respect equipment, limitations and what's already in the plan`() {
        val bench = slot("dumbbell-bench-press", 1)
        val noBarbell = ctx(listOf(bench), emptyMap(), equipment = setOf(Equipment.DUMBBELL, Equipment.BENCH, Equipment.DIP_STATION))
        assertEquals("triceps-dip", RecommendationEngine.accessoryFor(bench, noBarbell)!!.id)
        val shoulder = noBarbell.copy(limitations = setOf(Limitation.SHOULDER))
        assertEquals("dumbbell-front-raise", RecommendationEngine.accessoryFor(bench, shoulder)!!.id)
        val withCloseGrip = ctx(listOf(slot("barbell-bench-press", 1), slot("close-grip-bench-press", 2)), emptyMap())
        assertEquals("paused-bench-press", RecommendationEngine.accessoryFor(slot("barbell-bench-press", 1), withCloseGrip)!!.id)
    }

    @Test
    fun `stalled with too little weekly volume adds sets first`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("incline-dumbbell-press", 2), slot("barbell-row", 3))
        val rec = RecommendationEngine.evaluate(
            ctx(plan, mapOf("barbell-bench-press" to flat(80.0, 5, sessions = 4)), volume = volumes(MuscleGroup.CHEST)),
        ).single()
        assertEquals(RecommendationType.ADD_VOLUME, rec.type)
        assertEquals(listOf(1L, 2L), (rec.payload.action as RecommendationAction.AddSets).planExerciseIds)
        assertEquals(MuscleGroup.CHEST, rec.payload.evidence.group)
    }

    @Test
    fun `stuck in one rep range for six weeks switches the range for a block`() {
        // The PLAN.md example: 5 × 5 → 4 × 8.
        val squat = slot("back-squat", 1, sets = 5, repMin = 3, repMax = 6, rule = ProgressionRule.LINEAR)
        val history = listOf(ExerciseExposure(today.minusWeeks(8), List(5) { LoggedSet(90.0, 5) })) + flat(100.0, 5, sessions = 4)
        val rec = RecommendationEngine.evaluate(ctx(listOf(squat), mapOf("back-squat" to history))).single()
        assertEquals(RecommendationType.CHANGE_REP_RANGE, rec.type)
        val action = rec.payload.action as RecommendationAction.ChangeRepRange
        assertEquals(listOf(4, 8, 10), listOf(action.sets, action.repMin, action.repMax))
    }

    @Test
    fun `isolation lifts with no accessory get a variation, and a reminder to switch back later`() {
        val raise = slot("dumbbell-lateral-raise", 7, repMin = 10, repMax = 15)
        val rec = RecommendationEngine.evaluate(ctx(listOf(raise), mapOf("dumbbell-lateral-raise" to flat(8.0, 12, sessions = 4)))).single()
        assertEquals(RecommendationType.VARIATION, rec.type)
        val swap = rec.payload.action as RecommendationAction.SwapVariation
        assertEquals("cable-lateral-raise", swap.toExerciseId)

        val afterBlock = ctx(
            plan = listOf(raise.copy(exercise = byId.getValue("cable-lateral-raise"))),
            history = emptyMap(),
            applied = listOf(AppliedVariation(swap, today.minusWeeks(5))),
        )
        val back = RecommendationEngine.evaluate(afterBlock).single { it.type == RecommendationType.VARIATION }
        val backAction = back.payload.action as RecommendationAction.SwapVariation
        assertTrue(backAction.returning)
        assertEquals("dumbbell-lateral-raise", backAction.toExerciseId)
    }

    @Test
    fun `several stalled lifts at once suggest a deload instead of per-lift changes`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("back-squat", 3), slot("deadlift", 4))
        val history = plan.associate { it.exercise.id to flat(80.0, 6, sessions = 4) }
        val recs = RecommendationEngine.evaluate(ctx(plan, history))
        assertEquals(listOf(RecommendationType.DELOAD), recs.map { it.type })
        assertEquals(DeloadReason.SEVERAL_PLATEAUS, recs.single().payload.evidence.deloadReason)
        assertEquals(4, recs.single().payload.evidence.lifts.size)
    }

    @Test
    fun `a dismissed or running deload no longer holds back per-lift fixes, a running one holds everything`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("back-squat", 3), slot("deadlift", 4))
        val history = plan.associate { it.exercise.id to flat(80.0, 6, sessions = 4) }
        val handled = RecommendationEngine.evaluate(ctx(plan, history).copy(deloadRecentlyHandled = true))
        assertTrue(handled.none { it.type == RecommendationType.DELOAD })
        assertEquals(4, handled.count { it.exerciseId != null })
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history).copy(deloadActive = true)).isEmpty())
    }

    @Test
    fun `falling performance near failure suggests a recovery deload`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2))
        fun falling() = listOf(80.0, 82.5, 75.0).mapIndexed { i, w -> ExerciseExposure(today.minusDays(14L - i * 7), List(3) { LoggedSet(w, 8) }) }
        val history = mapOf("barbell-bench-press" to falling(), "barbell-row" to falling())
        val rec = RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(0, 0, 1, 0))).single()
        assertEquals(DeloadReason.RECOVERY, rec.payload.evidence.deloadReason)
        // Same drop without sets near failure: no deload yet.
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(2, 2, 3, 2))).none { it.type == RecommendationType.DELOAD })
    }

    @Test
    fun `six weeks of steady training schedules a deload`() {
        val plan = listOf(slot("barbell-bench-press", 1))
        val dates = (0 until 6).map { today.minusWeeks(it.toLong()) }
        val rec = RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = dates, lastDeloadEnd = null)).single()
        assertEquals(DeloadReason.SCHEDULED, rec.payload.evidence.deloadReason)
        assertTrue(RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = dates.take(5), lastDeloadEnd = null)).isEmpty())
    }

    @Test
    fun `a major muscle trained on one day a week gets added to another day`() {
        val plan = listOf(
            slot("barbell-bench-press", 1, dayId = 1, dayName = "Day A"),
            slot("barbell-row", 2, dayId = 1, dayName = "Day A"),
            slot("back-squat", 3, dayId = 2, dayName = "Day B"),
            slot("romanian-deadlift", 4, dayId = 2, dayName = "Day B"),
            slot("barbell-hip-thrust", 5, dayId = 2, dayName = "Day B"),
            slot("lat-pulldown", 6, dayId = 2, dayName = "Day B"),
            slot("overhead-press", 7, dayId = 1, dayName = "Day A"),
            slot("dumbbell-lateral-raise", 8, dayId = 2, dayName = "Day B"),
        )
        val dates = listOf(today, today.minusWeeks(1))
        val recs = RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = dates))
        val chest = recs.single { it.payload.evidence.group == MuscleGroup.CHEST }
        assertEquals(RecommendationType.INCREASE_FREQUENCY, chest.type)
        val add = chest.payload.action as RecommendationAction.AddExercise
        assertEquals("Day B", add.dayName)
        assertTrue(byId.getValue(add.exerciseId).primaryMuscles.any { MuscleGroup.of(it) == MuscleGroup.CHEST })
        // Quads and hamstrings are only on Day B too.
        assertTrue(recs.any { it.payload.evidence.group == MuscleGroup.QUADS })
        // Not before two weeks of training.
        assertTrue(RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = listOf(today))).isEmpty())
    }

    @Test
    fun `payload survives a JSON round trip`() {
        val plan = listOf(slot("barbell-bench-press", 1))
        val rec = RecommendationEngine.evaluate(ctx(plan, mapOf("barbell-bench-press" to flat(80.0, 5, sessions = 4)))).single()
        assertEquals(rec.payload, com.viktorolsson.spotter.core.model.RecommendationPayload.decode(rec.payload.encode()))
    }
}
