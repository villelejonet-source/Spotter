package com.viktorolsson.spotter.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// percentChange, formatChange and weekStreak are pinned by the shared fixture
// (core/engine TrainingMathFixturesTest); these cover the rest.
class TrainingStatsTest {
    private val wednesday = LocalDate(2026, 10, 28)

    @Test
    fun weekStartIsMonday() {
        assertEquals(LocalDate(2026, 10, 26), weekStart(wednesday))
        assertEquals(LocalDate(2026, 10, 26), weekStart(LocalDate(2026, 10, 26)))
        assertEquals(LocalDate(2026, 10, 26), weekStart(LocalDate(2026, 11, 1)))
    }

    @Test
    fun windowStartsWeeksBeforeThisMonday() {
        assertEquals(LocalDate(2026, 10, 26), windowStart(1, wednesday))
        assertEquals(LocalDate(2026, 10, 5), windowStart(4, wednesday))
    }

    @Test
    fun weeklyTotalsBucketByWeekOldestFirst() {
        val days = listOf(
            TrainedDay(LocalDate(2026, 10, 27), 1000.0),
            TrainedDay(LocalDate(2026, 10, 26), 500.0),
            TrainedDay(LocalDate(2026, 10, 18), 800.0),
            // Before the window: ignored.
            TrainedDay(LocalDate(2026, 10, 4), 9999.0),
        )
        val weeks = weeklyTotals(days, 3, wednesday)
        assertEquals(
            listOf(
                TrainingWeek(LocalDate(2026, 10, 12), 1, 800.0),
                TrainingWeek(LocalDate(2026, 10, 19), 0, 0.0),
                TrainingWeek(LocalDate(2026, 10, 26), 2, 1500.0),
            ),
            weeks,
        )
    }
}
