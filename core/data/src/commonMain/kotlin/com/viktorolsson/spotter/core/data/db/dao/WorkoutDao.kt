package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.viktorolsson.spotter.core.data.db.entity.CheckInRow
import com.viktorolsson.spotter.core.data.db.entity.DatedSet
import com.viktorolsson.spotter.core.data.db.entity.ExerciseHistorySet
import com.viktorolsson.spotter.core.data.db.entity.ExerciseSessionRef
import com.viktorolsson.spotter.core.data.db.entity.HistorySet
import com.viktorolsson.spotter.core.data.db.entity.LoggedExercise
import com.viktorolsson.spotter.core.data.db.entity.PersonalRecordEntity
import com.viktorolsson.spotter.core.data.db.entity.PlayedSessionRow
import com.viktorolsson.spotter.core.data.db.entity.RecordWithExercise
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseIdRow
import com.viktorolsson.spotter.core.data.db.entity.SessionRecordRow
import com.viktorolsson.spotter.core.data.db.entity.SessionWithExercises
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

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

    @Query("UPDATE workout_session SET feel = :feel WHERE id = :sessionId")
    suspend fun updateSessionFeel(sessionId: Long, feel: Int?)

    @Query("UPDATE workout_session SET perceivedDifficulty = :effort WHERE id = :sessionId")
    suspend fun updateSessionEffort(sessionId: Long, effort: Int?)

    @Query("UPDATE workout_session SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Instant)

    @Query("DELETE FROM workout_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT endedAt IS NOT NULL FROM workout_session WHERE id = :sessionId")
    suspend fun isSessionFinished(sessionId: Long): Boolean?

    @Query("DELETE FROM personal_record WHERE sessionId = :sessionId")
    suspend fun deleteSessionRecords(sessionId: Long)

    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinishedSessions(): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL AND startedAt >= :since ORDER BY startedAt DESC")
    fun observeFinishedSessionsSince(since: Instant): Flow<List<SessionWithExercises>>

    /** Finished sessions started in [start, end), newest first. */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL AND startedAt >= :start AND startedAt < :end ORDER BY startedAt DESC")
    fun observeFinishedSessionsBetween(start: Instant, end: Instant): Flow<List<SessionWithExercises>>

    @Query("SELECT COUNT(*) FROM workout_session WHERE endedAt IS NOT NULL")
    fun observeFinishedCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentFinished(limit: Int): Flow<List<SessionWithExercises>>

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
        DELETE FROM session_exercise WHERE sessionId = :sessionId
          AND NOT EXISTS (SELECT 1 FROM set_entry se WHERE se.sessionExerciseId = session_exercise.id)
        """,
    )
    suspend fun deleteExercisesWithoutSets(sessionId: Long)

    // --- Batched history for a set of exercises (one or two queries, not one per exercise) ---

    /** Each finished session that included one of [exerciseIds], newest first. */
    @Query(
        """
        SELECT DISTINCT sx.exerciseId AS exerciseId, ws.id AS sessionId, ws.startedAt AS startedAt, ws.isDeload AS isDeload,
            ws.readiness AS readiness, ws.feel AS feel
        FROM workout_session ws
        JOIN session_exercise sx ON sx.sessionId = ws.id
        WHERE sx.exerciseId IN (:exerciseIds) AND ws.endedAt IS NOT NULL
        ORDER BY ws.startedAt DESC
        """,
    )
    suspend fun getExerciseSessionRefs(exerciseIds: List<String>): List<ExerciseSessionRef>

    /** Completed sets of [exerciseIds] in [sessionIds], newest session first, in logged order. */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId, sx.exerciseId AS exerciseId, ws.startedAt AS startedAt FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.sessionId IN (:sessionIds) AND sx.exerciseId IN (:exerciseIds) AND se.completedAt IS NOT NULL
        ORDER BY ws.startedAt DESC, sx.position, se.position
        """,
    )
    suspend fun getCompletedSets(sessionIds: List<Long>, exerciseIds: List<String>): List<ExerciseHistorySet>

    /** Completed working sets of [exerciseIds] from sessions finished before [before], oldest first. */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId, sx.exerciseId AS exerciseId, ws.startedAt AS startedAt FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.exerciseId IN (:exerciseIds) AND ws.endedAt IS NOT NULL AND ws.startedAt < :before
          AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
        ORDER BY ws.startedAt, sx.position, se.position
        """,
    )
    suspend fun getWorkingSetsBeforeFor(exerciseIds: List<String>, before: Instant): List<ExerciseHistorySet>

    /**
     * Completed working sets for [exerciseId] from its last [sessions] finished
     * non-deload sessions, most recent session first.
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
            WHERE sx2.exerciseId = :exerciseId AND ws2.endedAt IS NOT NULL AND ws2.isDeload = 0
            GROUP BY ws2.id ORDER BY ws2.startedAt DESC LIMIT :sessions
          )
        ORDER BY ws.startedAt DESC, sx.position, se.position
        """,
    )
    suspend fun getRecentWorkingSets(exerciseId: String, sessions: Int): List<HistorySet>

    /** All completed sets of [exerciseId] from finished sessions, oldest first. */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId, ws.startedAt AS startedAt,
            ws.isDeload AS isDeload, ws.readiness AS readiness, ws.feel AS feel FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL AND se.completedAt IS NOT NULL
        ORDER BY ws.startedAt, sx.position, se.position
        """,
    )
    fun observeExerciseSets(exerciseId: String): Flow<List<DatedSet>>

    /** Completed working sets of [exerciseId] from sessions finished before [before], oldest first. */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL AND ws.startedAt < :before
          AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
        ORDER BY ws.startedAt, sx.position, se.position
        """,
    )
    suspend fun getWorkingSetsBefore(exerciseId: String, before: Instant): List<HistorySet>

    /** Exercises with logged history, most recently done first. */
    @Query(
        """
        SELECT e.*, MAX(ws.startedAt) AS lastDone, COUNT(DISTINCT ws.id) AS sessionCount FROM exercise e
        JOIN session_exercise sx ON sx.exerciseId = e.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE ws.endedAt IS NOT NULL
        GROUP BY e.id ORDER BY lastDone DESC
        """,
    )
    fun observeLoggedExercises(): Flow<List<LoggedExercise>>

    /**
     * Working sets of [exerciseId] from finished sessions that count towards change, oldest first
     * (plateau detection and lift trends): no lighter sessions and no rough days (isRoughDay).
     */
    @Query(
        """
        SELECT se.*, sx.sessionId AS sessionId, ws.startedAt AS startedAt,
            ws.isDeload AS isDeload, ws.readiness AS readiness, ws.feel AS feel FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE sx.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL AND ws.isDeload = 0
          AND COALESCE(ws.readiness, 5) > 2 AND COALESCE(ws.feel, 5) > 2
          AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
        ORDER BY ws.startedAt, sx.position, se.position
        """,
    )
    suspend fun getExposureSets(exerciseId: String): List<DatedSet>

    @Query("SELECT startedAt FROM workout_session WHERE endedAt IS NOT NULL AND isDeload = 0 ORDER BY startedAt")
    suspend fun getTrainingStarts(): List<Instant>

    /**
     * The latest session of a deload week: a lighter session with another lighter one within a
     * week of it. A single lighter day chosen at the check-in isn't a deload week, so it doesn't
     * reset the weeks since the last one.
     */
    @Query(
        """
        SELECT MAX(ws.startedAt) FROM workout_session ws
        WHERE ws.endedAt IS NOT NULL AND ws.isDeload = 1 AND EXISTS (
            SELECT 1 FROM workout_session other
            WHERE other.id != ws.id AND other.endedAt IS NOT NULL AND other.isDeload = 1
              AND ABS(other.startedAt - ws.startedAt) < 7 * 86400000
        )
        """,
    )
    suspend fun getLastDeloadSession(): Instant?

    /** Check-ins (before) and ratings (after) of finished workouts since [since], low or not. */
    @Query("SELECT readiness, feel FROM workout_session WHERE endedAt IS NOT NULL AND startedAt >= :since")
    suspend fun getCheckInsSince(since: Instant): List<CheckInRow>

    @Query("SELECT readiness, feel FROM workout_session WHERE endedAt IS NOT NULL AND startedAt >= :since")
    fun observeCheckInsSince(since: Instant): Flow<List<CheckInRow>>

    // --- Levels and badges ---

    /** One row per finished workout; [timedIds] are the holds, which add no volume. */
    @Query(
        """
        SELECT ws.id, ws.startedAt, ws.endedAt, ws.isDeload, ws.readiness, ws.feel,
            COUNT(se.id) AS workingSets,
            COALESCE(SUM(CASE WHEN sx.exerciseId IN (:timedIds) THEN 0
                ELSE COALESCE(se.weightKg, 0) * COALESCE(se.reps, 0) END), 0) AS volumeKg
        FROM workout_session ws
        LEFT JOIN session_exercise sx ON sx.sessionId = ws.id
        LEFT JOIN set_entry se ON se.sessionExerciseId = sx.id
            AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
        WHERE ws.endedAt IS NOT NULL
        GROUP BY ws.id
        """,
    )
    fun observePlayedSessions(timedIds: List<String>): Flow<List<PlayedSessionRow>>

    @Query(
        """
        SELECT DISTINCT sx.sessionId, sx.exerciseId FROM session_exercise sx
        JOIN workout_session ws ON sx.sessionId = ws.id
        JOIN set_entry se ON se.sessionExerciseId = sx.id
        WHERE ws.endedAt IS NOT NULL AND se.completedAt IS NOT NULL AND se.setType != 'WARMUP'
        """,
    )
    fun observePlayedExercises(): Flow<List<SessionExerciseIdRow>>

    @Query("SELECT sessionId, type FROM personal_record WHERE sessionId IS NOT NULL")
    fun observeRecordTypes(): Flow<List<SessionRecordRow>>

    @Query(
        """
        SELECT se.rir FROM set_entry se
        JOIN session_exercise sx ON se.sessionExerciseId = sx.id
        JOIN workout_session ws ON sx.sessionId = ws.id
        WHERE ws.endedAt IS NOT NULL AND ws.isDeload = 0 AND ws.startedAt >= :since
          AND se.completedAt IS NOT NULL AND se.rir IS NOT NULL AND se.setType != 'WARMUP'
        """,
    )
    suspend fun getRirSince(since: Instant): List<Int>

    // --- Personal records ---

    @Insert
    suspend fun insertRecords(records: List<PersonalRecordEntity>)

    @Transaction
    @Query("SELECT * FROM personal_record ORDER BY achievedAt DESC LIMIT :limit")
    fun observeRecentRecords(limit: Int): Flow<List<RecordWithExercise>>

    @Transaction
    @Query("SELECT * FROM personal_record WHERE sessionId = :sessionId")
    fun observeSessionRecords(sessionId: Long): Flow<List<RecordWithExercise>>

    @Transaction
    @Query("SELECT * FROM personal_record WHERE exerciseId = :exerciseId ORDER BY achievedAt DESC")
    fun observeExerciseRecords(exerciseId: String): Flow<List<RecordWithExercise>>

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
