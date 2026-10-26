package com.viktorolsson.spotter.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightFormatTest {
    @Test
    fun `kg values keep at most two decimals and drop trailing zeros`() {
        assertEquals("80", WeightUnit.KG.format(80.0))
        assertEquals("82.5", WeightUnit.KG.format(82.5))
        assertEquals("102.06", WeightUnit.KG.format(102.0625))
        assertEquals("0", WeightUnit.KG.format(0.0))
        assertEquals("0.1", WeightUnit.KG.format(0.1))
        assertEquals("1.05", WeightUnit.KG.format(1.049999))
    }

    @Test
    fun `pounds are converted before rounding`() {
        assertEquals("220.46", WeightUnit.LB.format(100.0))
        assertEquals("45", WeightUnit.LB.format(WeightUnit.LB.toKg(45.0)))
    }

    @Test
    fun `parsing accepts a comma and rejects negatives`() {
        assertEquals(82.5, WeightUnit.KG.parseToKg(" 82,5 "))
        assertEquals(null, WeightUnit.KG.parseToKg("-1"))
    }
}
