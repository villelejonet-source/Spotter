package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A deleted synced row, recorded by a trigger so the deletion reaches other devices. */
@Entity(tableName = "sync_tombstone", primaryKeys = ["tableName", "syncId"])
data class SyncTombstoneEntity(
    val tableName: String,
    val syncId: String,
    val deletedAt: Long,
)

/**
 * Single row ([id] = 1). While [applying] is 1 the sync triggers stand down, so rows
 * written by a pull keep their remote timestamps and don't echo back as local changes.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val applying: Int = 0,
)
