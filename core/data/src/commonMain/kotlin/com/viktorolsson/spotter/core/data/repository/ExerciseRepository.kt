package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.toEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.engine.ExerciseSimilarity
import com.viktorolsson.spotter.core.engine.SwapCandidate
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = exerciseDao.observeAll().map { it.map(ExerciseEntity::toModel) }

    suspend fun getById(id: String): Exercise? = exerciseDao.getById(id)?.toModel()

    /**
     * Exercises whose name contains every word of [query] (any order, case-insensitive),
     * narrowed by [filter]. The match is one precompiled regex per query and runs off the
     * main thread, so typing in the picker never does the work on the UI thread.
     */
    fun search(query: String, filter: (Exercise) -> Boolean = { true }): Flow<List<Exercise>> {
        val matches = ExerciseSearch.matcher(query)
        return exerciseDao.observeAll()
            .map { rows -> rows.asSequence().map(ExerciseEntity::toModel).filter { matches(it.name) && filter(it) }.toList() }
            .flowOn(Dispatchers.Default)
    }

    /**
     * Ranked alternatives for [original] (busy or missing equipment), scored on Default so
     * the swap sheet opens without blocking the UI. See [ExerciseSimilarity.rank].
     */
    suspend fun rankSwapCandidates(
        original: Exercise,
        availableEquipment: Set<Equipment>,
        limitations: Set<Limitation> = emptySet(),
        exclude: Set<String> = emptySet(),
    ): List<SwapCandidate> {
        val library = exerciseDao.observeAll().first().map(ExerciseEntity::toModel)
        return withContext(Dispatchers.Default) {
            ExerciseSimilarity.rank(original, library, availableEquipment, limitations, exclude)
        }
    }

    /** Creates a user-defined exercise and returns its id. */
    suspend fun createCustom(
        name: String,
        primaryMuscle: Muscle,
        pattern: MovementPattern,
        equipment: List<Equipment>,
    ): String {
        val exercise = Exercise(
            id = "custom-${Uuid.random()}",
            name = name.trim(),
            primaryMuscles = listOf(primaryMuscle),
            secondaryMuscles = emptyList(),
            movementPattern = pattern,
            equipment = equipment,
            mechanics = if (pattern in compoundPatterns) Mechanics.COMPOUND else Mechanics.ISOLATION,
            difficulty = Difficulty.BEGINNER,
            unilateral = false,
            instructions = null,
            isCustom = true,
        )
        exerciseDao.insert(exercise.toEntity())
        return exercise.id
    }

    private val compoundPatterns = setOf(
        MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL,
        MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE,
        MovementPattern.HIP_THRUST, MovementPattern.CARRY,
    )
}

/** Name matching for the exercise picker: every word of the query must appear in the name. */
object ExerciseSearch {
    /** A matcher over exercise names, compiled once per query; a blank query matches everything. */
    fun matcher(query: String): (String) -> Boolean {
        val words = query.trim().split(WHITESPACE).filter(String::isNotEmpty)
        if (words.isEmpty()) return { true }
        // (?=.*word) for each word: one pass over the name, any word order.
        val regex = Regex(words.joinToString("") { "(?=.*${Regex.escape(it)})" }, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return { name -> regex.containsMatchIn(name) }
    }

    private val WHITESPACE = Regex("\\s+")
}
