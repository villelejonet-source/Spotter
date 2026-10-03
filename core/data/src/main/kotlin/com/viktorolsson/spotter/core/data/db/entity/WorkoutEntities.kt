package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.SetType
import java.time.Instant

/** A session with [endedAt] = null is in progress; the stopwatch is derived from [startedAt]. */
@Entity(
    tableName = "workout_session",
    foreignKeys = [ForeignKey(PlanDayEntity::class, ["id"], ["planDayId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("planDayId"), Index("startedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant?,
    val planDayId: Long?,
    val notes: String?,
    /** 1–10 session RPE, optional. */
    val perceivedDifficulty: Int?,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(ExerciseEntity::class, ["id"], ["substitutedFromExerciseId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index("substitutedFromExerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val position: Int,
    val substitutedFromExerciseId: String?,
    val supersetGroup: Int?,
    val notes: String?,
)

/** Weights are always stored in kg; the UI converts to the user's unit. */
@Entity(
    tableName = "set_entry",
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionExerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rir: Int?,
    val rpe: Double?,
    val setType: SetType,
    /** Null until the set is ticked off. */
    val completedAt: Instant?,
    val notes: String?,
)

@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("exerciseId", "type"), Index("sessionId")],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val type: PersonalRecordType,
    val value: Double,
    val weightKg: Double?,
    val reps: Int?,
    val achievedAt: Instant,
    val sessionId: Long?,
)

@Entity(
    tableName = "recommendation",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId"), Index("status")],
)
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecommendationType,
    val exerciseId: String?,
    /** Type-specific JSON (e.g. accessory ids, suggested sets, the evidence shown on the card). */
    val payload: String,
    val createdAt: Instant,
    val status: RecommendationStatus,
)
