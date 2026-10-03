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

    private companion object {
        const val DB = "migration-test"
    }
}
