package com.viktorolsson.spotter.core.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.SyncSchema
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What [LocalSyncStore.applyAll] did with a batch. */
data class ApplyResult(
    /** Rows that changed locally. */
    val changed: Int,
    /** Rows whose parent isn't here yet; worth retrying once the rest of the pull is in. */
    val deferred: List<SyncRow>,
)

/**
 * Reads local changes out as [SyncRow]s and writes pulled rows back in. Works on any
 * synced table generically: columns come from the cursor, and foreign keys (from
 * `PRAGMA foreign_key_list`) are translated between local ids and global sync ids.
 *
 * Remote payloads are untrusted: local primary keys in them are ignored (autoincrement
 * tables get their own, the profile is always row 1), an `exercise` row may only create or
 * change a custom exercise under its own id, and fields this schema lacks are skipped.
 */
@Singleton
class LocalSyncStore @Inject constructor(private val database: SpotterDatabase) {
    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    /** Tables whose primary key is already global (custom exercise ids, the profile singleton). */
    private val keepsLocalId = setOf("exercise", "user_profile")
    private val order = SyncSchema.tables.map { it.name }

    /** Column → parent table, for foreign keys to tables keyed locally by an autoincrement id. */
    private val foreignKeys: Map<String, Map<String, String>> by lazy {
        order.associateWith { table ->
            db.query("PRAGMA foreign_key_list($table)").use { c ->
                buildMap {
                    while (c.moveToNext()) {
                        val parent = c.getString(c.getColumnIndexOrThrow("table"))
                        val from = c.getString(c.getColumnIndexOrThrow("from"))
                        if (parent in order && parent !in keepsLocalId) put(from, parent)
                    }
                }
            }
        }
    }

    /** Each synced table's columns, so fields from newer app versions or the website are skipped. */
    private val columns: Map<String, Set<String>> by lazy {
        order.associateWith { table ->
            db.query("PRAGMA table_info($table)").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
        }
    }

    fun hasLocalData(): Boolean =
        db.query("SELECT EXISTS(SELECT 1 FROM workout_session) OR EXISTS(SELECT 1 FROM plan)").use { it.moveToFirst() && it.getInt(0) == 1 }

    /**
     * Everything changed or deleted after [since] (ms). Foreign keys are translated to the
     * parents' sync ids with one lookup per parent table for just the ids referenced.
     */
    fun changesSince(since: Long): List<SyncRow> {
        class Pending(val table: String, val syncId: String, val updatedAt: Long, val values: MutableMap<String, JsonElement>, val refs: List<Triple<String, String, Long>>)

        val pending = mutableListOf<Pending>()
        val needed = mutableMapOf<String, MutableSet<Long>>()
        SyncSchema.tables.forEach { table ->
            val filter = table.condition?.replace("%.", "")?.let { " AND $it" }.orEmpty()
            val fks = foreignKeys.getValue(table.name)
            db.query("SELECT * FROM ${table.name} WHERE updatedAt > ? AND syncId IS NOT NULL$filter", arrayOf(since)).use { c ->
                while (c.moveToNext()) {
                    val values = mutableMapOf<String, JsonElement>()
                    val refs = mutableListOf<Triple<String, String, Long>>()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        if (name == "syncId" || name == "updatedAt") continue
                        if (name == "id" && table.name !in keepsLocalId) continue
                        val parent = fks[name]
                        if (parent != null && !c.isNull(i)) {
                            val localId = c.getLong(i)
                            refs += Triple(name, parent, localId)
                            needed.getOrPut(parent, ::mutableSetOf) += localId
                            values[name] = JsonNull // filled in below
                        } else {
                            values[name] = c.json(i)
                        }
                    }
                    pending += Pending(
                        table.name,
                        c.getString(c.getColumnIndexOrThrow("syncId")),
                        c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                        values,
                        refs,
                    )
                }
            }
        }

        val parentSyncIds: Map<String, Map<Long, String>> = needed.mapValues { (parent, ids) -> syncIdsOf(parent, ids) }
        val rows = pending.mapTo(mutableListOf()) { p ->
            p.refs.forEach { (column, parent, localId) ->
                parentSyncIds.getValue(parent)[localId]?.let { p.values[column] = JsonPrimitive(it) }
            }
            SyncRow(tableName = p.table, syncId = p.syncId, payload = JsonObject(p.values), updatedAt = p.updatedAt)
        }
        db.query("SELECT tableName, syncId, deletedAt FROM sync_tombstone WHERE deletedAt > ?", arrayOf(since)).use { c ->
            while (c.moveToNext()) rows += SyncRow(c.getString(0), c.getString(1), null, c.getLong(2), c.getLong(2))
        }
        return rows
    }

    private fun syncIdsOf(parent: String, ids: Set<Long>): Map<Long, String> = buildMap {
        ids.chunked(SQL_VARIABLE_LIMIT).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.query("SELECT id, syncId FROM $parent WHERE syncId IS NOT NULL AND id IN ($placeholders)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) put(c.getLong(0), c.getString(1))
            }
        }
    }

    /** [applyAll], returning only how many rows changed. */
    fun apply(rows: List<SyncRow>): Int = applyAll(rows).changed

    /**
     * Writes pulled rows, last-write-wins: a row only replaces local data that is older.
     * Upserts go first (parents before children), then deletions (children first). That
     * way a row moved to a new parent in the same batch as its old parent's deletion is
     * already under the new parent when the old one's cascade runs. Rows whose parent
     * isn't here are returned as deferred, so a caller applying page by page can retry
     * them once later pages are in.
     */
    fun applyAll(rows: List<SyncRow>): ApplyResult {
        if (rows.isEmpty()) return ApplyResult(0, emptyList())
        var changed = 0
        val deferred = mutableListOf<SyncRow>()
        db.beginTransaction()
        try {
            db.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
            val (deletes, upserts) = rows.partition { it.deletedAt != null }
            upserts.sortedBy { order.indexOf(it.tableName) }.forEach { row ->
                if (row.tableName !in order) return@forEach skipped(row, "unknown table")
                when (upsert(row)) {
                    Upsert.APPLIED -> changed++
                    Upsert.PARENT_MISSING -> deferred += row
                    Upsert.SKIPPED -> Unit
                }
            }
            deletes.sortedByDescending { order.indexOf(it.tableName) }.forEach { row ->
                if (row.tableName !in order) return@forEach skipped(row, "unknown table")
                val deletedAt = row.deletedAt ?: return@forEach
                val local = localVersion(row.tableName, row.syncId) ?: return@forEach
                if (local < deletedAt) {
                    db.delete(row.tableName, "syncId = ?", arrayOf(row.syncId))
                    changed++
                }
            }
            db.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
            // Merging two phones can leave two active plans: keep the newest. (Triggers are
            // back on, so this fix-up syncs too.)
            db.execSQL(
                "UPDATE plan SET isActive = 0 WHERE isActive = 1 AND id <> " +
                    "(SELECT id FROM plan WHERE isActive = 1 ORDER BY createdAt DESC, id DESC LIMIT 1)",
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.invalidationTracker.refreshVersionsAsync()
        return ApplyResult(changed, deferred)
    }

    /**
     * Body weight is one entry per day, but two devices can each log the same day under
     * different sync ids. The newer entry wins; the other is tombstoned so the backup and
     * other devices drop it too. Returns false when the incoming row lost.
     */
    private fun resolveSameDayWeight(row: SyncRow, values: ContentValues): Boolean {
        val date = values.getAsLong("date") ?: return true
        val other = db.query(
            "SELECT syncId, updatedAt FROM body_weight_entry WHERE date = ? AND syncId <> ?",
            arrayOf<Any>(date, row.syncId),
        ).use { if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null } ?: return true
        val (otherId, otherUpdatedAt) = other
        val loser = if (otherUpdatedAt >= row.updatedAt) row.syncId else otherId
        if (loser == otherId) db.delete("body_weight_entry", "syncId = ?", arrayOf(otherId))
        // Stamped "now" so the next push picks it up (triggers are off while applying).
        db.execSQL(
            "INSERT OR REPLACE INTO sync_tombstone (tableName, syncId, deletedAt) VALUES ('body_weight_entry', ?, " +
                "MAX(${SyncSchema.NOW_MS}, ?))",
            arrayOf<Any>(loser, maxOf(otherUpdatedAt, row.updatedAt) + 1),
        )
        return loser != row.syncId
    }

    /** Clears all synced data (before restoring a backup over a phone's own data). */
    fun wipe() {
        db.beginTransaction()
        try {
            db.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
            SyncSchema.tables.reversed().forEach { table ->
                val where = table.condition?.replace("%.", "")
                if (table.name == "exercise") {
                    // Custom exercises may still be referenced by nothing now; seeded ones stay.
                    db.delete("exercise", where, null)
                } else {
                    db.delete(table.name, null, null)
                }
            }
            db.execSQL("DELETE FROM sync_tombstone")
            db.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    private fun localVersion(table: String, syncId: String): Long? =
        db.query("SELECT updatedAt FROM $table WHERE syncId = ?", arrayOf(syncId)).use { if (it.moveToFirst()) it.getLong(0) else null }

    private enum class Upsert { APPLIED, SKIPPED, PARENT_MISSING }

    private fun upsert(row: SyncRow): Upsert {
        val payload = row.payload ?: return Upsert.SKIPPED
        val local = localVersion(row.tableName, row.syncId)
        if (local != null && local >= row.updatedAt) return Upsert.SKIPPED
        // A newer local deletion wins over an older remote edit.
        val tombstone = db.query(
            "SELECT deletedAt FROM sync_tombstone WHERE tableName = ? AND syncId = ?",
            arrayOf(row.tableName, row.syncId),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (tombstone != null && tombstone >= row.updatedAt) return Upsert.SKIPPED
        if (row.tableName == "exercise" && !isSafeExerciseRow(row, payload)) return Upsert.SKIPPED

        val values = ContentValues()
        val fks = foreignKeys.getValue(row.tableName)
        val known = columns.getValue(row.tableName)
        for ((name, element) in payload) {
            if (name !in known || name in managedColumns) continue
            // Local primary keys are never taken from a payload: autoincrement tables assign
            // their own, and the profile is always the singleton row.
            if (name == "id" && row.tableName != "exercise") continue
            val parent = fks[name]
            if (parent != null && element is JsonPrimitive && element.isString) {
                val localId = db.query("SELECT id FROM $parent WHERE syncId = ?", arrayOf(element.content))
                    .use { if (it.moveToFirst()) it.getLong(0) else null }
                    ?: return Upsert.PARENT_MISSING.also { skipped(row, "parent $parent ${element.content} not here (yet)") }
                values.put(name, localId)
            } else {
                values.putJson(name, element)
            }
        }
        if (row.tableName == "user_profile") values.put("id", UserProfileEntity.SINGLETON_ID)
        values.put("syncId", row.syncId)
        values.put("updatedAt", row.updatedAt)
        if (row.tableName == "body_weight_entry" && !resolveSameDayWeight(row, values)) return Upsert.SKIPPED
        try {
            if (local != null) {
                db.update(row.tableName, SQLiteDatabase.CONFLICT_REPLACE, values, "syncId = ?", arrayOf(row.syncId))
            } else {
                db.insert(row.tableName, SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        } catch (e: SQLiteConstraintException) {
            // A row this version can't hold (e.g. a plan exercise using an exercise from a
            // newer catalog): skip it rather than fail the whole pull and block sync.
            skipped(row, "constraint: ${e.message}")
            return Upsert.SKIPPED
        }
        if (tombstone != null) {
            db.delete("sync_tombstone", "tableName = ? AND syncId = ?", arrayOf(row.tableName, row.syncId))
        }
        return Upsert.APPLIED
    }

    /**
     * Only custom exercises sync, keyed by their own id, so a remote exercise row must be
     * custom, carry its sync id as its id, and must never land on a seeded catalog row.
     */
    private fun isSafeExerciseRow(row: SyncRow, payload: JsonObject): Boolean {
        val id = (payload["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id != row.syncId) return false.also { skipped(row, "exercise id does not match its sync id") }
        if (!id.startsWith(CUSTOM_EXERCISE_PREFIX)) return false.also { skipped(row, "not a custom exercise id") }
        val custom = payload["isCustom"]?.let { it as? JsonPrimitive }?.let { it.booleanOrNull ?: it.longOrNull?.let { n -> n != 0L } }
        if (custom != true) return false.also { skipped(row, "exercise row is not marked custom") }
        val seeded = db.query("SELECT isCustom FROM exercise WHERE id = ?", arrayOf(id)).use { it.moveToFirst() && it.getInt(0) == 0 }
        if (seeded) return false.also { skipped(row, "would overwrite a seeded exercise") }
        return true
    }

    private fun skipped(row: SyncRow, why: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Skipped ${row.tableName}/${row.syncId}: $why")
    }

    private companion object {
        const val TAG = "SpotterSync"
        const val CUSTOM_EXERCISE_PREFIX = "custom-"
        const val SQL_VARIABLE_LIMIT = 900
        /** Set by this store, never taken from a payload. */
        val managedColumns = setOf("syncId", "updatedAt")
    }
}

private fun Cursor.json(i: Int) = when (getType(i)) {
    Cursor.FIELD_TYPE_NULL -> JsonNull
    Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
    Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
    else -> JsonPrimitive(getString(i))
}

private fun ContentValues.putJson(name: String, element: JsonElement) {
    when {
        element is JsonNull -> putNull(name)
        element is JsonPrimitive && element.isString -> put(name, element.content)
        element is JsonPrimitive && element.longOrNull != null -> put(name, element.longOrNull)
        element is JsonPrimitive && element.doubleOrNull != null -> put(name, element.doubleOrNull)
        element is JsonPrimitive && element.booleanOrNull != null -> put(name, if (element.booleanOrNull == true) 1 else 0)
        else -> put(name, element.toString())
    }
}
