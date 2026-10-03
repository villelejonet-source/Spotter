package com.viktorolsson.spotter.core.data.db

import com.viktorolsson.spotter.core.data.db.entity.ExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanDayWithExercises
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanWithDays
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.db.entity.SessionExerciseWithSets
import com.viktorolsson.spotter.core.data.db.entity.SessionWithExercises
import com.viktorolsson.spotter.core.data.db.entity.SetEntryEntity
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.PlanTarget
import com.viktorolsson.spotter.core.model.UserProfile
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
    planDayName = planDay?.name,
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
    target = planExercise?.toTarget(),
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

fun PlanWithDays.toModel() = Plan(
    id = plan.id,
    name = plan.name,
    splitType = plan.splitType,
    goal = plan.goal,
    createdAt = plan.createdAt,
    days = days.sortedBy { it.day.position }.map(PlanDayWithExercises::toModel),
)

fun PlanDayWithExercises.toModel() = PlanDay(
    id = day.id,
    position = day.position,
    name = day.name,
    exercises = exercises.sortedBy { it.planExercise.position }.map { (pe, exercise) ->
        PlanExercise(
            id = pe.id,
            exercise = exercise.toModel(),
            position = pe.position,
            sets = pe.sets,
            repMin = pe.repMin,
            repMax = pe.repMax,
            targetRir = pe.targetRir,
            restSeconds = pe.restSeconds,
            progressionRule = pe.progressionRule,
            supersetGroup = pe.supersetGroup,
            startingWeightKg = pe.startingWeightKg,
        )
    },
)

fun PlanExerciseEntity.toTarget() = PlanTarget(sets = sets, repMin = repMin, repMax = repMax, targetRir = targetRir)

fun UserProfileEntity.toModel() = UserProfile(
    sex = sex,
    heightCm = heightCm,
    birthDate = birthDate,
    bodyWeightKg = bodyWeightKg,
    units = units,
    experience = experience,
    goal = goal,
    daysPerWeek = daysPerWeek,
    sessionLengthMinutes = sessionLengthMinutes,
    equipment = equipment,
    focusAreas = focusAreas,
    limitations = limitations,
)

fun UserProfile.toEntity() = UserProfileEntity(
    sex = sex,
    heightCm = heightCm,
    birthDate = birthDate,
    bodyWeightKg = bodyWeightKg,
    units = units,
    experience = experience,
    goal = goal,
    daysPerWeek = daysPerWeek,
    sessionLengthMinutes = sessionLengthMinutes,
    equipment = equipment,
    focusAreas = focusAreas,
    limitations = limitations,
)
