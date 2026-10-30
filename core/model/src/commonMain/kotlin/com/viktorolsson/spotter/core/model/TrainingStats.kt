package com.viktorolsson.spotter.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

// The Today dashboard's numbers, the same rules as the website's (web/src/lib/stats.ts) and
// pinned by core/model/fixtures/training-math.json. Weeks start on Monday, as on the website.

/** A finished workout, reduced to what the dashboard counts: its local date and its volume. */
data class TrainedDay(val date: LocalDate, val volumeKg: Double)

data class TrainingWeek(val start: LocalDate, val workouts: Int, val volumeKg: Double)

/** Monday of the week containing [date]. */
fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** The Monday [weeks] - 1 weeks before this week's: the oldest week [weeklyTotals] returns. */
fun windowStart(weeks: Int, today: LocalDate): LocalDate = weekStart(today).minus(weeks - 1, DateTimeUnit.WEEK)

/** The last [weeks] weeks up to and including this one, oldest first, empty weeks included. */
fun weeklyTotals(days: List<TrainedDay>, weeks: Int, today: LocalDate): List<TrainingWeek> {
    val first = windowStart(weeks, today)
    val byWeek = days.groupBy { weekStart(it.date) }
    return List(weeks) { i ->
        val start = first.plus(i, DateTimeUnit.WEEK)
        val inWeek = byWeek[start].orEmpty()
        TrainingWeek(start, inWeek.size, inWeek.sumOf { it.volumeKg })
    }
}

/**
 * Weeks in a row with at least one workout, ending this week, or last week while this one
 * has none yet (a streak isn't broken until the week is over).
 */
fun weekStreak(dates: Collection<LocalDate>, today: LocalDate): Int {
    val weeks = dates.mapTo(HashSet(), ::weekStart)
    var week = weekStart(today)
    if (week !in weeks) week = week.minus(1, DateTimeUnit.WEEK)
    var streak = 0
    while (week in weeks) {
        streak++
        week = week.minus(1, DateTimeUnit.WEEK)
    }
    return streak
}

/** The change from [previous] to [current] in whole percent, or null when there's nothing to compare with. */
fun percentChange(current: Double, previous: Double): Int? =
    if (previous == 0.0) null else ((current - previous) / previous * 100).roundToInt()

/** "+12%", "−8%" (a true minus sign), "±0%". */
fun formatChange(percent: Int): String = when {
    percent == 0 -> "±0%"
    percent > 0 -> "+$percent%"
    else -> "−${abs(percent)}%"
}
