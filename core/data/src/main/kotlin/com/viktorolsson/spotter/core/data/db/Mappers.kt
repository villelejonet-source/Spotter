package com.viktorolsson.spotter.core.data.db

import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseWithSets
import com.viktorolsson.spotter.core.data.db.entity.SessionWithExercises
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSet

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

fun Exercise.toEntity() = ExerciseEntity(
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

fun SessionWithExercises.toModel() = WorkoutSession(
    id = session.id,
    startedAt = session.startedAt,
    endedAt = session.endedAt,
    notes = session.notes,
    exercises = exercises.sortedBy { it.sessionExercise.position }.map(SessionExerciseWithSets::toModel),
)

fun SessionExerciseWithSets.toModel() = SessionExercise(
    id = sessionExercise.id,
    exercise = exercise.toModel(),
    position = sessionExercise.position,
    supersetGroup = sessionExercise.supersetGroup,
    restSeconds = sessionExercise.restSeconds,
    notes = sessionExercise.notes,
    sets = sets.sortedBy { it.position }.map(SetEntryEntity::toModel),
)

fun SetEntryEntity.toModel() = WorkoutSet(
    id = id,
    position = position,
    weightKg = weightKg,
    reps = reps,
    rir = rir,
    setType = setType,
    completedAt = completedAt,
    notes = notes,
)

fun SetEntryEntity.toPrevious() = PreviousSet(weightKg = weightKg, reps = reps)
