package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.toKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
    private fun input(
        id: String = "barbell-bench-press",
        rule: ProgressionRule = ProgressionRule.DOUBLE_PROGRESSION,
        sets: Int = 3,
        repMin: Int = 8,
        repMax: Int = 12,
        targetRir: Int? = 2,
        history: List<List<LoggedSet>> = emptyList(),
        start: Double? = 60.0,
        unit: WeightUnit = WeightUnit.KG,
    ) = ProgressionInput(byId.getValue(id), rule, sets, repMin, repMax, targetRir, history, start, unit)

    private fun sets(weight: Double?, vararg reps: Int, rir: Int? = null) = reps.map { LoggedSet(weight, it, rir) }

    private fun ProgressionTarget.weights() = sets.map { it.weightKg }
    private fun ProgressionTarget.reps() = sets.map { it.reps }

    @Test
    fun `first time uses the calibration estimate at the bottom of the range`() {
        val target = Progression.next(input())
        assertEquals(ProgressionReason.CALIBRATION, target.reason)
        assertEquals(listOf(60.0, 60.0, 60.0), target.weights())
        assertEquals(listOf(8, 8, 8), target.reps())

        val noEstimate = Progression.next(input(id = "dumbbell-lateral-raise", start = null))
        assertEquals(ProgressionReason.FIRST_TIME, noEstimate.reason)
        assertNull(noEstimate.sets.first().weightKg)
    }

    @Test
    fun `double progression adds a rep per set until every set hits the top`() {
        val target = Progression.next(input(history = listOf(sets(60.0, 10, 9, 8))))
        assertEquals(ProgressionReason.ADD_REPS, target.reason)
        assertEquals(listOf(60.0, 60.0, 60.0), target.weights())
        assertEquals(listOf(11, 10, 9), target.reps())

        val capped = Progression.next(input(history = listOf(sets(60.0, 12, 12, 11))))
        assertEquals(listOf(12, 12, 12), capped.reps())
    }

    @Test
    fun `double progression adds weight and drops to the bottom once all sets hit the top`() {
        val target = Progression.next(input(history = listOf(sets(60.0, 12, 12, 12))))
        assertEquals(ProgressionReason.INCREASE_WEIGHT, target.reason)
        assertEquals(listOf(62.5, 62.5, 62.5), target.weights())
        assertEquals(listOf(8, 8, 8), target.reps())
    }

    @Test
    fun `fewer sets than planned is not the top of the range`() {
        val target = Progression.next(input(history = listOf(sets(60.0, 12, 12))))
        assertEquals(ProgressionReason.ADD_REPS, target.reason)
        assertEquals(60.0, target.sets.first().weightKg!!, 0.0)
    }

    @Test
    fun `increments follow the equipment and unit`() {
        val dumbbell = Progression.next(input(id = "dumbbell-curl", history = listOf(sets(12.0, 12, 12, 12))))
        assertEquals(14.0, dumbbell.sets.first().weightKg!!, 0.001)

        val lb = Progression.next(input(unit = WeightUnit.LB, history = listOf(sets(WeightUnit.LB.toKgOf(135.0), 12, 12, 12))))
        assertEquals(140.0, WeightUnit.LB.fromKg(lb.sets.first().weightKg!!), 0.01)
    }

    @Test
    fun `linear adds weight every successful session, more for lower body`() {
        // Range 3–6: linear target is 4 reps.
        val bench = Progression.next(input(rule = ProgressionRule.LINEAR, repMin = 3, repMax = 6, history = listOf(sets(80.0, 4, 4, 5))))
        assertEquals(ProgressionReason.INCREASE_WEIGHT, bench.reason)
        assertEquals(listOf(82.5, 82.5, 82.5), bench.weights())
        assertEquals(listOf(4, 4, 4), bench.reps())

        val squat = Progression.next(
            input(id = "back-squat", rule = ProgressionRule.LINEAR, repMin = 3, repMax = 6, history = listOf(sets(100.0, 4, 4, 4))),
        )
        assertEquals(105.0, squat.sets.first().weightKg!!, 0.001)
    }

    @Test
    fun `a miss repeats the weight, two misses in a row reset by ten percent`() {
        val once = Progression.next(input(rule = ProgressionRule.LINEAR, repMin = 3, repMax = 6, history = listOf(sets(100.0, 4, 3, 2))))
        assertEquals(ProgressionReason.REPEAT, once.reason)
        assertEquals(100.0, once.sets.first().weightKg!!, 0.0)

        val twice = Progression.next(
            input(rule = ProgressionRule.LINEAR, repMin = 3, repMax = 6, history = listOf(sets(100.0, 4, 3, 2), sets(100.0, 3, 3, 2))),
        )
        assertEquals(ProgressionReason.RESET_AFTER_MISSES, twice.reason)
        assertEquals(90.0, twice.sets.first().weightKg!!, 0.001)

        val doubleMiss = Progression.next(input(history = listOf(sets(60.0, 7, 6, 6))))
        assertEquals(ProgressionReason.REPEAT, doubleMiss.reason)
        assertEquals(listOf(8, 8, 8), doubleMiss.reps())
    }

    @Test
    fun `logged RIR far from target overrides the default`() {
        val tooHard = Progression.next(input(history = listOf(sets(60.0, 12, 12, 12, rir = 0))))
        assertEquals(ProgressionReason.HOLD_TOO_HARD, tooHard.reason)
        assertEquals(60.0, tooHard.sets.first().weightKg!!, 0.0)

        val tooEasy = Progression.next(input(history = listOf(sets(60.0, 10, 10, 10, rir = 4))))
        assertEquals(ProgressionReason.INCREASE_TOO_EASY, tooEasy.reason)
        assertEquals(62.5, tooEasy.sets.first().weightKg!!, 0.001)

        val onTarget = Progression.next(input(history = listOf(sets(60.0, 12, 12, 12, rir = 2))))
        assertEquals(ProgressionReason.INCREASE_WEIGHT, onTarget.reason)
    }

    @Test
    fun `bodyweight exercises progress reps up to the top of the range`() {
        val target = Progression.next(input(id = "push-up", start = null, history = listOf(sets(null, 10, 9, 8))))
        assertEquals(listOf(11, 10, 9), target.reps())
        assertNull(target.sets.first().weightKg)

        val top = Progression.next(input(id = "push-up", start = null, history = listOf(sets(null, 12, 12, 12))))
        assertEquals(ProgressionReason.BODYWEIGHT_TOP_OF_RANGE, top.reason)
    }

    @Test
    fun `holds progress in seconds, five at a time`() {
        val plank = input(id = "plank", repMin = 30, repMax = 60, targetRir = null, start = null, history = listOf(sets(null, 30, 30, 25)))
        assertEquals(listOf(35, 35, 30), Progression.next(plank).reps())

        val top = Progression.next(plank.copy(history = listOf(sets(null, 60, 60, 60))))
        assertEquals(ProgressionReason.BODYWEIGHT_TOP_OF_RANGE, top.reason)
    }

    private fun WeightUnit.toKgOf(value: Double) = toKg(value)
}
