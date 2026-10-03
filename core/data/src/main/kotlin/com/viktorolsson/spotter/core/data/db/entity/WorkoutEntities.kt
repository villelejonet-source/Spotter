package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.SetType
import java.time.Instant

/** A session with [endedAt] = null is in progress; the stopwatch is derived from [startedAt]. */
@Entity(
    tableName = "workout_session",
    foreignKeys = [ForeignKey(PlanDayEntity::class, ["id"], ["planDayId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("syncId", unique = true), Index("planDayId"), Index("startedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant?,
    val planDayId: Long?,
    val notes: String?,
    /** 1–10 session RPE, optional. */
    val perceivedDifficulty: Int?,
    /** Lighter deload-week session; excluded from progression and plateau history. */
    @ColumnInfo(defaultValue = "0")
    val isDeload: Boolean = false,
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(ExerciseEntity::class, ["id"], ["substitutedFromExerciseId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(PlanExerciseEntity::class, ["id"], ["planExerciseId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("syncId", unique = true), Index("sessionId"), Index("exerciseId"), Index("substitutedFromExerciseId"), Index("planExerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val position: Int,
    val substitutedFromExerciseId: String?,
    val supersetGroup: Int?,
    val notes: String?,
    /** Rest after each set; null falls back to the plan default, then the user default. */
    @ColumnInfo(defaultValue = "NULL")
    val restSeconds: Int? = null,
    /** The plan prescription this exercise was started from, if any. */
    @ColumnInfo(defaultValue = "NULL")
    val planExerciseId: Long? = null,
    /** Why the pre-filled targets are what they are (planned workouts only). */
    @ColumnInfo(defaultValue = "NULL")
    val progressionReason: ProgressionReason? = null,
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)

/** Weights are always stored in kg; the UI converts to the user's unit. */
@Entity(
    tableName = "set_entry",
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("syncId", unique = true), Index("sessionExerciseId")],
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
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("syncId", unique = true), Index("exerciseId", "type"), Index("sessionId")],
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
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "recommendation",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("syncId", unique = true), Index("exerciseId"), Index("status")],
)
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecommendationType,
    val exerciseId: String?,
    /** Type-specific JSON (e.g. accessory ids, suggested sets, the evidence shown on the card). */
    val payload: String,
    val createdAt: Instant,
    val status: RecommendationStatus,
    /** When it was applied or dismissed. */
    @ColumnInfo(defaultValue = "NULL")
    val resolvedAt: Instant? = null,
    /** Identifies "the same suggestion" across refreshes (type + what it targets). */
    @ColumnInfo(defaultValue = "''")
    val dedupKey: String = "",
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)
