package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "planDayId", entityColumn = "id")
    val planDay: PlanDayEntity?,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetEntryEntity>,
    @Relation(parentColumn = "planExerciseId", entityColumn = "id")
    val planExercise: PlanExerciseEntity?,
    @Relation(parentColumn = "substitutedFromExerciseId", entityColumn = "id")
    val substitutedFrom: ExerciseEntity?,
)

/** A logged set with the session it belongs to, for grouping history by session. */
data class HistorySet(
    @Embedded val set: SetEntryEntity,
    val sessionId: Long,
)

data class PlanWithDays(
    @Embedded val plan: PlanEntity,
    @Relation(entity = PlanDayEntity::class, parentColumn = "id", entityColumn = "planId")
    val days: List<PlanDayWithExercises>,
)

data class PlanDayWithExercises(
    @Embedded val day: PlanDayEntity,
    @Relation(entity = PlanExerciseEntity::class, parentColumn = "id", entityColumn = "planDayId")
    val exercises: List<PlanExerciseWithExercise>,
)

data class PlanExerciseWithExercise(
    @Embedded val planExercise: PlanExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)
