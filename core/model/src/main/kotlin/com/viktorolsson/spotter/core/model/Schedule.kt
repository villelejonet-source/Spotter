package com.viktorolsson.spotter.core.model

import java.time.LocalDate

/**
 * A workout planned for a date, from the website's calendar (or later the app). It's a
 * plan day, or a free workout when [planDayId] is null. Starting it links [sessionId].
 */
data class ScheduledWorkout(
    val id: Long,
    val date: LocalDate,
    val planDayId: Long?,
    /** Null for a free workout, or when the plan day was deleted. */
    val planDayName: String?,
    val notes: String?,
    val sessionId: Long?,
    /** The linked workout is finished. */
    val done: Boolean,
)
