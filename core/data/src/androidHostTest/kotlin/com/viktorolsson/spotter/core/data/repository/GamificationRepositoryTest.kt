package com.viktorolsson.spotter.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.Badge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class GamificationRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var gamification: GamificationRepository
    private val clock = MutableClock(Instant.parse("2026-10-05T16:00:00Z"))

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        val seed = parseExerciseSeed(File("catalog/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(db), clock)
        gamification = GamificationRepository(db.workoutDao(), UserProfileRepository(db.userProfileDao()))
    }

    @After
    fun tearDown() = db.close()

    /** Logs an hour-long workout of [exerciseId] with the given sets (weight × reps) and finishes it. */
    private suspend fun logWorkout(exerciseId: String, vararg sets: Pair<Double?, Int>): Long {
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf(exerciseId), defaultSetCount = sets.size)
        val exercise = workouts.getSession(id)!!.exercises.last()
        repeat(sets.size - exercise.sets.size) { workouts.addSet(exercise.id) }
        val logged = workouts.getSession(id)!!.exercises.last().sets
        sets.forEachIndexed { i, (w, r) ->
            workouts.updateSetValues(logged[i].id, w, r, null)
            workouts.toggleSetCompleted(logged[i].id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(id)
        clock.advanceSeconds(2 * 86_400)
        return id
    }

    @Test
    fun setsVolumeAndRecordsComeFromTheDatabase() = runTest {
        val first = logWorkout("barbell-bench-press", 80.0 to 8, 80.0 to 8)
        val second = logWorkout("barbell-bench-press", 82.5 to 8, 80.0 to 9)

        val progress = gamification.observeProgress(TimeZone.UTC).first()
        assertEquals(10, progress.xpFor(first)!!.sets)
        assertEquals(0, progress.xpFor(first)!!.records)
        assertEquals(150, progress.xpFor(second)!!.records)
        // 1 280 kg in the first workout, 2 660 kg after the second.
        assertEquals(listOf(Badge.FIRST_REP, Badge.GRAND_PIANO), progress.unlockedBy(first))
        assertEquals(listOf(Badge.FIRST_PR, Badge.CAR, Badge.TRIPLE_THREAT).sorted(), progress.unlockedBy(second).sorted())
        assertEquals(1.0, progress.badges.single { it.badge == Badge.EXPLORER }.current, 0.0)
    }

    @Test
    fun holdsCountAsSetsButLiftNothing() = runTest {
        val id = logWorkout("plank", 10.0 to 45)

        val progress = gamification.observeProgress(TimeZone.UTC).first()
        assertEquals(5, progress.xpFor(id)!!.sets)
        assertEquals(0.0, progress.badges.single { it.badge == Badge.GRAND_PIANO }.current, 0.0)
    }

    @Test
    fun anUnfinishedWorkoutDoesNotCount() = runTest {
        workouts.startEmptyWorkout()
        assertEquals(emptyList<Any>(), gamification.observeProgress(TimeZone.UTC).first().sessions)
    }
}
