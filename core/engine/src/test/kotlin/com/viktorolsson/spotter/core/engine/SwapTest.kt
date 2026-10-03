package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.Fixtures.library
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapTest {
    private fun ranked(id: String, equipment: Set<com.viktorolsson.spotter.core.model.Equipment> = Fixtures.fullGym, limitations: Set<Limitation> = emptySet()) =
        ExerciseSimilarity.rank(byId.getValue(id), library, equipment, limitations).map { it.exercise.id }

    /** The case from PLAN.md: bench busy → DB bench / machine press first. */
    @Test
    fun `bench busy - dumbbell bench and machine press first`() {
        assertEquals(setOf("dumbbell-bench-press", "machine-chest-press"), ranked("barbell-bench-press").take(2).toSet())
    }

    @Test
    fun `top alternatives keep the movement pattern`() {
        listOf("barbell-bench-press", "back-squat", "lat-pulldown", "romanian-deadlift", "dumbbell-lateral-raise", "triceps-pushdown")
            .forEach { id ->
                val pattern = byId.getValue(id).movementPattern
                ranked(id).take(5).forEach { assertEquals("$id → $it", pattern, byId.getValue(it).movementPattern) }
            }
    }

    @Test
    fun `easier and supported options before harder ones`() {
        val pulldown = ranked("lat-pulldown")
        assertTrue(pulldown.indexOf("assisted-pull-up-machine") < pulldown.indexOf("pull-up"))
        val bench = ranked("barbell-bench-press")
        assertTrue(bench.indexOf("smith-machine-bench-press") < bench.indexOf("push-up"))
    }

    @Test
    fun `respects equipment, limitations, and never suggests the original`() {
        val dumbbells = ranked("barbell-bench-press", Fixtures.dumbbellsOnly)
        assertEquals("dumbbell-bench-press", dumbbells.first())
        dumbbells.forEach { assertTrue(it, Fixtures.dumbbellsOnly.containsAll(byId.getValue(it).equipment)) }

        val knee = ranked("back-squat", limitations = setOf(Limitation.KNEE))
        assertTrue(knee.none { it in Rules.excludedFor.getValue(Limitation.KNEE) })
        assertEquals("leg-press", knee.first())
        assertFalse("back-squat" in ranked("back-squat"))

        val excluded = ExerciseSimilarity.rank(byId.getValue("barbell-bench-press"), library, Fixtures.fullGym, exclude = setOf("dumbbell-bench-press"))
        assertFalse(excluded.any { it.exercise.id == "dumbbell-bench-press" })
    }

    @Test
    fun `unrelated exercises are not suggested`() {
        ranked("dumbbell-curl").forEach {
            val pattern = byId.getValue(it).movementPattern
            assertTrue("$it ($pattern)", pattern == MovementPattern.ELBOW_FLEXION || pattern == MovementPattern.VERTICAL_PULL || pattern == MovementPattern.HORIZONTAL_PULL)
        }
    }

    @Test
    fun `weights convert between exercises of the same family`() {
        fun convert(from: String, kg: Double, to: String, unit: WeightUnit = WeightUnit.KG) =
            WeightConversion.convert(byId.getValue(from), kg, byId.getValue(to), unit)

        assertEquals(40.0, convert("barbell-bench-press", 100.0, "dumbbell-bench-press")!!, 0.001)
        assertEquals(180.0, convert("back-squat", 100.0, "leg-press")!!, 0.001)
        assertEquals(100.0, convert("dumbbell-bench-press", 40.0, "barbell-bench-press")!!, 0.001)
        // 40 kg = 88.2 lb per hand, rounded down to a 5 lb step.
        assertEquals(85.0, WeightUnit.LB.fromKg(convert("barbell-bench-press", 100.0, "dumbbell-bench-press", WeightUnit.LB)!!), 0.01)
        // Different anchor families or unloaded targets can't be converted.
        assertNull(convert("barbell-bench-press", 100.0, "back-squat"))
        assertNull(convert("lat-pulldown", 60.0, "pull-up"))
    }
}
