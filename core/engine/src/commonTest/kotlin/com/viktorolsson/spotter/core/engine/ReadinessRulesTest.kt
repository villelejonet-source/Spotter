package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.ReadinessLevel
import com.viktorolsson.spotter.core.model.ReadinessReason
import com.viktorolsson.spotter.core.model.RecoveryNight
import com.viktorolsson.spotter.core.model.RecoverySignal
import com.viktorolsson.spotter.core.model.SessionAdvice
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReadinessRulesTest {
    private val today = LocalDate(2026, 11, 16)

    /** [days] earlier nights of 7 h 30 min, HRV 50 ms and 55 bpm, then last night as given. */
    private fun history(
        days: Int = 14,
        sleep: Duration? = 7.hours + 30.minutes,
        hrv: Double? = 50.0,
        resting: Double? = 55.0,
        before: (Int) -> RecoveryNight? = { null },
    ) = (1..days).map { back ->
        before(back) ?: RecoveryNight(today.minus(DatePeriod(days = back)), 7.hours + 30.minutes, 50.0, 55.0)
    } + RecoveryNight(today, sleep, hrv, resting)

    private fun assess(nights: List<RecoveryNight>) = ReadinessRules.assess(nights, today)

    @Test
    fun `a normal night is good and the workout stays as planned`() {
        val readiness = assess(history())!!
        assertEquals(ReadinessLevel.GOOD, readiness.level)
        assertEquals(SessionAdvice.AS_PLANNED, readiness.advice)
        assertEquals(emptyList(), readiness.reasons)
        assertEquals(emptySet(), readiness.missing)
        assertEquals(4, readiness.level.checkIn)
    }

    @Test
    fun `an hour under the usual is reduced but still as planned`() {
        val readiness = assess(history(sleep = 6.hours + 20.minutes))!!
        assertEquals(ReadinessLevel.REDUCED, readiness.level)
        assertEquals(SessionAdvice.AS_PLANNED, readiness.advice)
        assertEquals(listOf(ReadinessReason.ShortSleep(6.hours + 20.minutes, 70.minutes)), readiness.reasons)
    }

    @Test
    fun `a very short night suggests a lighter session`() {
        val readiness = assess(history(sleep = 4.hours + 50.minutes))!!
        assertEquals(ReadinessLevel.LOW, readiness.level)
        assertEquals(SessionAdvice.LIGHTER, readiness.advice)
        assertEquals(2, readiness.level.checkIn)
    }

    @Test
    fun `a short night with low hrv and a raised resting heart rate suggests rest`() {
        val readiness = assess(history(sleep = 5.hours + 10.minutes, hrv = 38.0, resting = 63.0))!!
        assertEquals(ReadinessLevel.VERY_LOW, readiness.level)
        assertEquals(SessionAdvice.REST_OR_LIGHT, readiness.advice)
        assertEquals(
            listOf(
                ReadinessReason.ShortSleep(5.hours + 10.minutes, 140.minutes),
                ReadinessReason.LowHrv(24),
                ReadinessReason.HighRestingHeartRate(8),
            ),
            readiness.reasons,
        )
    }

    @Test
    fun `hrv a little under the usual only reduces`() {
        val readiness = assess(history(hrv = 44.0))!!
        assertEquals(ReadinessLevel.REDUCED, readiness.level)
        assertEquals(listOf(ReadinessReason.LowHrv(12)), readiness.reasons)
    }

    @Test
    fun `three shortish nights in a row add up`() {
        val short = 6.hours + 40.minutes
        val readiness = assess(history(sleep = 6.hours + 10.minutes) { back -> if (back <= 2) RecoveryNight(today.minus(DatePeriod(days = back)), short, 50.0, 55.0) else null })!!
        // Last night is 80 minutes under (1 point) and the three nights average 60 under (1 more).
        assertEquals(ReadinessLevel.LOW, readiness.level)
        assertEquals(ReadinessReason.SleepDebt(60.minutes), readiness.reasons.last())
    }

    @Test
    fun `without enough history only the length of the night counts`() {
        val few = history(days = 3, sleep = 6.hours + 20.minutes, hrv = 30.0, resting = 70.0)
        assertEquals(ReadinessLevel.GOOD, assess(few)!!.level)
        val short = assess(history(days = 3, sleep = 5.hours + 30.minutes, hrv = 30.0, resting = 70.0))!!
        assertEquals(ReadinessLevel.REDUCED, short.level)
        assertEquals(listOf(ReadinessReason.ShortSleep(5.hours + 30.minutes, null)), short.reasons)
    }

    @Test
    fun `signals the watch did not record are named as missing`() {
        val readiness = assess(history(hrv = null, resting = null))!!
        assertEquals(ReadinessLevel.GOOD, readiness.level)
        assertEquals(setOf(RecoverySignal.HRV, RecoverySignal.RESTING_HEART_RATE), readiness.missing)
    }

    @Test
    fun `nothing for last night gives no readiness`() {
        assertNull(assess(history().dropLast(1)))
        assertNull(assess(history(sleep = null, hrv = null, resting = null)))
    }

    @Test
    fun `nights older than the baseline window are left out`() {
        // Seven good nights long ago don't make a usual; the recent three aren't enough.
        val old = (40..46).map { RecoveryNight(today.minus(DatePeriod(days = it)), 9.hours, 80.0, 45.0) }
        val readiness = assess(old + history(days = 3, sleep = 7.hours))!!
        assertEquals(ReadinessLevel.GOOD, readiness.level)
    }
}
