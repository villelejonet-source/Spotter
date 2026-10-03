package com.viktorolsson.spotter.core.data.repository

import androidx.room.withTransaction
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.PersonalRecordEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.data.db.entity.WorkoutSessionEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.db.toPrevious
import com.viktorolsson.spotter.core.engine.Deload
import com.viktorolsson.spotter.core.engine.ExerciseBests
import com.viktorolsson.spotter.core.engine.WarmUps
import com.viktorolsson.spotter.core.engine.LoggedSet
import com.viktorolsson.spotter.core.engine.PersonalRecords
import com.viktorolsson.spotter.core.engine.Progression
import com.viktorolsson.spotter.core.engine.ProgressionInput
import com.viktorolsson.spotter.core.engine.WeightConversion
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
    private val planDao: PlanDao,
    private val exerciseDao: ExerciseDao,
    private val preferences: UserPreferencesRepository,
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

    /**
     * Starts the given plan day (or returns the workout already in progress). Each
     * exercise keeps its plan link, rest and superset, and its sets are pre-filled with
     * the progression target computed from the last two sessions of that exercise
     * (or the plan's calibration estimate the first time).
     */
    suspend fun startPlannedWorkout(planDayId: Long): Long = db.withTransaction {
        dao.getActiveSession()?.let { return@withTransaction it.id }
        val day = planDao.getDay(planDayId) ?: error("No plan day $planDayId")
        val prefs = preferences.preferences.first()
        val unit = prefs.weightUnit
        val deload = prefs.isDeload(LocalDate.now(clock))
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                startedAt = clock.instant(),
                endedAt = null,
                planDayId = planDayId,
                notes = null,
                perceivedDifficulty = null,
                isDeload = deload,
            ),
        )
        day.exercises.sortedBy { it.planExercise.position }.forEachIndexed { position, (pe, exercise) ->
            val progression = Progression.next(
                ProgressionInput(
                    exercise = exercise.toModel(),
                    rule = pe.progressionRule,
                    sets = pe.sets,
                    repMin = pe.repMin,
                    repMax = pe.repMax,
                    targetRir = pe.targetRir,
                    history = recentHistory(pe.exerciseId),
                    startingWeightKg = pe.startingWeightKg,
                    unit = unit,
                ),
            )
            val target = if (deload) Deload.lighten(progression, exercise.toModel(), pe.repMin, unit) else progression
            val sessionExerciseId = dao.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = pe.exerciseId,
                    position = position,
                    substitutedFromExerciseId = null,
                    supersetGroup = pe.supersetGroup,
                    notes = null,
                    restSeconds = pe.restSeconds,
                    planExerciseId = pe.id,
                    progressionReason = target.reason,
                ),
            )
            dao.insertSets(
                target.sets.mapIndexed { i, set ->
                    newSet(sessionExerciseId, position = i, weightKg = set.weightKg, reps = set.reps, setType = SetType.WORKING)
                },
            )
        }
        sessionId
    }

    /** Working sets of the last two sessions with [exerciseId], most recent first. */
    private suspend fun recentHistory(exerciseId: String): List<List<LoggedSet>> =
        dao.getRecentWorkingSets(exerciseId, sessions = 2)
            .groupBy { it.sessionId }
            .values
            .map { sets -> sets.map { LoggedSet(it.set.weightKg, it.set.reps ?: 0, it.set.rir) } }

    /**
     * Swaps an exercise for another (busy or missing equipment). Remaining sets get a
     * weight from the substitute's own history, or one converted from the original's
     * (e.g. barbell → dumbbell). Sets already ticked stay with the original, and the
     * substitute continues as a new exercise right after it. [replaceInPlan] also
     * changes the plan, so future sessions use the substitute.
     */
    suspend fun swapExercise(sessionExerciseId: Long, newExerciseId: String, replaceInPlan: Boolean) = db.withTransaction {
        val current = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        if (current.exerciseId == newExerciseId) return@withTransaction
        val original = exerciseDao.getById(current.exerciseId)?.toModel() ?: return@withTransaction
        val replacement = exerciseDao.getById(newExerciseId)?.toModel() ?: return@withTransaction
        val unit = preferences.preferences.first().weightUnit

        val sets = dao.getSets(sessionExerciseId)
        val (done, todo) = sets.partition { it.completedAt != null }
        val referenceKg = todo.firstOrNull { it.weightKg != null }?.weightKg ?: done.lastOrNull { it.weightKg != null }?.weightKg
        val ownLast = recentHistory(newExerciseId).firstOrNull()
        val newWeightKg = ownLast?.mapNotNull { it.weightKg }?.maxOrNull()
            ?: referenceKg?.let { WeightConversion.convert(original, it, replacement, unit) }
        // Swapping back to the original clears the substitution.
        val substitutedFrom = (current.substitutedFromExerciseId ?: current.exerciseId).takeIf { it != newExerciseId }

        val targetId = if (done.isEmpty()) {
            dao.updateSessionExercises(
                listOf(current.copy(exerciseId = newExerciseId, substitutedFromExerciseId = substitutedFrom, progressionReason = null)),
            )
            current.id
        } else {
            val later = dao.getSessionExercises(current.sessionId).filter { it.position > current.position }
            dao.updateSessionExercises(later.map { it.copy(position = it.position + 1) })
            dao.insertSessionExercise(
                current.copy(
                    id = 0,
                    exerciseId = newExerciseId,
                    position = current.position + 1,
                    substitutedFromExerciseId = substitutedFrom,
                    notes = null,
                    progressionReason = null,
                ),
            )
        }

        val remaining = todo.ifEmpty { listOfNotNull(done.lastOrNull()?.copy(id = 0, completedAt = null, notes = null)) }
        val moved = remaining.mapIndexed { i, set ->
            set.copy(sessionExerciseId = targetId, position = if (done.isEmpty()) set.position else i, weightKg = newWeightKg)
        }
        dao.updateSets(moved.filter { it.id != 0L })
        dao.insertSets(moved.filter { it.id == 0L })

        val planExerciseId = current.planExerciseId
        if (replaceInPlan && planExerciseId != null) planDao.replaceExercise(planExerciseId, newExerciseId, newWeightKg)
    }

    /** Last session's working sets of [exerciseId] (warm-ups left out), for the "previous" column. */
    suspend fun getPreviousSets(exerciseId: String): List<PreviousSet> =
        dao.getPreviousSets(exerciseId).filter { it.setType != SetType.WARMUP }.map { it.toPrevious() }

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

    /**
     * Adds a warm-up ramp before the working sets, based on the first working set's
     * weight. Does nothing if warm-ups are already there or the weight isn't set.
     * Returns how many sets were added.
     */
    suspend fun addWarmUps(sessionExerciseId: Long, extended: Boolean): Int = db.withTransaction {
        val entry = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction 0
        val sets = dao.getSets(sessionExerciseId)
        if (sets.any { it.setType == SetType.WARMUP }) return@withTransaction 0
        val working = sets.firstOrNull { it.weightKg != null }?.weightKg ?: return@withTransaction 0
        val exercise = exerciseDao.getById(entry.exerciseId)?.toModel() ?: return@withTransaction 0
        val unit = preferences.preferences.first().weightUnit
        val ramp = WarmUps.generate(working, exercise, unit, extended)
        if (ramp.isEmpty()) return@withTransaction 0
        dao.updateSets(sets.map { it.copy(position = it.position + ramp.size) })
        dao.insertSets(ramp.mapIndexed { i, t -> newSet(sessionExerciseId, i, t.weightKg, t.reps, SetType.WARMUP) })
        ramp.size
    }

    /** Best results for [exerciseId] from sessions before [before]; the baseline for live PRs. */
    suspend fun bestsBefore(exerciseId: String, before: Instant): ExerciseBests =
        PersonalRecords.bests(
            dao.getWorkingSetsBefore(exerciseId, before).groupBy { it.sessionId }.values
                .map { sets -> sets.map { LoggedSet(it.set.weightKg, it.set.reps ?: 0, it.set.rir) } },
        )

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

    /**
     * Ends the session, dropping sets that were never ticked and exercises left with no
     * sets, then records any personal records it set.
     */
    suspend fun finishWorkout(sessionId: Long) = db.withTransaction {
        dao.deleteIncompleteSets(sessionId)
        dao.deleteExercisesWithoutSets(sessionId)
        val now = clock.instant()
        dao.endSession(sessionId, now)
        recordPersonalRecords(sessionId, now)
    }

    private suspend fun recordPersonalRecords(sessionId: Long, achievedAt: Instant) {
        val session = dao.getSessionWithExercises(sessionId) ?: return
        val records = session.exercises.groupBy { it.exercise.id }.flatMap { (exerciseId, entries) ->
            val logged = entries.flatMap { it.sets }
                .filter { it.completedAt != null && it.setType != SetType.WARMUP && it.reps != null }
                .map { LoggedSet(it.weightKg, it.reps!!, it.rir) }
            val previous = dao.getWorkingSetsBefore(exerciseId, session.session.startedAt)
                .groupBy { it.sessionId }.values
                .map { sets -> sets.map { LoggedSet(it.set.weightKg, it.set.reps ?: 0, it.set.rir) } }
            PersonalRecords.detect(logged, PersonalRecords.bests(previous)).map {
                PersonalRecordEntity(
                    exerciseId = exerciseId,
                    type = it.type,
                    value = it.value,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    achievedAt = achievedAt,
                    sessionId = sessionId,
                )
            }
        }
        if (records.isNotEmpty()) dao.insertRecords(records)
    }

    /**
     * "Repeat this workout": starts an empty workout with the same exercises (or
     * returns the one in progress). Sets pre-fill from the latest time each was done.
     */
    suspend fun repeatWorkout(sessionId: Long): Long = db.withTransaction {
        dao.getActiveSession()?.let { return@withTransaction it.id }
        val source = dao.getSessionWithExercises(sessionId) ?: error("No session $sessionId")
        val newId = startEmptyWorkout()
        addExercises(newId, source.exercises.sortedBy { it.sessionExercise.position }.map { it.exercise.id }.distinct())
        newId
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
