package com.viktorolsson.spotter.core.model

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
    /** Goes up when the workout changes, so a newer write replaces an older one. */
    val version: Long,
    val title: String?,
    val start: Instant,
    val end: Instant,
    /** Estimated active energy; null when the profile can't give one. */
    val activeKcal: Int?,
)
