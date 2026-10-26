package com.viktorolsson.spotter.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The locale-independent formatting, the same on Android and iOS. */
class FormattingTest {
    @Test
    fun clockPadsMinutesAndSeconds() {
        assertEquals("0:45", formatClock(45))
        assertEquals("12:05", formatClock(725))
        assertEquals("1:02:09", formatClock(3729))
        assertEquals("0:00", formatClock(-5))
    }

    @Test
    fun oneDecimalRoundsHalfUpWithAPoint() {
        assertEquals("72.4", formatOneDecimalHalfUp(72.4))
        assertEquals("72.5", formatOneDecimalHalfUp(72.46))
        assertEquals("80.0", formatOneDecimalHalfUp(79.96))
        assertEquals("0.3", formatOneDecimalHalfUp(0.25))
    }

    @Test
    fun localFormatPadsWholeNumbers() {
        assertEquals("7:03", "%d:%02d".localFormat(7, 3))
        assertEquals("100%", "%d%%".localFormat(100))
    }
}
