package com.viktorolsson.spotter.core.data.sync

import androidx.room.PooledConnection
import androidx.room.Transactor
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.SyncSchema
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.logDebug
import com.viktorolsson.spotter.core.data.logWarn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What [LocalSyncStore.applyAll] did with a batch. */
data class ApplyResult(
    /** Rows that changed locally. */
    val changed: Int,
    /** Rows whose parent isn't here yet; worth retrying once the rest of the pull is in. */
    val deferred: List<SyncRow>,
)

/**
 * Reads local changes out as [SyncRow]s and writes pulled rows back in. Works on any
 * synced table generically: columns come from the result set, and foreign keys (from
 * `PRAGMA foreign_key_list`) are translated between local ids and global sync ids.
 *
 * Remote payloads are untrusted: local primary keys in them are ignored (autoincrement
 * tables get their own, the profile is always row 1), an `exercise` row may only create or
 * change a custom exercise under its own id, and fields this schema lacks are skipped.
 */
class LocalSyncStore(private val database: SpotterDatabase) {
    /** Tables whose primary key is already global (custom exercise ids, the profile singleton). */
    private val keepsLocalId = setOf("exercise", "user_profile")
    private val order = SyncSchema.tables.map { it.name }

    /** A foreign key to a table keyed locally by an autoincrement id; [unlinks] when its parent goes (`SET NULL`). */
    private data class ForeignKey(val parent: String, val unlinks: Boolean)

    /** Column → foreign key, per synced table. */
    private var foreignKeys: Map<String, Map<String, ForeignKey>>? = null

    /** Each synced table's columns, so fields from newer app versions or the website are skipped. */
    private var columns: Map<String, Set<String>>? = null

    private suspend fun Sql.foreignKeys(): Map<String, Map<String, ForeignKey>> = foreignKeys ?: order.associateWith { table ->
        query("PRAGMA foreign_key_list($table)") { st ->
            val tableColumn = st.columnIndex("table")
            val fromColumn = st.columnIndex("from")
            val onDeleteColumn = st.columnIndex("on_delete")
            buildMap {
                while (st.step()) {
                    val parent = st.getText(tableColumn)
                    if (parent in order && parent !in keepsLocalId) {
                        put(st.getText(fromColumn), ForeignKey(parent, unlinks = st.getText(onDeleteColumn) == "SET NULL"))
                    }
                }
            }
        }
    }.also { foreignKeys = it }

    private suspend fun Sql.columns(): Map<String, Set<String>> = columns ?: order.associateWith { table ->
        query("PRAGMA table_info($table)") { st ->
            val name = st.columnIndex("name")
            buildSet { while (st.step()) add(st.getText(name)) }
        }
    }.also { columns = it }

    suspend fun hasLocalData(): Boolean = read {
        query("SELECT EXISTS(SELECT 1 FROM workout_session) OR EXISTS(SELECT 1 FROM plan)") { it.step() && it.getLong(0) == 1L }
    }

    /**
     * Everything changed or deleted after [since] (ms). Foreign keys are translated to the
     * parents' sync ids with one lookup per parent table for just the ids referenced.
     */
    suspend fun changesSince(since: Long): List<SyncRow> = read {
        class Pending(val table: String, val syncId: String, val updatedAt: Long, val values: MutableMap<String, JsonElement>, val refs: List<Triple<String, String, Long>>)

        val pending = mutableListOf<Pending>()
        val needed = mutableMapOf<String, MutableSet<Long>>()
        SyncSchema.tables.forEach { table ->
            val filter = table.condition?.replace("%.", "")?.let { " AND $it" }.orEmpty()
            val fks = foreignKeys().getValue(table.name)
            query("SELECT * FROM ${table.name} WHERE updatedAt > ? AND syncId IS NOT NULL$filter", since) { st ->
                val syncIdColumn = st.columnIndex("syncId")
                val updatedAtColumn = st.columnIndex("updatedAt")
                while (st.step()) {
                    val values = mutableMapOf<String, JsonElement>()
                    val refs = mutableListOf<Triple<String, String, Long>>()
                    for (i in 0 until st.getColumnCount()) {
                        val name = st.getColumnName(i)
                        if (name == "syncId" || name == "updatedAt") continue
                        if (name == "id" && table.name !in keepsLocalId) continue
                        val parent = fks[name]?.parent
                        if (parent != null && !st.isNull(i)) {
                            val localId = st.getLong(i)
                            refs += Triple(name, parent, localId)
                            needed.getOrPut(parent, ::mutableSetOf) += localId
                            values[name] = JsonNull // filled in below
                        } else {
                            values[name] = st.json(i)
                        }
                    }
                    pending += Pending(table.name, st.getText(syncIdColumn), st.getLong(updatedAtColumn), values, refs)
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
        query("SELECT tableName, syncId, deletedAt FROM sync_tombstone WHERE deletedAt > ?", since) { st ->
            while (st.step()) rows += SyncRow(st.getText(0), st.getText(1), null, st.getLong(2), st.getLong(2))
        }
        rows
    }

    private suspend fun Sql.syncIdsOf(parent: String, ids: Set<Long>): Map<Long, String> = buildMap {
        ids.chunked(SQL_VARIABLE_LIMIT).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            query("SELECT id, syncId FROM $parent WHERE syncId IS NOT NULL AND id IN ($placeholders)", *chunk.toTypedArray()) { st ->
                while (st.step()) put(st.getLong(0), st.getText(1))
            }
        }
    }

    /** [applyAll], returning only how many rows changed. */
    suspend fun apply(rows: List<SyncRow>): Int = applyAll(rows).changed

    /**
     * Writes pulled rows, last-write-wins: a row only replaces local data that is older.
     * Upserts go first (parents before children), then deletions (children first). That
     * way a row moved to a new parent in the same batch as its old parent's deletion is
     * already under the new parent when the old one's cascade runs. Rows whose parent
     * isn't here are returned as deferred, so a caller applying page by page can retry
     * them once later pages are in. On that last try ([unlinkMissing]), a missing parent the
     * row only refers to (a workout's plan day, deleted since) is dropped from the row
     * instead, as deleting it here would have done; a row that belongs to its parent stays
     * deferred.
     */
    suspend fun applyAll(rows: List<SyncRow>, unlinkMissing: Boolean = false): ApplyResult {
        if (rows.isEmpty()) return ApplyResult(0, emptyList())
        var changed = 0
        val deferred = mutableListOf<SyncRow>()
        write {
            exec("UPDATE sync_state SET applying = 1 WHERE id = 1")
            val (deletes, upserts) = rows.partition { it.deletedAt != null }
            upserts.sortedBy { order.indexOf(it.tableName) }.forEach { row ->
                if (row.tableName !in order) return@forEach skipped(row, "unknown table")
                when (upsert(row, unlinkMissing)) {
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
                    exec("DELETE FROM ${row.tableName} WHERE syncId = ?", row.syncId)
                    changed++
                }
            }
            exec("UPDATE sync_state SET applying = 0 WHERE id = 1")
            // Merging two phones can leave two active plans: keep the newest. (Triggers are
            // back on, so this fix-up syncs too.)
            exec(
                "UPDATE plan SET isActive = 0 WHERE isActive = 1 AND id <> " +
                    "(SELECT id FROM plan WHERE isActive = 1 ORDER BY createdAt DESC, id DESC LIMIT 1)",
            )
        }
        database.invalidationTracker.refreshAsync()
        return ApplyResult(changed, deferred)
    }

    /**
     * Body weight is one entry per day, but two devices can each log the same day under
     * different sync ids. The newer entry wins; the other is tombstoned so the backup and
     * other devices drop it too. Returns false when the incoming row lost.
     */
    private suspend fun Sql.resolveSameDayWeight(row: SyncRow, values: Map<String, Any?>): Boolean {
        val date = values["date"] as? Long ?: return true
        val other = query("SELECT syncId, updatedAt FROM body_weight_entry WHERE date = ? AND syncId <> ?", date, row.syncId) {
            if (it.step()) it.getText(0) to it.getLong(1) else null
        } ?: return true
        val (otherId, otherUpdatedAt) = other
        val loser = if (otherUpdatedAt >= row.updatedAt) row.syncId else otherId
        if (loser == otherId) exec("DELETE FROM body_weight_entry WHERE syncId = ?", otherId)
        // Stamped "now" so the next push picks it up (triggers are off while applying).
        exec(
            "INSERT OR REPLACE INTO sync_tombstone (tableName, syncId, deletedAt) VALUES ('body_weight_entry', ?, " +
                "MAX(${SyncSchema.NOW_MS}, ?))",
            loser,
            maxOf(otherUpdatedAt, row.updatedAt) + 1,
        )
        return loser != row.syncId
    }

    /** Clears all synced data (before restoring a backup over a phone's own data). */
    suspend fun wipe() {
        write {
            exec("UPDATE sync_state SET applying = 1 WHERE id = 1")
            SyncSchema.tables.reversed().forEach { table ->
                val where = table.condition?.replace("%.", "")
                if (table.name == "exercise") {
                    // Custom exercises may still be referenced by nothing now; seeded ones stay.
                    exec("DELETE FROM exercise" + where?.let { " WHERE $it" }.orEmpty())
                } else {
                    exec("DELETE FROM ${table.name}")
                }
            }
            exec("DELETE FROM sync_tombstone")
            exec("UPDATE sync_state SET applying = 0 WHERE id = 1")
        }
        database.invalidationTracker.refreshAsync()
    }

    private suspend fun Sql.localVersion(table: String, syncId: String): Long? =
        query("SELECT updatedAt FROM $table WHERE syncId = ?", syncId) { if (it.step()) it.getLong(0) else null }

    private enum class Upsert { APPLIED, SKIPPED, PARENT_MISSING }

    private suspend fun Sql.upsert(row: SyncRow, unlinkMissing: Boolean): Upsert {
        val payload = row.payload ?: return Upsert.SKIPPED
        val local = localVersion(row.tableName, row.syncId)
        if (local != null && local >= row.updatedAt) return Upsert.SKIPPED
        // A newer local deletion wins over an older remote edit.
        val tombstone = query("SELECT deletedAt FROM sync_tombstone WHERE tableName = ? AND syncId = ?", row.tableName, row.syncId) {
            if (it.step()) it.getLong(0) else null
        }
        if (tombstone != null && tombstone >= row.updatedAt) return Upsert.SKIPPED
        if (row.tableName == "exercise" && !isSafeExerciseRow(row, payload)) return Upsert.SKIPPED

        // Column names below come from this database's own schema (`known`), never the payload.
        val values = linkedMapOf<String, Any?>()
        val fks = foreignKeys().getValue(row.tableName)
        val known = columns().getValue(row.tableName)
        for ((name, element) in payload) {
            if (name !in known || name in managedColumns) continue
            // Local primary keys are never taken from a payload: autoincrement tables assign
            // their own, and the profile is always the singleton row.
            if (name == "id" && row.tableName != "exercise") continue
            val fk = fks[name]
            if (fk != null && element is JsonPrimitive && element.isString) {
                val localId = query("SELECT id FROM ${fk.parent} WHERE syncId = ?", element.content) { if (it.step()) it.getLong(0) else null }
                if (localId == null && !(unlinkMissing && fk.unlinks)) {
                    logDebug(TAG, "Deferred ${row.tableName}/${row.syncId}: parent ${fk.parent} not here yet")
                    return Upsert.PARENT_MISSING
                }
                values[name] = localId
            } else {
                values[name] = element.sqlValue()
            }
        }
        if (row.tableName == "user_profile") values["id"] = UserProfileEntity.SINGLETON_ID
        values["syncId"] = row.syncId
        values["updatedAt"] = row.updatedAt
        if (row.tableName == "body_weight_entry" && !resolveSameDayWeight(row, values)) return Upsert.SKIPPED
        try {
            if (local != null) {
                val assignments = values.keys.joinToString(", ") { "\"$it\" = ?" }
                exec("UPDATE OR REPLACE ${row.tableName} SET $assignments WHERE syncId = ?", *values.values.toTypedArray(), row.syncId)
            } else {
                val names = values.keys.joinToString(", ") { "\"$it\"" }
                val placeholders = values.keys.joinToString(", ") { "?" }
                exec("INSERT OR REPLACE INTO ${row.tableName} ($names) VALUES ($placeholders)", *values.values.toTypedArray())
            }
        } catch (e: SQLiteException) {
            // A row this version can't hold (e.g. a plan exercise using an exercise from a
            // newer catalog): skip it rather than fail the whole pull and block sync.
            if (e.message?.contains("constraint", ignoreCase = true) != true) throw e
            skipped(row, "constraint: ${e.message}")
            return Upsert.SKIPPED
        }
        if (tombstone != null) {
            exec("DELETE FROM sync_tombstone WHERE tableName = ? AND syncId = ?", row.tableName, row.syncId)
        }
        return Upsert.APPLIED
    }

    /**
     * Only custom exercises sync, keyed by their own id, so a remote exercise row must be
     * custom, carry its sync id as its id, and must never land on a seeded catalog row.
     */
    private suspend fun Sql.isSafeExerciseRow(row: SyncRow, payload: JsonObject): Boolean {
        val id = (payload["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id != row.syncId) return false.also { skipped(row, "exercise id does not match its sync id") }
        if (!id.startsWith(CUSTOM_EXERCISE_PREFIX)) return false.also { skipped(row, "not a custom exercise id") }
        val custom = payload["isCustom"]?.let { it as? JsonPrimitive }?.let { it.booleanOrNull ?: it.longOrNull?.let { n -> n != 0L } }
        if (custom != true) return false.also { skipped(row, "exercise row is not marked custom") }
        val seeded = query("SELECT isCustom FROM exercise WHERE id = ?", id) { it.step() && it.getLong(0) == 0L }
        if (seeded) return false.also { skipped(row, "would overwrite a seeded exercise") }
        return true
    }

    // A pulled row that can't be stored here: data the person has elsewhere won't show on this
    // phone, so it's always logged, not only with debug logging on.
    private fun skipped(row: SyncRow, why: String) = logWarn(TAG, "Skipped ${row.tableName}/${row.syncId}: $why")

    private suspend fun <R> read(block: suspend Sql.() -> R): R =
        database.useReaderConnection { connection -> Sql(connection).block() }

    /** One immediate (write-locked) transaction; it rolls back if [block] throws. */
    private suspend fun <R> write(block: suspend Sql.() -> R): R =
        database.useWriterConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { Sql(this).block() }
        }

    private companion object {
        const val TAG = "SpotterSync"
        const val CUSTOM_EXERCISE_PREFIX = "custom-"
        const val SQL_VARIABLE_LIMIT = 900
        /** Set by this store, never taken from a payload. */
        val managedColumns = setOf("syncId", "updatedAt")
    }
}

/** Prepared statements on one pooled connection, with positional arguments bound by type. */
private class Sql(private val connection: PooledConnection) {
    suspend fun <R> query(sql: String, vararg args: Any?, block: (SQLiteStatement) -> R): R =
        connection.usePrepared(sql) { st ->
            st.bindAll(args)
            block(st)
        }

    suspend fun exec(sql: String, vararg args: Any?) {
        connection.usePrepared(sql) { st ->
            st.bindAll(args)
            st.step()
        }
    }
}

private fun SQLiteStatement.bindAll(args: Array<out Any?>) {
    args.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> bindNull(index)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Double -> bindDouble(index, value)
            is Boolean -> bindLong(index, if (value) 1 else 0)
            is String -> bindText(index, value)
            else -> error("Can't bind ${value::class.simpleName}")
        }
    }
}

private fun SQLiteStatement.columnIndex(name: String): Int =
    (0 until getColumnCount()).firstOrNull { getColumnName(it) == name } ?: error("No column $name")

private fun SQLiteStatement.json(i: Int) = when (getColumnType(i)) {
    SQLITE_DATA_NULL -> JsonNull
    SQLITE_DATA_INTEGER -> JsonPrimitive(getLong(i))
    SQLITE_DATA_FLOAT -> JsonPrimitive(getDouble(i))
    else -> JsonPrimitive(getText(i))
}

/** A payload value as SQLite stores it; same rules as Android's ContentValues before. */
private fun JsonElement.sqlValue(): Any? = when {
    this is JsonNull -> null
    this is JsonPrimitive && isString -> content
    this is JsonPrimitive && longOrNull != null -> longOrNull
    this is JsonPrimitive && doubleOrNull != null -> doubleOrNull
    this is JsonPrimitive && booleanOrNull != null -> if (booleanOrNull == true) 1L else 0L
    else -> toString()
}
