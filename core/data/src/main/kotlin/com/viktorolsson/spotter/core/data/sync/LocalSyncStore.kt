package com.viktorolsson.spotter.core.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.SyncSchema
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads local changes out as [SyncRow]s and writes pulled rows back in. Works on any
 * synced table generically: columns come from the cursor, and foreign keys (from
 * `PRAGMA foreign_key_list`) are translated between local ids and global sync ids.
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

    /** Everything changed or deleted after [since] (ms). */
    fun changesSince(since: Long): List<SyncRow> {
        val parentSyncIds = mutableMapOf<String, Map<Long, String>>()
        fun syncIdOf(parent: String, localId: Long): String? = parentSyncIds.getOrPut(parent) {
            db.query("SELECT id, syncId FROM $parent WHERE syncId IS NOT NULL").use { c ->
                buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
            }
        }[localId]

        val rows = mutableListOf<SyncRow>()
        SyncSchema.tables.forEach { table ->
            val filter = table.condition?.replace("%.", "")?.let { " AND $it" }.orEmpty()
            val fks = foreignKeys.getValue(table.name)
            db.query("SELECT * FROM ${table.name} WHERE updatedAt > ? AND syncId IS NOT NULL$filter", arrayOf(since)).use { c ->
                while (c.moveToNext()) {
                    val values = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        if (name == "syncId" || name == "updatedAt") continue
                        if (name == "id" && table.name !in keepsLocalId) continue
                        val parent = fks[name]
                        values[name] = if (parent != null && !c.isNull(i)) {
                            syncIdOf(parent, c.getLong(i))?.let(::JsonPrimitive) ?: JsonNull
                        } else {
                            c.json(i)
                        }
                    }
                    rows += SyncRow(
                        tableName = table.name,
                        syncId = c.getString(c.getColumnIndexOrThrow("syncId")),
                        payload = JsonObject(values),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                    )
                }
            }
        }
        db.query("SELECT tableName, syncId, deletedAt FROM sync_tombstone WHERE deletedAt > ?", arrayOf(since)).use { c ->
            while (c.moveToNext()) rows += SyncRow(c.getString(0), c.getString(1), null, c.getLong(2), c.getLong(2))
        }
        return rows
    }

    /**
     * Writes pulled rows, last-write-wins: a row only replaces local data that is older.
     * Upserts go first (parents before children), then deletions (children first). That
     * way a row moved to a new parent in the same batch as its old parent's deletion is
     * already under the new parent when the old one's cascade runs. Returns how many rows
     * changed locally.
     */
    fun apply(rows: List<SyncRow>): Int {
        if (rows.isEmpty()) return 0
        var changed = 0
        db.beginTransaction()
        try {
            db.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
            val (deletes, upserts) = rows.partition { it.deletedAt != null }
            upserts.sortedBy { order.indexOf(it.tableName) }.forEach { row ->
                if (row.tableName in order && upsert(row)) changed++
            }
            deletes.sortedByDescending { order.indexOf(it.tableName) }.forEach { row ->
                if (row.tableName !in order) return@forEach
                val local = localVersion(row.tableName, row.syncId) ?: return@forEach
                if (local < row.deletedAt!!) {
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
        return changed
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

    private fun upsert(row: SyncRow): Boolean {
        val payload = row.payload ?: return false
        val local = localVersion(row.tableName, row.syncId)
        if (local != null && local >= row.updatedAt) return false
        // A newer local deletion wins over an older remote edit.
        val tombstone = db.query(
            "SELECT deletedAt FROM sync_tombstone WHERE tableName = ? AND syncId = ?",
            arrayOf(row.tableName, row.syncId),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (tombstone != null && tombstone >= row.updatedAt) return false

        val values = ContentValues()
        val fks = foreignKeys.getValue(row.tableName)
        val known = columns.getValue(row.tableName)
        for ((name, element) in payload) {
            if (name !in known) continue
            val parent = fks[name]
            if (parent != null && element is JsonPrimitive && element.isString) {
                val localId = db.query("SELECT id FROM $parent WHERE syncId = ?", arrayOf(element.content))
                    .use { if (it.moveToFirst()) it.getLong(0) else null } ?: return false // parent not here (yet)
                values.put(name, localId)
            } else {
                values.putJson(name, element)
            }
        }
        values.put("syncId", row.syncId)
        values.put("updatedAt", row.updatedAt)
        if (row.tableName == "body_weight_entry" && !resolveSameDayWeight(row, values)) return false
        try {
            if (local != null) {
                db.update(row.tableName, SQLiteDatabase.CONFLICT_REPLACE, values, "syncId = ?", arrayOf(row.syncId))
            } else {
                db.insert(row.tableName, SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        } catch (e: SQLiteConstraintException) {
            // A row this version can't hold (e.g. a plan exercise using an exercise from a
            // newer catalog): skip it rather than fail the whole pull and block sync.
            return false
        }
        if (tombstone != null) {
            db.delete("sync_tombstone", "tableName = ? AND syncId = ?", arrayOf(row.tableName, row.syncId))
        }
        return true
    }
}

private fun Cursor.json(i: Int) = when (getType(i)) {
    Cursor.FIELD_TYPE_NULL -> JsonNull
    Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
    Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
    else -> JsonPrimitive(getString(i))
}

private fun ContentValues.putJson(name: String, element: kotlinx.serialization.json.JsonElement) {
    when {
        element is JsonNull -> putNull(name)
        element is JsonPrimitive && element.isString -> put(name, element.content)
        element is JsonPrimitive && element.longOrNull != null -> put(name, element.longOrNull)
        element is JsonPrimitive && element.doubleOrNull != null -> put(name, element.doubleOrNull)
        element is JsonPrimitive && element.booleanOrNull != null -> put(name, if (element.booleanOrNull == true) 1 else 0)
        else -> put(name, element.toString())
    }
}
