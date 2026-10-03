package com.viktorolsson.spotter.core.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One synced record as stored in Supabase (`public.sync_rows`). */
@Serializable
data class SyncRow(
    @SerialName("table_name") val tableName: String,
    @SerialName("sync_id") val syncId: String,
    /** Column values, with local foreign keys replaced by the parent's sync id; null when deleted. */
    val payload: JsonObject? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
)

/** A row as read back, with the server timestamp used as the pull cursor. */
@Serializable
data class RemoteRow(
    @SerialName("table_name") val tableName: String,
    @SerialName("sync_id") val syncId: String,
    val payload: JsonObject? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
) {
    fun toSyncRow() = SyncRow(tableName, syncId, payload, updatedAt, deletedAt)
}

/** The server side of sync. Implemented with Supabase; faked in tests. */
interface SyncRemote {
    /** Whether the signed-in user has anything backed up. */
    suspend fun hasRows(): Boolean

    /** Rows changed after [since] (null: everything), oldest first, one page at a time. */
    suspend fun pull(since: String?, offset: Int, limit: Int): List<RemoteRow>

    /** Uploads changes; the server keeps whichever version is newer. */
    suspend fun push(rows: List<SyncRow>)
}

/** What to do the first time a phone with its own data meets a non-empty backup. */
enum class FirstSyncChoice {
    /** Replace this phone's data with the backup (e.g. a new phone that went through setup). */
    USE_BACKUP,
    /** Keep both; the newest version of each record wins. */
    MERGE,
}
