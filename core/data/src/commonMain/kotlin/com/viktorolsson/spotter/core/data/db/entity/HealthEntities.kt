package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A workout this phone has saved to its health store, and which version of it. It describes
 * this phone only, so it has no sync columns and isn't in `SyncSchema.tables`.
 */
@Entity(tableName = "health_export")
data class HealthExportEntity(
    /** `workout_session.syncId`; no foreign key, so the row outlives the session and its record can be removed. */
    @PrimaryKey val sessionSyncId: String,
    /** The workout's [FinishedSessionRow.state] when it was saved; a different one now means save it again. */
    val state: Long,
)

/** A finished session as the export sees it: enough to tell that what's saved from it changed. */
data class FinishedSessionRow(val id: Long, val syncId: String, val state: Long)
