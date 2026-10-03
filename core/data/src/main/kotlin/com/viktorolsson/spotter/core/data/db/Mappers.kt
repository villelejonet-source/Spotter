package com.viktorolsson.spotter.core.data.db

import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.model.Exercise

fun ExerciseEntity.toModel() = Exercise(
    id = id,
    name = name,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    movementPattern = movementPattern,
    equipment = equipment,
    mechanics = mechanics,
    difficulty = difficulty,
    unilateral = unilateral,
    instructions = instructions,
    isCustom = isCustom,
)
