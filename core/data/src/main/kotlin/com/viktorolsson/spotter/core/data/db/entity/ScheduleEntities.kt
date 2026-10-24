package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A workout planned for [date]: a plan day, or a free workout when [planDayId] is null.
 * [sessionId] is set once it's started. Deleting the plan day or the workout keeps the
 * entry (it becomes a free or not-yet-done workout).
 */
@Entity(
    tableName = "scheduled_session",
    foreignKeys = [
        ForeignKey(PlanDayEntity::class, ["id"], ["planDayId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("syncId", unique = true), Index("updatedAt"), Index("date"), Index("planDayId"), Index("sessionId")],
)
data class ScheduledSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val planDayId: Long?,
    val notes: String?,
    val sessionId: Long?,
    /** Global id for cloud sync; assigned by a database trigger (see SyncSchema). */
    val syncId: String? = null,
    /** Time of the last local change (ms), maintained by a database trigger. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
)

/** A scheduled entry with its plan day's name and whether its workout is finished. */
data class ScheduledSessionRow(
    val id: Long,
    val date: LocalDate,
    val planDayId: Long?,
    val planDayName: String?,
    val notes: String?,
    val sessionId: Long?,
    val done: Boolean,
)
