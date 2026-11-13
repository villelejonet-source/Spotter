package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.viktorolsson.spotter.core.data.db.entity.FinishedSessionRow
import com.viktorolsson.spotter.core.data.db.entity.HealthExportEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface HealthExportDao {
    /**
     * The finished workouts the health store should have: those ended since [since], and any
     * saved before. `state` changes when what's saved from a workout does (its end and its
     * sets), so feel, effort and notes don't count, and sets that a sync delivers after their
     * session do. Emits on every such change, made here or by a sync.
     */
    @Query(
        """
        SELECT s.id, s.syncId, s.endedAt + COALESCE((
            SELECT SUM(st.updatedAt) FROM set_entry st
            JOIN session_exercise se ON se.id = st.sessionExerciseId WHERE se.sessionId = s.id
        ), 0) AS state
        FROM workout_session s
        WHERE s.endedAt IS NOT NULL AND s.syncId IS NOT NULL
            AND (s.endedAt >= :since OR s.syncId IN (SELECT sessionSyncId FROM health_export))
        ORDER BY s.endedAt
        """,
    )
    fun observeFinishedSessions(since: Instant): Flow<List<FinishedSessionRow>>

    @Query("SELECT * FROM health_export")
    suspend fun getAll(): List<HealthExportEntity>

    @Upsert
    suspend fun upsert(export: HealthExportEntity)

    @Query("DELETE FROM health_export WHERE sessionSyncId = :sessionSyncId")
    suspend fun delete(sessionSyncId: String)
}
