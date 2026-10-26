package com.viktorolsson.spotter.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.viktorolsson.spotter.core.data.query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(DB),
        AndroidSQLiteDriver(),
        SpotterDatabase::class,
    )

    @Test
    fun migrate1To2KeepsSessionExercisesAndAddsNullRest() {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                """INSERT INTO exercise (id, name, primaryMuscles, secondaryMuscles, movementPattern, equipment,
                   mechanics, difficulty, unilateral, instructions, isCustom)
                   VALUES ('push-up', 'Push-Up', 'CHEST', '', 'HORIZONTAL_PUSH', '', 'COMPOUND', 'BEGINNER', 0, NULL, 0)""",
            )
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty) VALUES (1, 0, NULL, NULL, NULL, NULL)")
            db.execSQL(
                """INSERT INTO session_exercise (id, sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes)
                   VALUES (1, 1, 'push-up', 0, NULL, NULL, 'elbows in')""",
            )
        }
        helper.runMigrationsAndValidate(2).use { db ->
            db.query("SELECT notes, restSeconds FROM session_exercise WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("elbows in", it.getString(0))
                assertEquals(true, it.isNull(1))
            }
        }
    }

    @Test
    fun migrate2To3AddsPlanLinkAndStartingWeight() {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                """INSERT INTO exercise (id, name, primaryMuscles, secondaryMuscles, movementPattern, equipment,
                   mechanics, difficulty, unilateral, instructions, isCustom)
                   VALUES ('push-up', 'Push-Up', 'CHEST', '', 'HORIZONTAL_PUSH', '', 'COMPOUND', 'BEGINNER', 0, NULL, 0)""",
            )
            db.execSQL("INSERT INTO plan (id, name, splitType, goal, createdAt, isActive) VALUES (1, 'P', 'FULL_BODY', 'HYPERTROPHY', 0, 1)")
            db.execSQL("INSERT INTO plan_day (id, planId, position, name) VALUES (1, 1, 0, 'A')")
            db.execSQL(
                """INSERT INTO plan_exercise (id, planDayId, exerciseId, position, sets, repMin, repMax, targetRir,
                   restSeconds, progressionRule, supersetGroup) VALUES (1, 1, 'push-up', 0, 3, 8, 12, 2, 90, 'DOUBLE_PROGRESSION', NULL)""",
            )
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty) VALUES (1, 0, NULL, 1, NULL, NULL)")
            db.execSQL(
                """INSERT INTO session_exercise (id, sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes, restSeconds)
                   VALUES (1, 1, 'push-up', 0, NULL, NULL, NULL, 90)""",
            )
        }
        helper.runMigrationsAndValidate(3).use { db ->
            db.query("SELECT restSeconds, planExerciseId FROM session_exercise WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(90, it.getInt(0))
                assertEquals(true, it.isNull(1))
            }
            db.query("SELECT startingWeightKg FROM plan_exercise WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(true, it.isNull(0))
            }
        }
    }

    @Test
    fun migrate3To4AddsProgressionReason() {
        helper.createDatabase(3).use { db ->
            db.execSQL(
                """INSERT INTO exercise (id, name, primaryMuscles, secondaryMuscles, movementPattern, equipment,
                   mechanics, difficulty, unilateral, instructions, isCustom)
                   VALUES ('push-up', 'Push-Up', 'CHEST', '', 'HORIZONTAL_PUSH', '', 'COMPOUND', 'BEGINNER', 0, NULL, 0)""",
            )
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty) VALUES (1, 0, NULL, NULL, NULL, NULL)")
            db.execSQL(
                """INSERT INTO session_exercise (id, sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes, restSeconds, planExerciseId)
                   VALUES (1, 1, 'push-up', 0, NULL, NULL, NULL, 90, NULL)""",
            )
        }
        helper.runMigrationsAndValidate(4).use { db ->
            db.query("SELECT restSeconds, progressionReason FROM session_exercise WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(90, it.getInt(0))
                assertEquals(true, it.isNull(1))
            }
        }
    }

    @Test
    fun migrate4To5AddsDeloadFlagAndRecommendationFields() {
        helper.createDatabase(4).use { db ->
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty) VALUES (1, 0, 1, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO recommendation (id, type, exerciseId, payload, createdAt, status) VALUES (1, 'DELOAD', NULL, '{}', 0, 'ACTIVE')")
        }
        helper.runMigrationsAndValidate(5).use { db ->
            db.query("SELECT isDeload FROM workout_session WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT resolvedAt, dedupKey FROM recommendation WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(true, it.isNull(0))
                assertEquals("", it.getString(1))
            }
        }
    }

    @Test
    fun migrate5To6GivesExistingRowsSyncIdsAndInstallsTriggers() {
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """INSERT INTO exercise (id, name, primaryMuscles, secondaryMuscles, movementPattern, equipment,
                   mechanics, difficulty, unilateral, instructions, isCustom)
                   VALUES ('push-up', 'Push-Up', 'CHEST', '', 'HORIZONTAL_PUSH', '', 'COMPOUND', 'BEGINNER', 0, NULL, 0),
                          ('custom-1', 'Mine', 'CHEST', '', 'HORIZONTAL_PUSH', '', 'COMPOUND', 'BEGINNER', 0, NULL, 1)""",
            )
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty, isDeload) VALUES (1, 0, 1, NULL, NULL, NULL, 0)")
        }
        helper.runMigrationsAndValidate(6, listOf(com.viktorolsson.spotter.core.data.db.SyncSchema.MIGRATION_5_6)).use { db ->
            db.query("SELECT syncId FROM exercise ORDER BY id").use {
                it.moveToFirst(); assertEquals("custom-1", it.getString(0)) // custom: keyed by its own id
                it.moveToNext(); assertEquals(true, it.isNull(0))           // seeded: not synced
            }
            db.query("SELECT syncId, updatedAt FROM workout_session WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(32, it.getString(0)!!.length)
                assertEquals(true, it.getLong(1) > 0)
            }
            // Triggers are live after the migration.
            db.execSQL("DELETE FROM workout_session WHERE id = 1")
            db.query("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = 'workout_session'").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate6To7AddsScheduledSessionsThatSync() {
        helper.createDatabase(6).close()
        helper.runMigrationsAndValidate(7).use { db ->
            // The app installs sync triggers on every open; do the same here.
            com.viktorolsson.spotter.core.data.db.SyncSchema.createTriggers(db)
            db.execSQL("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20000, NULL, 'Easy day', NULL)")
            db.query("SELECT syncId, updatedAt FROM scheduled_session").use {
                it.moveToFirst()
                assertEquals(32, it.getString(0)!!.length)
                assertEquals(true, it.getLong(1) > 0)
            }
        }
    }

    @Test
    fun migrate7To8AddsIndicesAndKeepsTriggers() {
        helper.createDatabase(7).use { db ->
            SyncSchema.createTriggers(db)
            db.execSQL("INSERT INTO workout_session (id, startedAt, endedAt, planDayId, notes, perceivedDifficulty, isDeload) VALUES (1, 0, 1, NULL, NULL, NULL, 0)")
        }
        helper.runMigrationsAndValidate(8).use { db ->
            fun indices(table: String) = db.query("PRAGMA index_list($table)").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))!!) }
            }
            SyncSchema.tables.forEach { t -> assertTrue(t.name, "index_${t.name}_updatedAt" in indices(t.name)) }
            assertTrue("index_sync_tombstone_deletedAt" in indices("sync_tombstone"))
            assertTrue("index_personal_record_achievedAt" in indices("personal_record"))
            assertTrue("index_workout_session_endedAt" in indices("workout_session"))
            // The row and its sync id survived, and the triggers are still live after the migration.
            db.query("SELECT syncId FROM workout_session WHERE id = 1").use { it.moveToFirst(); assertEquals(32, it.getString(0)!!.length) }
            assertTrue(SyncSchema.triggersInstalled(db))
            db.execSQL("DELETE FROM workout_session WHERE id = 1")
            db.query("SELECT COUNT(*) FROM sync_tombstone WHERE tableName = 'workout_session'").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migratingFrom6InstallsTheScheduledSessionTriggers() {
        helper.createDatabase(6).use { SyncSchema.createTriggers(it, upTo = 6) }
        helper.runMigrationsAndValidate(8).use { db ->
            assertTrue(SyncSchema.triggersInstalled(db))
            db.execSQL("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20000, NULL, 'Easy day', NULL)")
            db.query("SELECT syncId FROM scheduled_session").use { it.moveToFirst(); assertEquals(32, it.getString(0)!!.length) }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
