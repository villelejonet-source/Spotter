package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.model.RecoveryNight
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A sleep session as a health store has it: when it ended and how much of it was sleep. */
data class SleepSample(val end: Instant, val asleep: Duration)

/** One reading of heart rate variability (ms) or resting heart rate (bpm). */
data class HealthReading(val time: Instant, val value: Double)

/**
 * Sorts a health store's raw records into nights, the same way on every platform:
 * - sleep belongs to the day it ended on, and the longest session of that day is the night
 *   (a nap, or the same night written by two apps, must not add to it);
 * - heart rate variability is measured during sleep, so a reading from the evening on
 *   (after 18:00) belongs to the next morning; the night's readings are averaged;
 * - resting heart rate is a figure for the day it's dated, averaged when there are several.
 */
fun recoveryNights(
    sleep: List<SleepSample>,
    hrv: List<HealthReading>,
    resting: List<HealthReading>,
    zone: TimeZone,
): List<RecoveryNight> {
    val sleepByDay = sleep.groupBy { it.end.toLocalDateTime(zone).date }.mapValues { (_, sessions) -> sessions.maxOf { it.asleep } }
    val hrvByDay = hrv.groupBy { (it.time + EVENING).toLocalDateTime(zone).date }.mapValues { (_, readings) -> readings.map { it.value }.average() }
    val restingByDay = resting.groupBy { it.time.toLocalDateTime(zone).date }.mapValues { (_, readings) -> readings.map { it.value }.average() }
    return (sleepByDay.keys + hrvByDay.keys + restingByDay.keys).sorted().map { day ->
        RecoveryNight(day, sleepByDay[day], hrvByDay[day], restingByDay[day])
    }
}

/**
 * The stretch of time a store must be asked for to get the nights that ended on [from]
 * through [to]: it starts the evening before the first one, when that night's readings begin.
 */
fun recoveryWindow(from: LocalDate, to: LocalDate, zone: TimeZone): ClosedRange<Instant> =
    (from.atStartOfDayIn(zone) - EVENING)..to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)

/** Added to a reading's time so that one taken after 18:00 lands on the next day. */
private val EVENING = 6.hours
