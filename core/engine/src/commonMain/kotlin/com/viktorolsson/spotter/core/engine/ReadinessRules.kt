package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Readiness
import com.viktorolsson.spotter.core.model.ReadinessLevel
import com.viktorolsson.spotter.core.model.ReadinessReason
import com.viktorolsson.spotter.core.model.RecoveryNight
import com.viktorolsson.spotter.core.model.RecoverySignal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How recovered the person is this morning, from what the phone's health store recorded
 * overnight: sleep, heart rate variability and resting heart rate. Each is compared with the
 * person's own usual (the median of the nights before), never with a population norm, and
 * each can add points; the points give the level. Plain rules with fixed thresholds, so the
 * app can say exactly why. A training suggestion, not a medical reading.
 */
object ReadinessRules {
    /** Nights before last night that go into "your usual". */
    const val BASELINE_DAYS = 28

    /**
     * Null when there's nothing for last night (the night that ended on [today]): no watch
     * worn, or not synced yet. A signal with fewer than [MIN_BASELINE_NIGHTS] earlier nights
     * has no usual yet; sleep then counts by its length alone, the other two not at all.
     */
    fun assess(nights: List<RecoveryNight>, today: LocalDate): Readiness? {
        val last = nights.firstOrNull { it.date == today } ?: return null
        val missing = buildSet {
            if (last.sleep == null) add(RecoverySignal.SLEEP)
            if (last.hrvMs == null) add(RecoverySignal.HRV)
            if (last.restingBpm == null) add(RecoverySignal.RESTING_HEART_RATE)
        }
        if (missing.size == RecoverySignal.entries.size) return null
        val earlier = nights.filter { it.date < today && it.date >= today.minus(DatePeriod(days = BASELINE_DAYS)) }
        val usualSleep: Duration? = usual(earlier.mapNotNull { it.sleep?.inWholeSeconds?.toDouble() })?.seconds
        val usualHrv = usual(earlier.mapNotNull { it.hrvMs })
        val usualResting = usual(earlier.mapNotNull { it.restingBpm })

        var points = 0
        val reasons = mutableListOf<ReadinessReason>()
        val slept = last.sleep
        if (slept != null) {
            val under: Duration? = usualSleep?.minus(slept)
            val sleepPoints = when {
                slept < VERY_SHORT_SLEEP || (under != null && under >= WELL_UNDER_USUAL) -> 2
                slept < SHORT_SLEEP || (under != null && under >= UNDER_USUAL) -> 1
                else -> 0
            }
            if (sleepPoints > 0) {
                points += sleepPoints
                reasons += ReadinessReason.ShortSleep(slept, under?.takeIf { it >= UNDER_USUAL })
            }
            // The two nights before as well: one short night is covered above, a run of them adds up.
            val before = listOf(1, 2).mapNotNull { back -> earlier.firstOrNull { it.date == today.minus(DatePeriod(days = back)) }?.sleep }
            if (usualSleep != null && before.size == 2) {
                val debt = usualSleep - (before[0] + before[1] + slept) / 3
                if (debt >= UNDER_USUAL) {
                    points += 1
                    reasons += ReadinessReason.SleepDebt(debt)
                }
            }
        }
        val hrv = last.hrvMs
        if (hrv != null && usualHrv != null && usualHrv > 0) {
            val under = (usualHrv - hrv) / usualHrv
            if (under >= HRV_UNDER) {
                points += if (under >= HRV_WELL_UNDER) 2 else 1
                reasons += ReadinessReason.LowHrv((under * 100).roundToInt())
            }
        }
        val resting = last.restingBpm
        if (resting != null && usualResting != null) {
            val over = resting - usualResting
            if (over >= RESTING_OVER) {
                points += if (over >= RESTING_WELL_OVER) 2 else 1
                reasons += ReadinessReason.HighRestingHeartRate(over.roundToInt())
            }
        }
        val level = when {
            points == 0 -> ReadinessLevel.GOOD
            points == 1 -> ReadinessLevel.REDUCED
            points < VERY_LOW_POINTS -> ReadinessLevel.LOW
            else -> ReadinessLevel.VERY_LOW
        }
        return Readiness(level, reasons, missing, last.sleep)
    }

    /** The median, once there are enough nights to call it usual. */
    private fun usual(values: List<Double>): Double? {
        if (values.size < MIN_BASELINE_NIGHTS) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    const val MIN_BASELINE_NIGHTS = 7
    private val VERY_SHORT_SLEEP = 5.hours
    private val SHORT_SLEEP = 6.hours
    private val UNDER_USUAL = 60.minutes
    private val WELL_UNDER_USUAL = 2.hours
    private const val HRV_UNDER = 0.10
    private const val HRV_WELL_UNDER = 0.20
    private const val RESTING_OVER = 4.0
    private const val RESTING_WELL_OVER = 7.0
    private const val VERY_LOW_POINTS = 4
}
