package com.viktorolsson.spotter.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SpotterDatabase::class.java)

    @Test
    fun migrate1To2KeepsSessionExercisesAndAddsNullRest() {
        helper.createDatabase(DB, 1).use { db ->
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
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT notes, restSeconds FROM session_exercise WHERE id = 1").use {
                it.moveToFirst()
                assertEquals("elbows in", it.getString(0))
                assertEquals(true, it.isNull(1))
            }
        }
    }

    @Test
    fun migrate2To3AddsPlanLinkAndStartingWeight() {
        helper.createDatabase(DB, 2).use { db ->
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
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
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

    private companion object {
        const val DB = "migration-test"
    }
}
