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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationTest {
    private val today = LocalDate(2026, 10, 3)

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
        List(sessions) { i -> ExerciseExposure(today.minus((sessions - 1 - i) * daysApart, DateTimeUnit.DAY), List(3) { LoggedSet(weight, reps) }) }

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
        lastDeloadEnd: LocalDate? = today.minus(10, DateTimeUnit.DAY),
        applied: List<AppliedVariation> = emptyList(),
    ) = RecommendationContext(
        today, plan, history, trainingDates, lastDeloadEnd, volume, recentRir, library, equipment, limitations, applied,
    )

    @Test
    fun `a lift that keeps improving is not a plateau`() {
        val bench = slot("barbell-bench-press", 1)
        val rising = (0 until 5).map { i ->
            ExerciseExposure(today.minus(4L - i, DateTimeUnit.WEEK), List(3) { LoggedSet(80.0 + i * 2.5, 8) })
        }
        assertNull(RecommendationEngine.detectPlateau(bench, rising))
    }

    @Test
    fun `stalled for three sessions over three weeks is a plateau - shorter stalls are not`() {
        val bench = slot("barbell-bench-press", 1)
        val plateau = RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 4))
        assertNotNull(plateau)
        assertEquals(3, plateau.sessions)
        assertEquals(3, plateau.weeks)
        assertEquals(80.0 to 8, plateau.latest!!.weightKg to plateau.latest.reps)

        assertNull(RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 3))) // only 2 after the best
        assertNull(RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 5, daysApart = 3))) // 12 days
    }

    @Test
    fun `missing the target twice in a row is a plateau straight away`() {
        val bench = slot("barbell-bench-press", 1, repMin = 6)
        val missed = listOf(
            ExerciseExposure(today.minus(4, DateTimeUnit.DAY), List(3) { LoggedSet(85.0, 4) }),
            ExerciseExposure(today, List(3) { LoggedSet(85.0, 5) }),
        )
        assertTrue(RecommendationEngine.detectPlateau(bench, missed)!!.missedTwice)
    }

    @Test
    fun `a set with no reps doesn't count towards a session's score`() {
        val exposure = ExerciseExposure(today, listOf(LoggedSet(140.0, 0), LoggedSet(80.0, 8)))
        assertEquals(80.0 * (1 + 8 / 30.0), exposure.score, 1e-9)
        assertEquals(LoggedSet(80.0, 8), exposure.bestSet)
    }

    /** Bodyweight reps and an estimated 1RM in kg aren't comparable: adding weight isn't a drop. */
    @Test
    fun `moving from bodyweight to added weight is no plateau`() {
        val pullUps = slot("pull-up", 1)
        val history = flat(null, 12, sessions = 5).mapIndexed { i, e -> if (i < 3) e else e.copy(sets = List(3) { LoggedSet(5.0, 8) }) }
        assertNull(RecommendationEngine.detectPlateau(pullUps, history))
    }

    @Test
    fun `moving from bodyweight to added weight is no fall`() {
        val plan = listOf(slot("pull-up", 1), slot("chest-dip", 2))
        fun progressing() = listOf<Double?>(null, null, 5.0).mapIndexed { i, w ->
            ExerciseExposure(today.minus(14L - i * 7, DateTimeUnit.DAY), List(3) { LoggedSet(w, if (w == null) 12 else 8) })
        }
        val history = mapOf("pull-up" to progressing(), "chest-dip" to progressing())
        val recs = RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(0, 0, 1, 0)))
        assertTrue(recs.none { it.type == RecommendationType.DELOAD }, "$recs")
    }

    @Test
    fun `a lift set to no progression is never a plateau`() {
        val bench = slot("barbell-bench-press", 1, rule = ProgressionRule.NONE)
        assertNull(RecommendationEngine.detectPlateau(bench, flat(80.0, 8, sessions = 6)))
    }

    /** Twelve sessions over seven weeks: old enough for plan suggestions. */
    private val sevenWeeks = List(12) { today.minus(it * 4L, DateTimeUnit.DAY) }

    @Test
    fun `stalled lifts and lonely muscles make no cards of their own`() {
        // An upper/lower plan: chest only on the upper day, legs only on the lower day.
        val plan = listOf(
            slot("barbell-bench-press", 1), slot("barbell-row", 2),
            slot("back-squat", 3, dayId = 2, dayName = "Lower A"), slot("romanian-deadlift", 4, dayId = 2, dayName = "Lower A"),
        )
        val history = mapOf("barbell-bench-press" to flat(80.0, 5, sessions = 5))
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, trainingDates = sevenWeeks)).isEmpty())
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, volume = volumes(MuscleGroup.CHEST))).isEmpty())
    }

    @Test
    fun `too little weekly volume adds a set to the lift and one more for the muscle`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("incline-dumbbell-press", 2), slot("barbell-row", 3))
        val add = assertNotNull(RecommendationEngine.moreSets(plan[0], ctx(plan, emptyMap(), volume = volumes(MuscleGroup.CHEST))))
        assertEquals(listOf(1L, 2L), add.planExerciseIds)
        assertNull(RecommendationEngine.moreSets(plan[0], ctx(plan, emptyMap())))
    }

    @Test
    fun `stuck in one rep range for six weeks switches the range for a block`() {
        // The PLAN.md example: 5 × 5 → 4 × 8.
        val squat = slot("back-squat", 1, sets = 5, repMin = 3, repMax = 6, rule = ProgressionRule.LINEAR)
        val history = listOf(ExerciseExposure(today.minus(8, DateTimeUnit.WEEK), List(5) { LoggedSet(90.0, 5) })) + flat(100.0, 5, sessions = 4)
        val plateau = assertNotNull(RecommendationEngine.detectPlateau(squat, history))
        val action = assertNotNull(RecommendationEngine.repRangeChange(plateau))
        assertEquals(listOf(4, 8, 10), listOf(action.sets, action.repMin, action.repMax))
        // Four weeks in the range is too soon.
        assertNull(RecommendationEngine.repRangeChange(assertNotNull(RecommendationEngine.detectPlateau(squat, flat(100.0, 5, sessions = 4)))))
    }

    @Test
    fun `a variation block ends with a reminder to switch back`() {
        val raise = slot("dumbbell-lateral-raise", 7, repMin = 10, repMax = 15)
        val swap = assertNotNull(RecommendationEngine.variationFor(raise, ctx(listOf(raise), emptyMap())))
        assertEquals("cable-lateral-raise", swap.toExerciseId)

        val afterBlock = ctx(
            plan = listOf(raise.copy(exercise = byId.getValue("cable-lateral-raise"))),
            history = emptyMap(),
            applied = listOf(AppliedVariation(swap, today.minus(5, DateTimeUnit.WEEK))),
        )
        val back = RecommendationEngine.evaluate(afterBlock).single { it.type == RecommendationType.VARIATION }
        val backAction = back.payload.action as RecommendationAction.SwapVariation
        assertTrue(backAction.returning)
        assertEquals("dumbbell-lateral-raise", backAction.toExerciseId)
        assertTrue(RecommendationEngine.evaluate(afterBlock.copy(appliedVariations = listOf(AppliedVariation(swap, today.minus(4, DateTimeUnit.WEEK))))).isEmpty())
    }

    @Test
    fun `several stalled lifts at once suggest a deload once the plan is old enough`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("back-squat", 3), slot("deadlift", 4))
        val history = plan.associate { it.exercise.id to flat(80.0, 6, sessions = 4) }
        val recs = RecommendationEngine.evaluate(ctx(plan, history, trainingDates = sevenWeeks))
        assertEquals(listOf(RecommendationType.DELOAD), recs.map { it.type })
        assertEquals(DeloadReason.SEVERAL_PLATEAUS, recs.single().payload.evidence.deloadReason)
        assertEquals(4, recs.single().payload.evidence.lifts.size)
        // Four sessions in: too early to read stalls as a plan problem.
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history)).isEmpty())
    }

    @Test
    fun `a dismissed or running deload holds the deload back`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2), slot("back-squat", 3), slot("deadlift", 4))
        val history = plan.associate { it.exercise.id to flat(80.0, 6, sessions = 4) }
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, trainingDates = sevenWeeks).copy(deloadRecentlyHandled = true)).isEmpty())
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, trainingDates = sevenWeeks).copy(deloadActive = true)).isEmpty())
    }

    @Test
    fun `falling performance near failure suggests a recovery deload`() {
        val plan = listOf(slot("barbell-bench-press", 1), slot("barbell-row", 2))
        fun falling() = listOf(80.0, 82.5, 75.0).mapIndexed { i, w -> ExerciseExposure(today.minus(14L - i * 7, DateTimeUnit.DAY), List(3) { LoggedSet(w, 8) }) }
        val history = mapOf("barbell-bench-press" to falling(), "barbell-row" to falling())
        val rec = RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(0, 0, 1, 0))).single()
        assertEquals(DeloadReason.RECOVERY, rec.payload.evidence.deloadReason)
        // Same drop without sets near failure: no deload yet.
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(2, 2, 3, 2))).none { it.type == RecommendationType.DELOAD })
    }

    @Test
    fun `feeling low three times in ten days suggests a recovery deload`() {
        val plan = listOf(slot("barbell-bench-press", 1))
        assertTrue(RecommendationEngine.evaluate(ctx(plan, emptyMap()).copy(recentLowDays = 2)).none { it.type == RecommendationType.DELOAD })

        val rec = RecommendationEngine.evaluate(ctx(plan, emptyMap()).copy(recentLowDays = 3)).single()
        assertEquals(DeloadReason.RECOVERY, rec.payload.evidence.deloadReason)
        assertEquals(3, rec.payload.evidence.lowDays)
        assertTrue(rec.payload.evidence.lifts.isEmpty())
    }

    @Test
    fun `a 5-3-1 lift is never called stalled or falling`() {
        fun falling() = listOf(80.0, 82.5, 75.0).mapIndexed { i, w -> ExerciseExposure(today.minus(14L - i * 7, DateTimeUnit.DAY), List(3) { LoggedSet(w, 8) }) }
        val plan = listOf(slot("barbell-bench-press", 1).copy(fiveThreeOne = true), slot("barbell-row", 2).copy(fiveThreeOne = true))
        val history = mapOf("barbell-bench-press" to falling(), "barbell-row" to falling())
        assertTrue(RecommendationEngine.evaluate(ctx(plan, history, recentRir = listOf(0, 0, 1, 0))).isEmpty())
    }

    @Test
    fun `six weeks of steady training schedules a deload`() {
        val plan = listOf(slot("barbell-bench-press", 1))
        val dates = (0 until 6).map { today.minus(it.toLong(), DateTimeUnit.WEEK) }
        val rec = RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = dates, lastDeloadEnd = null)).single()
        assertEquals(DeloadReason.SCHEDULED, rec.payload.evidence.deloadReason)
        assertTrue(RecommendationEngine.evaluate(ctx(plan, emptyMap(), trainingDates = dates.take(5), lastDeloadEnd = null)).isEmpty())
    }

    @Test
    fun `payload survives a JSON round trip`() {
        val rec = RecommendationEngine.evaluate(ctx(listOf(slot("barbell-bench-press", 1)), emptyMap()).copy(recentLowDays = 3)).single()
        assertEquals(rec.payload, com.viktorolsson.spotter.core.model.RecommendationPayload.decode(rec.payload.encode()))
    }
}
