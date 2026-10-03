package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.RecordWithExercise
import com.viktorolsson.spotter.core.data.db.entity.SessionWithExercises
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.engine.MuscleBalance
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Read side of workouts: finished sessions, per-exercise history, records and weekly volume. */
@Singleton
class HistoryRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val clock: Clock,
) {
    fun observeFinishedSessions(): Flow<List<WorkoutSession>> =
        dao.observeFinishedSessions().map { it.map(SessionWithExercises::toModel) }

    /** Each session the exercise was done in, oldest first. */
    fun observeExerciseLogs(exerciseId: String): Flow<List<ExerciseSessionLog>> =
        dao.observeExerciseSets(exerciseId).map { rows ->
            rows.groupBy { it.sessionId }.map { (sessionId, sets) ->
                ExerciseSessionLog(sessionId, sets.first().startedAt, sets.map { it.set.toModel() })
            }
        }

    fun observeLoggedExercises(): Flow<List<LoggedExerciseSummary>> =
        dao.observeLoggedExercises().map { rows ->
            rows.map { LoggedExerciseSummary(it.exercise.toModel(), it.lastDone, it.sessionCount) }
        }

    fun observeRecentRecords(limit: Int = 10): Flow<List<PersonalRecord>> =
        dao.observeRecentRecords(limit).map { it.map(RecordWithExercise::toModel) }

    fun observeSessionRecords(sessionId: Long): Flow<List<PersonalRecord>> =
        dao.observeSessionRecords(sessionId).map { it.map(RecordWithExercise::toModel) }

    fun observeExerciseRecords(exerciseId: String): Flow<List<PersonalRecord>> =
        dao.observeExerciseRecords(exerciseId).map { it.map(RecordWithExercise::toModel) }

    /** Working sets per muscle group over the last 7 days, against the goal's target. */
    fun observeWeeklyVolume(goal: Goal?): Flow<List<MuscleVolume>> =
        dao.observeFinishedSessionsSince(clock.instant().minus(Duration.ofDays(7))).map { sessions ->
            val sets: List<Exercise> = sessions.flatMap { session ->
                session.exercises.flatMap { ex ->
                    val exercise = ex.exercise.toModel()
                    ex.sets.filter { it.completedAt != null && it.setType != SetType.WARMUP }.map { exercise }
                }
            }
            MuscleBalance.weekly(sets, goal)
        }
}

data class LoggedExerciseSummary(val exercise: Exercise, val lastDone: Instant, val sessionCount: Int)
