package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.toEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = exerciseDao.observeAll().map { it.map(ExerciseEntity::toModel) }

    fun observeCount(): Flow<Int> = exerciseDao.observeCount()

    suspend fun getById(id: String): Exercise? = exerciseDao.getById(id)?.toModel()

    /** Creates a user-defined exercise and returns its id. */
    suspend fun createCustom(
        name: String,
        primaryMuscle: Muscle,
        pattern: MovementPattern,
        equipment: List<Equipment>,
    ): String {
        val exercise = Exercise(
            id = "custom-${UUID.randomUUID()}",
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
