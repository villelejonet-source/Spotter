package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.viktorolsson.spotter.core.data.db.entity.ScheduledSessionRow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleDao {
    @Query(
        """
        SELECT s.id, s.date, s.planDayId, d.name AS planDayName, s.notes, s.sessionId,
               (w.endedAt IS NOT NULL) AS done
        FROM scheduled_session s
        LEFT JOIN plan_day d ON d.id = s.planDayId
        LEFT JOIN workout_session w ON w.id = s.sessionId
        WHERE s.date BETWEEN :from AND :to
        ORDER BY s.date, s.id
        """,
    )
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ScheduledSessionRow>>

    /** updatedAt is left alone: the sync trigger stamps the change. */
    @Query("UPDATE scheduled_session SET sessionId = :sessionId WHERE id = :id")
    suspend fun setSession(id: Long, sessionId: Long)

    @Query("SELECT planDayId FROM scheduled_session WHERE id = :id")
    suspend fun getPlanDayId(id: Long): Long?
}
