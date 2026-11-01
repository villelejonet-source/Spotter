package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FiveThreeOneTest {
    private val bench = byId.getValue("barbell-bench-press")
    private val squat = byId.getValue("back-squat")

    private fun weights(week: Int, tm: Double = 100.0) = FiveThreeOne.targets(tm, week, bench, WeightUnit.KG).map { it.weightKg }
    private fun reps(week: Int) = FiveThreeOne.targets(100.0, week, bench, WeightUnit.KG).map { it.reps }
    private fun amrap(week: Int) = FiveThreeOne.targets(100.0, week, bench, WeightUnit.KG).map { it.amrap }

    @Test
    fun `each week is its percentages of the training max`() {
        assertEquals(listOf(65.0, 75.0, 85.0), weights(1))
        assertEquals(listOf(70.0, 80.0, 90.0), weights(2))
        assertEquals(listOf(75.0, 85.0, 95.0), weights(3))
        assertEquals(listOf(40.0, 50.0, 60.0), weights(4))
        assertEquals(listOf(5, 5, 5), reps(1))
        assertEquals(listOf(3, 3, 3), reps(2))
        assertEquals(listOf(5, 3, 1), reps(3))
        assertEquals(listOf(5, 5, 5), reps(4))
    }

    @Test
    fun `the last set of weeks one to three is AMRAP and the deload has none`() {
        (1..3).forEach { assertEquals(listOf(false, false, true), amrap(it)) }
        assertEquals(listOf(false, false, false), amrap(4))
        assertNull(FiveThreeOne.amrapMinimum(4))
        assertEquals(1, FiveThreeOne.amrapMinimum(3))
    }

    @Test
    fun `weights round to a loadable weight`() {
        // 65 % of 107.5 = 69.875, nearest plate step 70; 95 % = 102.125 → 102.5.
        assertEquals(listOf(70.0, 80.0, 92.5), weights(1, tm = 107.5))
        assertEquals(102.5, weights(3, tm = 107.5).last()!!, 0.001)
    }

    @Test
    fun `hitting the AMRAP minimum moves to the next week with the same training max`() {
        assertEquals(100.0 to 2, FiveThreeOne.advance(100.0, 1, amrapReps = 8, bench, WeightUnit.KG))
        assertEquals(100.0 to 4, FiveThreeOne.advance(100.0, 3, amrapReps = 1, bench, WeightUnit.KG))
    }

    @Test
    fun `after the deload week the training max goes up and the cycle starts over`() {
        assertEquals(102.5 to 1, FiveThreeOne.advance(100.0, 4, amrapReps = null, bench, WeightUnit.KG))
        assertEquals(145.0 to 1, FiveThreeOne.advance(140.0, 4, amrapReps = null, squat, WeightUnit.KG))
    }

    @Test
    fun `missing the AMRAP minimum lowers the training max and repeats the week`() {
        assertEquals(90.0 to 2, FiveThreeOne.advance(100.0, 2, amrapReps = 2, bench, WeightUnit.KG))
    }

    @Test
    fun `a starting training max is ninety percent of the 1RM rounded down`() {
        assertEquals(105.0, FiveThreeOne.trainingMaxFrom(118.0, bench, WeightUnit.KG), 0.001)
    }
}
