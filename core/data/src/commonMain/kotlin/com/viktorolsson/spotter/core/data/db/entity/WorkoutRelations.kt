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

/** A logged set with its session and exercise, for history read for several exercises at once. */
data class ExerciseHistorySet(
    @Embedded val set: SetEntryEntity,
    val sessionId: Long,
    val exerciseId: String,
    val startedAt: kotlin.time.Instant,
)

/** One (exercise, finished session) pair; the session's start and whether it was a deload. */
data class ExerciseSessionRef(
    val exerciseId: String,
    val sessionId: Long,
    val startedAt: kotlin.time.Instant,
    val isDeload: Boolean,
    val readiness: Int? = null,
    val feel: Int? = null,
)

/** A logged set with its session's start time, for per-exercise history. */
data class DatedSet(
    @Embedded val set: SetEntryEntity,
    val sessionId: Long,
    val startedAt: kotlin.time.Instant,
    /** The session's lighter flag and check-ins, to tell lighter sessions and rough days apart. */
    val isDeload: Boolean,
    val readiness: Int?,
    val feel: Int?,
)

data class RecordWithExercise(
    @Embedded val record: PersonalRecordEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/** An exercise the user has logged, with when it was last done. */
data class LoggedExercise(
    @Embedded val exercise: ExerciseEntity,
    val lastDone: kotlin.time.Instant,
    val sessionCount: Int,
)

/** A finished workout's check-in before and rating after, 1–5 each, either possibly skipped. */
data class CheckInRow(val readiness: Int?, val feel: Int?)

/** A finished workout's totals for levels and badges (core/engine Gamification.kt). */
data class PlayedSessionRow(
    val id: Long,
    val startedAt: kotlin.time.Instant,
    val endedAt: kotlin.time.Instant,
    val isDeload: Boolean,
    val readiness: Int?,
    val feel: Int?,
    /** Completed sets that aren't warm-ups. */
    val workingSets: Int,
    /** As WorkoutSummary.volumeKg: completed working sets, holds left out. */
    val volumeKg: Double,
)

/** An exercise with at least one completed working set in a finished workout. */
data class SessionExerciseIdRow(val sessionId: Long, val exerciseId: String)

data class SessionRecordRow(val sessionId: Long, val type: com.viktorolsson.spotter.core.model.PersonalRecordType)
