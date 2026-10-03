package com.viktorolsson.spotter.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.entity.UserProfileEntity
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {
    private lateinit var db: SpotterDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
    }

    @Test
    fun seededLibraryRoundTripsThroughConverters() = runTest {
        seed()
        val bench = db.exerciseDao().getById("barbell-bench-press")!!
        assertEquals(listOf(Muscle.CHEST), bench.primaryMuscles)
        assertEquals(listOf(Muscle.FRONT_DELTS, Muscle.TRICEPS), bench.secondaryMuscles)
        assertEquals(listOf(Equipment.BARBELL, Equipment.BENCH, Equipment.SQUAT_RACK), bench.equipment)

        val pushUp = db.exerciseDao().getById("push-up")!!
        assertTrue(pushUp.equipment.isEmpty())
    }

    @Test
    fun reseedingIsIdempotent() = runTest {
        seed()
        val count = db.exerciseDao().observeCount().first()
        seed()
        assertEquals(count, db.exerciseDao().observeCount().first())
    }

    @Test
    fun queriesByPatternAndName() = runTest {
        seed()
        val hinges = db.exerciseDao().getByPattern(MovementPattern.HINGE)
        assertTrue(hinges.any { it.id == "romanian-deadlift" })
        assertTrue(hinges.all { it.movementPattern == MovementPattern.HINGE })

        val curls = db.exerciseDao().search("curl").first()
        assertTrue(curls.isNotEmpty())
        assertTrue(curls.all { it.name.contains("curl", ignoreCase = true) })
    }

    @Test
    fun profileSetsRoundTrip() = runTest {
        val profile = UserProfileEntity(
            sex = Sex.FEMALE,
            heightCm = 168.0,
            birthDate = LocalDate.of(1997, 5, 14),
            bodyWeightKg = 62.5,
            units = WeightUnit.KG,
            experience = ExperienceLevel.INTERMEDIATE,
            goal = Goal.HYPERTROPHY,
            daysPerWeek = 4,
            sessionLengthMinutes = 60,
            equipment = setOf(Equipment.BARBELL, Equipment.DUMBBELL),
            focusAreas = setOf(BodyArea.GLUTES),
            limitations = emptySet(),
        )
        db.userProfileDao().upsert(profile)
        assertEquals(profile, db.userProfileDao().observe().first())

        db.userProfileDao().upsert(profile.copy(limitations = setOf(Limitation.KNEE)))
        assertEquals(setOf(Limitation.KNEE), db.userProfileDao().observe().first()!!.limitations)
    }

    @Test
    fun foreignKeysAreEnforced() = runTest {
        seed()
        // A session exercise pointing at an unknown exercise must be rejected.
        val sessionId = db.openHelper.writableDatabase.let { sql ->
            sql.execSQL(
                "INSERT INTO workout_session (startedAt, endedAt, planDayId, notes, perceivedDifficulty) VALUES (?, NULL, NULL, NULL, NULL)",
                arrayOf<Any>(Instant.parse("2026-10-03T10:00:00Z").toEpochMilli()),
            )
            sql.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
        }
        val result = runCatching {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO session_exercise (sessionId, exerciseId, position, substitutedFromExerciseId, supersetGroup, notes) VALUES (?, 'no-such-exercise', 0, NULL, NULL, NULL)",
                arrayOf<Any>(sessionId),
            )
        }
        assertTrue(result.isFailure)
    }
}
