package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.viktorolsson.spotter.core.data.db.entity.HistorySet
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionWithExercises
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WorkoutDao {
    // --- Sessions ---

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    fun observeSessionWithExercises(sessionId: Long): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    suspend fun getSessionWithExercises(sessionId: Long): SessionWithExercises?

    @Query("UPDATE workout_session SET notes = :notes WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: Long, notes: String?)

    @Query("UPDATE workout_session SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Instant)

    @Query("DELETE FROM workout_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    // --- Exercises in a session ---

    @Insert
    suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Update
    suspend fun updateSessionExercises(exercises: List<SessionExerciseEntity>)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM session_exercise WHERE sessionId = :sessionId")
    suspend fun nextExercisePosition(sessionId: Long): Int

    @Query("DELETE FROM session_exercise WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    // --- Sets ---

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>): List<Long>

    @Query("SELECT * FROM set_entry WHERE id = :id")
    suspend fun getSet(id: Long): SetEntryEntity?

    @Query("SELECT * FROM set_entry WHERE sessionExerciseId = :sessionExerciseId ORDER BY position")
    suspend fun getSets(sessionExerciseId: Long): List<SetEntryEntity>

    @Update
    suspend fun updateSets(sets: List<SetEntryEntity>)

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query(
        """
        DELETE FROM set_entry WHERE completedAt IS NULL AND sessionExerciseId IN
            (SELECT id FROM session_exercise WHERE sessionId = :sessionId)
        """,
    )
    suspend fun deleteIncompleteSets(sessionId: Long)

    @Query(
        """
        DELETE FROM session_exercise WHERE sessionId = :sessionId AND id NOT IN
            (SELECT sessionExerciseId FROM set_entry)
        """,
    )
    suspend fun deleteExercisesWithoutSets(sessionId: Long)

    /**
     * Completed working sets for [exerciseId] from its last [sessions] finished
     * sessions, most recent session first.
     */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.exerciseId = :exerciseId AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
          AND ws.id IN (
            SELECT ws2.id FROM workout_session ws2
            JOIN session_exercise sx2 ON sx2.sessionId = ws2.id
            WHERE sx2.exerciseId = :exerciseId AND ws2.endedAt IS NOT NULL
            GROUP BY ws2.id ORDER BY ws2.startedAt DESC LIMIT :sessions
          )
        ORDER BY ws.startedAt DESC, sx.position, se.position
        """,
    )
    suspend fun getRecentWorkingSets(exerciseId: String, sessions: Int): List<HistorySet>

    /**
     * Completed sets for [exerciseId] from the most recent finished session that
     * included it, in the order they were logged.
     */
    @Query(
        """
        SELECT se.* FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        WHERE sx.exerciseId = :exerciseId AND se.completedAt IS NOT NULL AND sx.sessionId = (
            SELECT ws.id FROM workout_session ws
            JOIN session_exercise sx2 ON sx2.sessionId = ws.id
            WHERE sx2.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL
            ORDER BY ws.startedAt DESC LIMIT 1
        )
        ORDER BY sx.position, se.position
        """,
    )
    suspend fun getPreviousSets(exerciseId: String): List<SetEntryEntity>
}
