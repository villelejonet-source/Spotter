package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.model.RecoveryNight
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RecoverySamplesTest {
    private val stockholm = TimeZone.of("Europe/Stockholm")
    private fun at(text: String) = Instant.parse(text)

    @Test
    fun aNightIsTheDayItEndedOnWithItsReadingsFromTheEveningBefore() {
        val nights = recoveryNights(
            sleep = listOf(SleepSample(at("2026-11-13T05:30:00Z"), 7.hours + 10.minutes)),
            // 23:30 and 03:00 local time: both the night that ended on the 13th.
            hrv = listOf(HealthReading(at("2026-11-12T22:30:00Z"), 40.0), HealthReading(at("2026-11-13T02:00:00Z"), 50.0)),
            resting = listOf(HealthReading(at("2026-11-13T07:00:00Z"), 54.0)),
            zone = stockholm,
        )
        assertEquals(listOf(RecoveryNight(LocalDate(2026, 11, 13), 7.hours + 10.minutes, 45.0, 54.0)), nights)
    }

    @Test
    fun aNapOrTheSameNightFromTwoAppsDoesNotAddToTheNight() {
        val nights = recoveryNights(
            sleep = listOf(
                SleepSample(at("2026-11-13T05:30:00Z"), 7.hours),
                SleepSample(at("2026-11-13T05:35:00Z"), 6.hours + 50.minutes),
                SleepSample(at("2026-11-13T14:00:00Z"), 40.minutes),
            ),
            hrv = emptyList(), resting = emptyList(), zone = stockholm,
        )
        assertEquals(listOf(RecoveryNight(LocalDate(2026, 11, 13), sleep = 7.hours)), nights)
    }

    @Test
    fun nightsWithOnlyOneSignalStillCount() {
        val nights = recoveryNights(
            sleep = emptyList(),
            hrv = listOf(HealthReading(at("2026-11-12T01:00:00Z"), 48.0)),
            resting = listOf(HealthReading(at("2026-11-13T07:00:00Z"), 54.0)),
            zone = stockholm,
        )
        assertEquals(
            listOf(RecoveryNight(LocalDate(2026, 11, 12), hrvMs = 48.0), RecoveryNight(LocalDate(2026, 11, 13), restingBpm = 54.0)),
            nights,
        )
    }

    @Test
    fun theWindowToAskForStartsTheEveningBeforeTheFirstNight() {
        val window = recoveryWindow(LocalDate(2026, 11, 12), LocalDate(2026, 11, 13), stockholm)
        assertEquals(at("2026-11-11T17:00:00Z"), window.start)
        assertEquals(at("2026-11-13T23:00:00Z"), window.endInclusive)
    }
}
