package com.viktorolsson.spotter.core.data.repository

import androidx.room.withTransaction
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.db.toPrevious
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every edit is written straight to Room, so an in-progress workout survives a
 * crash, a phone call or the process being killed. There is no "save" step.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val db: SpotterDatabase,
    private val dao: WorkoutDao,
    private val clock: Clock,
) {
    fun observeActiveSession(): Flow<ActiveSession?> =
        dao.observeActiveSession().map { it?.let { s -> ActiveSession(s.id, s.startedAt) } }

    fun observeSession(sessionId: Long): Flow<WorkoutSession?> =
        dao.observeSessionWithExercises(sessionId).map { it?.toModel() }

    suspend fun getSession(sessionId: Long): WorkoutSession? = dao.getSessionWithExercises(sessionId)?.toModel()

    /** Starts an empty workout, or returns the one already in progress (only one can be active). */
    suspend fun startEmptyWorkout(): Long = db.withTransaction {
        dao.getActiveSession()?.id ?: dao.insertSession(
            WorkoutSessionEntity(
                startedAt = clock.instant(),
                endedAt = null,
                planDayId = null,
                notes = null,
                perceivedDifficulty = null,
            ),
        )
    }

    suspend fun getPreviousSets(exerciseId: String): List<PreviousSet> =
        dao.getPreviousSets(exerciseId).map { it.toPrevious() }

    /**
     * Appends exercises to the session. Sets are pre-filled from the last time each
     * exercise was done (same count, weight and reps), or [defaultSetCount] empty sets.
     */
    suspend fun addExercises(sessionId: Long, exerciseIds: List<String>, defaultSetCount: Int = 3) =
        db.withTransaction {
            var position = dao.nextExercisePosition(sessionId)
            exerciseIds.forEach { exerciseId ->
                val sessionExerciseId = dao.insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        position = position++,
                        substitutedFromExerciseId = null,
                        supersetGroup = null,
                        notes = null,
                    ),
                )
                val previous = dao.getPreviousSets(exerciseId)
                val sets = if (previous.isNotEmpty()) {
                    previous.mapIndexed { i, prev ->
                        newSet(sessionExerciseId, i, prev.weightKg, prev.reps, prev.setType)
                    }
                } else {
                    List(defaultSetCount) { i -> newSet(sessionExerciseId, i, null, null, SetType.WORKING) }
                }
                dao.insertSets(sets)
            }
        }

    suspend fun removeExercise(sessionExerciseId: Long) = db.withTransaction {
        val removed = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        dao.deleteSessionExercise(sessionExerciseId)
        renumberExercises(removed.sessionId)
    }

    suspend fun moveExercise(sessionExerciseId: Long, offset: Int) = db.withTransaction {
        val moving = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val list = dao.getSessionExercises(moving.sessionId).toMutableList()
        val from = list.indexOfFirst { it.id == sessionExerciseId }
        val to = (from + offset).coerceIn(0, list.lastIndex)
        if (from == to) return@withTransaction
        list.add(to, list.removeAt(from))
        dao.updateSessionExercises(list.mapIndexed { i, e -> e.copy(position = i) })
    }

    suspend fun setExerciseNotes(sessionExerciseId: Long, notes: String?) = updateExercise(sessionExerciseId) {
        it.copy(notes = notes?.takeIf(String::isNotBlank))
    }

    suspend fun setExerciseRest(sessionExerciseId: Long, restSeconds: Int) = updateExercise(sessionExerciseId) {
        it.copy(restSeconds = restSeconds.coerceIn(0, 60 * 10))
    }

    /**
     * Links this exercise with the next one into a superset (or extends the
     * group the next one is in). Rest only starts after the last exercise of a group.
     */
    suspend fun supersetWithNext(sessionExerciseId: Long) = db.withTransaction {
        val current = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val list = dao.getSessionExercises(current.sessionId)
        val next = list.getOrNull(list.indexOfFirst { it.id == sessionExerciseId } + 1) ?: return@withTransaction
        val group = current.supersetGroup ?: next.supersetGroup ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
        val oldNextGroup = next.supersetGroup
        dao.updateSessionExercises(
            list.map {
                when {
                    it.id == current.id || it.id == next.id -> it.copy(supersetGroup = group)
                    oldNextGroup != null && it.supersetGroup == oldNextGroup -> it.copy(supersetGroup = group)
                    else -> it
                }
            },
        )
    }

    suspend fun removeFromSuperset(sessionExerciseId: Long) = db.withTransaction {
        val current = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val group = current.supersetGroup ?: return@withTransaction
        val members = dao.getSessionExercises(current.sessionId).filter { it.supersetGroup == group }
        // A group of two dissolves entirely; otherwise only this exercise leaves.
        val updated = if (members.size <= 2) members.map { it.copy(supersetGroup = null) }
        else listOf(current.copy(supersetGroup = null))
        dao.updateSessionExercises(updated)
    }

    /** Adds a set that copies the last set's weight, reps and type. */
    suspend fun addSet(sessionExerciseId: Long) = db.withTransaction {
        val sets = dao.getSets(sessionExerciseId)
        val last = sets.lastOrNull()
        dao.insertSets(
            listOf(
                newSet(
                    sessionExerciseId,
                    position = (last?.position ?: -1) + 1,
                    weightKg = last?.weightKg,
                    reps = last?.reps,
                    setType = last?.setType?.takeIf { it != SetType.WARMUP } ?: SetType.WORKING,
                ),
            ),
        )
    }

    suspend fun deleteSet(setId: Long) = db.withTransaction {
        val set = dao.getSet(setId) ?: return@withTransaction
        dao.deleteSet(setId)
        dao.updateSets(dao.getSets(set.sessionExerciseId).mapIndexed { i, s -> s.copy(position = i) })
    }

    suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, rir: Int?) = updateSet(setId) {
        it.copy(weightKg = weightKg, reps = reps, rir = rir)
    }

    suspend fun setSetType(setId: Long, type: SetType) = updateSet(setId) { it.copy(setType = type) }

    suspend fun setSetNotes(setId: Long, notes: String?) = updateSet(setId) {
        it.copy(notes = notes?.takeIf(String::isNotBlank))
    }

    /**
     * Ticks a set off (or un-ticks it). When ticking, later untouched sets of the
     * same exercise inherit its weight and reps, so the next set is usually one tap.
     * Returns true if the set is now completed.
     */
    suspend fun toggleSetCompleted(setId: Long): Boolean = db.withTransaction {
        val set = dao.getSet(setId) ?: return@withTransaction false
        if (set.completedAt != null) {
            dao.updateSets(listOf(set.copy(completedAt = null)))
            return@withTransaction false
        }
        val completed = set.copy(completedAt = clock.instant())
        val following = dao.getSets(set.sessionExerciseId)
            .filter { it.position > set.position && it.completedAt == null && it.weightKg == null && it.reps == null }
            .map { it.copy(weightKg = set.weightKg, reps = set.reps) }
        dao.updateSets(listOf(completed) + following)
        true
    }

    suspend fun setSessionNotes(sessionId: Long, notes: String?) =
        dao.updateSessionNotes(sessionId, notes?.takeIf(String::isNotBlank))

    /** Ends the session, dropping sets that were never ticked and exercises left with no sets. */
    suspend fun finishWorkout(sessionId: Long) = db.withTransaction {
        dao.deleteIncompleteSets(sessionId)
        dao.deleteExercisesWithoutSets(sessionId)
        dao.endSession(sessionId, clock.instant())
    }

    suspend fun discardWorkout(sessionId: Long) = dao.deleteSession(sessionId)

    private suspend fun updateSet(setId: Long, change: (SetEntryEntity) -> SetEntryEntity) {
        val set = dao.getSet(setId) ?: return
        dao.updateSets(listOf(change(set)))
    }

    private suspend fun updateExercise(id: Long, change: (SessionExerciseEntity) -> SessionExerciseEntity) {
        val exercise = dao.getSessionExercise(id) ?: return
        dao.updateSessionExercises(listOf(change(exercise)))
    }

    private suspend fun renumberExercises(sessionId: Long) {
        dao.updateSessionExercises(dao.getSessionExercises(sessionId).mapIndexed { i, e -> e.copy(position = i) })
    }

    private fun newSet(sessionExerciseId: Long, position: Int, weightKg: Double?, reps: Int?, setType: SetType) =
        SetEntryEntity(
            sessionExerciseId = sessionExerciseId,
            position = position,
            weightKg = weightKg,
            reps = reps,
            rir = null,
            rpe = null,
            setType = setType,
            completedAt = null,
            notes = null,
        )
}

data class ActiveSession(val id: Long, val startedAt: Instant)
