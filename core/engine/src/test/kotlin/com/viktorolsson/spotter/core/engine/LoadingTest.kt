package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.toKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingTest {
    private val bench = byId.getValue("barbell-bench-press")

    @Test
    fun `plates per side in kg and lb`() {
        val kg = Plates.load(102.5, bench, WeightUnit.KG)!!
        assertEquals(listOf(25.0, 15.0, 1.25), kg.perSide)
        assertTrue(kg.exact)

        val lb = Plates.load(WeightUnit.LB.toKg(225.0), bench, WeightUnit.LB)!!
        assertEquals(45.0, lb.bar, 0.0)
        assertEquals(listOf(45.0, 45.0), lb.perSide)
        assertEquals(225.0, lb.total, 0.01)
    }

    @Test
    fun `bar types, impossible loads, and non-bar exercises`() {
        assertEquals(25.0, Plates.bar(byId.getValue("trap-bar-deadlift"), WeightUnit.KG)!!, 0.0)
        assertEquals(10.0, Plates.bar(byId.getValue("ez-bar-curl"), WeightUnit.KG)!!, 0.0)
        assertNull(Plates.load(30.0, byId.getValue("dumbbell-bench-press"), WeightUnit.KG))
        assertNull(Plates.load(60.0, byId.getValue("smith-machine-bench-press"), WeightUnit.KG))

        val odd = Plates.load(101.0, bench, WeightUnit.KG)!!
        assertFalse(odd.exact)
        assertEquals(100.0, odd.total, 0.0) // closest loadable without going over
        assertTrue(Plates.load(20.0, bench, WeightUnit.KG)!!.perSide.isEmpty())
    }

    @Test
    fun `barbell warm-up ramp`() {
        val sets = WarmUps.generate(100.0, bench, WeightUnit.KG)
        assertEquals(listOf(20.0 to 10, 50.0 to 5, 70.0 to 3, 85.0 to 1), sets.map { it.weightKg to it.reps })
        val older = WarmUps.generate(100.0, bench, WeightUnit.KG, extended = true)
        assertEquals(30.0 to 8, older[1].weightKg to older[1].reps)
    }

    @Test
    fun `light working weights skip steps that would repeat or exceed`() {
        // 40 kg: bar 20 isn't < 50 % of 40, so no bar set; 20, 27.5, 32.5 remain.
        val sets = WarmUps.generate(40.0, bench, WeightUnit.KG)
        assertEquals(listOf(20.0, 27.5, 32.5), sets.map { it.weightKg })
        assertTrue(WarmUps.generate(20.0, bench, WeightUnit.KG).isEmpty())
    }

    @Test
    fun `dumbbell and lb ramps round to their own steps, bodyweight gets none`() {
        val db = WarmUps.generate(30.0, byId.getValue("dumbbell-bench-press"), WeightUnit.KG)
        assertEquals(listOf(14.0, 20.0, 24.0), db.map { it.weightKg })
        val lb = WarmUps.generate(WeightUnit.LB.toKg(225.0), bench, WeightUnit.LB).map { WeightUnit.LB.fromKg(it.weightKg!!) }
        assertEquals(listOf(45.0, 110.0, 155.0, 190.0), lb.map { Math.round(it).toDouble() })
        assertTrue(WarmUps.generate(0.0, byId.getValue("push-up"), WeightUnit.KG).isEmpty())
    }
}
