package com.viktorolsson.spotter.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Change tracking for cloud sync, done with SQLite triggers so app code doesn't have to
 * remember it: every insert gets a global `syncId`, every insert/update stamps
 * `updatedAt`, and every delete (including cascades) leaves a tombstone. While
 * `sync_state.applying` is 1 (a pull is being written) the triggers stand down.
 *
 * The update trigger only fires for writes that don't already move `updatedAt` forward
 * (app writes carry the loaded, i.e. same or stale, value) and stamps a strictly larger
 * one, so its own UPDATE can't re-trigger it even where recursive triggers are on.
 */
object SyncSchema {
    /** A synced table and how its global id is formed. */
    data class Table(
        val name: String,
        /** SQL for a new row's syncId (NEW.* is in scope). */
        val newSyncId: String = RANDOM_ID,
        /** Only rows matching this (NEW.* / OLD.* style, with the prefix substituted) are synced. */
        val condition: String? = null,
    )

    /**
     * Parents before children: pulls are applied in this order so foreign keys resolve.
     * Seeded exercises are identical everywhere, so only custom ones sync, keyed by their
     * own (already global) id; the profile is a singleton with a fixed id.
     */
    val tables = listOf(
        Table("exercise", newSyncId = "NEW.id", condition = "%.isCustom = 1"),
        Table("user_profile", newSyncId = "'user-profile'"),
        Table("body_weight_entry"),
        Table("plan"),
        Table("plan_day"),
        Table("plan_exercise"),
        Table("workout_session"),
        Table("session_exercise"),
        Table("set_entry"),
        Table("personal_record"),
        Table("recommendation"),
    )

    const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    private const val RANDOM_ID = "lower(hex(randomblob(16)))"
    private const val ACTIVE = "(SELECT applying FROM sync_state WHERE id = 1) = 0"

    fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, applying) VALUES (1, 0)")
        tables.forEach { t ->
            val newCond = t.condition?.replace("%", "NEW")?.let { " AND $it" }.orEmpty()
            val oldCond = t.condition?.replace("%", "OLD")?.let { " AND $it" }.orEmpty()
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS sync_ins_${t.name} AFTER INSERT ON ${t.name}
                WHEN $ACTIVE$newCond
                BEGIN
                    UPDATE ${t.name} SET syncId = COALESCE(NEW.syncId, ${t.newSyncId}), updatedAt = $NOW_MS WHERE rowid = NEW.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS sync_upd_${t.name} AFTER UPDATE ON ${t.name}
                WHEN $ACTIVE AND NEW.updatedAt <= OLD.updatedAt$newCond
                BEGIN
                    UPDATE ${t.name} SET syncId = COALESCE(NEW.syncId, ${t.newSyncId}),
                        updatedAt = MAX($NOW_MS, OLD.updatedAt + 1) WHERE rowid = NEW.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS sync_del_${t.name} AFTER DELETE ON ${t.name}
                WHEN $ACTIVE AND OLD.syncId IS NOT NULL$oldCond
                BEGIN
                    INSERT OR REPLACE INTO sync_tombstone (tableName, syncId, deletedAt) VALUES ('${t.name}', OLD.syncId, $NOW_MS);
                END
                """.trimIndent(),
            )
        }
    }

    /** Schema 5 → 6: sync columns, ids for existing rows, tombstones, triggers. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            tables.forEach { t ->
                db.execSQL("ALTER TABLE ${t.name} ADD COLUMN syncId TEXT")
                db.execSQL("ALTER TABLE ${t.name} ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                val id = t.newSyncId.replace("NEW.", "")
                val where = t.condition?.replace("%.", "")?.let { " WHERE $it" }.orEmpty()
                db.execSQL("UPDATE ${t.name} SET syncId = $id, updatedAt = $NOW_MS$where")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${t.name}_syncId ON ${t.name} (syncId)")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_tombstone (tableName TEXT NOT NULL, syncId TEXT NOT NULL, " +
                    "deletedAt INTEGER NOT NULL, PRIMARY KEY(tableName, syncId))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (id INTEGER NOT NULL, applying INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))")
            createTriggers(db)
        }
    }
}
