package com.viktorolsson.spotter.core.data.seed

import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `version` inside `assets/exercises.json`, duplicated here so start-up can skip opening
 * and parsing the asset once that version is installed. Bump both together whenever the
 * file changes (ExerciseSeedTest fails if they drift) so installed apps re-seed.
 */
const val EXERCISE_CATALOG_VERSION = 2

/** Shape of `assets/exercises.json`. Bump [version] whenever the file changes so installed apps re-seed. */
@Serializable
data class ExerciseSeedFile(
    val version: Int,
    val exercises: List<SeedExercise>,
)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle> = emptyList(),
    val pattern: MovementPattern,
    val equipment: List<Equipment> = emptyList(),
    val mechanics: Mechanics,
    val difficulty: Difficulty,
    val unilateral: Boolean = false,
    val instructions: String? = null,
) {
    fun toEntity() = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern,
        equipment = equipment,
        mechanics = mechanics,
        difficulty = difficulty,
        unilateral = unilateral,
        instructions = instructions,
        isCustom = false,
    )
}

internal val seedJson = Json { ignoreUnknownKeys = true }

fun parseExerciseSeed(text: String): ExerciseSeedFile = seedJson.decodeFromString(text)
