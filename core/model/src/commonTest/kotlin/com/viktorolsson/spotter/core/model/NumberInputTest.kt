package com.viktorolsson.spotter.core.model

import com.viktorolsson.spotter.core.model.NumberInput.Blank
import com.viktorolsson.spotter.core.model.NumberInput.Invalid
import com.viktorolsson.spotter.core.model.NumberInput.Value
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberInputTest {
    @Test
    fun decimalCommaAndPointGiveTheSameNumber() {
        assertEquals(Value(62.5), NumberInput.decimal("62,5"))
        assertEquals(Value(62.5), NumberInput.decimal("62.5"))
        assertEquals(Value(62.5), NumberInput.decimal(" 62,5 "))
        assertEquals(Value(102.25), NumberInput.decimal("102,25"))
    }

    @Test
    fun partlyTypedDecimalsAreNumbers() {
        assertEquals(Value(62.0), NumberInput.decimal("62,"))
        assertEquals(Value(62.0), NumberInput.decimal("62."))
        assertEquals(Value(0.5), NumberInput.decimal(",5"))
        assertEquals(Value(0.0), NumberInput.decimal("0"))
    }

    @Test
    fun blankIsBlank() {
        assertEquals(Blank, NumberInput.decimal(""))
        assertEquals(Blank, NumberInput.decimal("   "))
        assertEquals(Blank, NumberInput.whole(""))
    }

    @Test
    fun anythingElseIsInvalid() {
        listOf(".", ",", "1,2,3", "1.2.3", "62,5kg", "-1", "+1", "1e3", "NaN", "Infinity", "1 000", "abc", "0x10")
            .forEach { assertEquals(Invalid, NumberInput.decimal(it), "decimal(\"$it\")") }
    }

    @Test
    fun wholeNumbersRejectDecimals() {
        assertEquals(Value(8.0), NumberInput.whole(" 8 "))
        assertEquals(Invalid, NumberInput.whole("8,0"))
        assertEquals(Invalid, NumberInput.whole("8.5"))
        assertEquals(Invalid, NumberInput.whole("-3"))
        assertEquals(Invalid, NumberInput.whole("99999999999"))
    }

    @Test
    fun orNullHelpers() {
        assertEquals(62.5, NumberInput.decimalOrNull("62,5"))
        assertEquals(null, NumberInput.decimalOrNull("."))
        assertEquals(12, NumberInput.wholeOrNull("12"))
        assertEquals(null, NumberInput.wholeOrNull(""))
    }
}
