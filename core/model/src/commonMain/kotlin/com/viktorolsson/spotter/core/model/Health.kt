package com.viktorolsson.spotter.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/** Whether the phone has a health store Spotter can use (Health Connect on Android). */
enum class HealthAvailability {
    AVAILABLE,

    /** The phone supports it, but its app must be installed or updated first. */
    NEEDS_INSTALL,

    UNSUPPORTED,
}

/** A finished workout as the phone's health store gets it. */
data class HealthWorkout(
    /** The session's sync id: the same workout always replaces its own record. */
    val id: String,
    /** Higher than the last one written for this workout: the store ignores a write that isn't. */
    val version: Long,
    val title: String?,
    val start: Instant,
    val end: Instant,
    /** Estimated active energy; null when the profile can't give one. */
    val activeKcal: Int?,
)

/** What the health store knows about one night: [date] is the morning it ended on. */
data class RecoveryNight(
    val date: LocalDate,
    /** Time asleep, awake stretches left out when the source gives them. */
    val sleep: Duration? = null,
    /** Heart rate variability (RMSSD, milliseconds), averaged over the night. */
    val hrvMs: Double? = null,
    val restingBpm: Double? = null,
)

enum class RecoverySignal { SLEEP, HRV, RESTING_HEART_RATE }

/** How recovered the person looks this morning; [checkIn] is the matching answer to "How do you feel today?". */
enum class ReadinessLevel(val checkIn: Int) { GOOD(4), REDUCED(3), LOW(2), VERY_LOW(1) }

/** What to do with today's planned workout. A suggestion: the person can always overrule it. */
enum class SessionAdvice { AS_PLANNED, LIGHTER, REST_OR_LIGHT }

/** One thing that pulled readiness down, with the numbers to say it in plain words. */
sealed interface ReadinessReason {
    /** Last night was short; [underUsual] is set when it was also well under the person's usual. */
    data class ShortSleep(val slept: Duration, val underUsual: Duration?) : ReadinessReason

    /** The last three nights averaged this much under the usual. */
    data class SleepDebt(val underUsualPerNight: Duration) : ReadinessReason

    data class LowHrv(val percentUnderUsual: Int) : ReadinessReason

    data class HighRestingHeartRate(val bpmOverUsual: Int) : ReadinessReason
}

/** This morning's readiness, from the health store's data (`ReadinessRules` in `core/engine`). */
data class Readiness(
    val level: ReadinessLevel,
    /** Empty when nothing stands out. */
    val reasons: List<ReadinessReason>,
    /** Signals the health store had nothing for last night, so the picture is partial. */
    val missing: Set<RecoverySignal>,
    /** Last night's sleep, for the summary line. */
    val slept: Duration?,
) {
    /** Lighter exactly when the matching check-in is a low one, as when the person answers for themselves. */
    val advice: SessionAdvice
        get() = when {
            level == ReadinessLevel.VERY_LOW -> SessionAdvice.REST_OR_LIGHT
            level.checkIn <= LOW_CHECK_IN -> SessionAdvice.LIGHTER
            else -> SessionAdvice.AS_PLANNED
        }
}
