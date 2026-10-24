package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.ScheduleDao
import com.viktorolsson.spotter.core.data.db.entity.ScheduledSessionRow
import com.viktorolsson.spotter.core.model.ScheduledWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Workouts planned on dates (made on the website's calendar and synced here). */
@Singleton
class ScheduleRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    /** Scheduled workouts from today through the next [days] - 1 days. */
    fun observeUpcoming(days: Long = 7): Flow<List<ScheduledWorkout>> {
        val today = LocalDate.now(clock)
        return dao.observeRange(today, today.plusDays(days - 1)).map { rows -> rows.map(ScheduledSessionRow::toModel) }
    }

    /**
     * Starts the scheduled workout (its plan day, or an empty workout) and links it, so the
     * entry shows as done here and on the website. A workout already in progress is
     * returned instead, as everywhere else.
     */
    suspend fun start(scheduled: ScheduledWorkout): Long {
        workouts.observeActiveSession().first()?.let { return it.id }
        val planDayId = dao.getPlanDayId(scheduled.id)
        val sessionId = if (planDayId != null) workouts.startPlannedWorkout(planDayId) else workouts.startEmptyWorkout()
        dao.setSession(scheduled.id, sessionId)
        return sessionId
    }
}

private fun ScheduledSessionRow.toModel() = ScheduledWorkout(
    id = id,
    date = date,
    planDayId = planDayId,
    planDayName = planDayName,
    notes = notes,
    sessionId = sessionId,
    done = done,
)
