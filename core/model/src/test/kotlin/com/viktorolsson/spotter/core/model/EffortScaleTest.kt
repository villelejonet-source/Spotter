package com.viktorolsson.spotter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortScaleTest {
    @Test
    fun rpeIsTenMinusRir() {
        assertEquals(8, EffortScale.RPE.display(2))
        assertEquals(10, EffortScale.RPE.display(0))
        assertEquals(2, EffortScale.RPE.toRir(8))
        assertEquals(0, EffortScale.RPE.toRir(10))
    }

    @Test
    fun rpeRejectsValuesOutsideOneToTen() {
        assertNull(EffortScale.RPE.toRir(0))
        assertNull(EffortScale.RPE.toRir(11))
    }

    @Test
    fun rirIsStoredAsTyped() {
        assertEquals(3, EffortScale.RIR.display(3))
        assertEquals(3, EffortScale.RIR.toRir(3))
        assertNull(EffortScale.RIR.toRir(11))
    }
}
