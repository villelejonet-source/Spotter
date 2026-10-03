package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SetType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var repo: WorkoutRepository
    private val clock = MutableClock(Instant.parse("2026-10-03T10:00:00Z"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seed = parseExerciseSeed(File("src/main/assets/exercises.json").readText())
        db.exerciseDao().upsertAll(seed.exercises.map(SeedExercise::toEntity))
        repo = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(id: Long) = repo.getSession(id)!!

    @Test
    fun onlyOneWorkoutCanBeActive() = runTest {
        val first = repo.startEmptyWorkout()
        assertEquals(first, repo.startEmptyWorkout())
    }

    @Test
    fun newExerciseWithoutHistoryGetsThreeEmptySets() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-bench-press", "pull-up"))
        val exercises = session(id).exercises
        assertEquals(listOf("barbell-bench-press", "pull-up"), exercises.map { it.exercise.id })
        assertEquals(3, exercises[0].sets.size)
        assertTrue(exercises[0].sets.all { it.weightKg == null && it.reps == null && !it.isCompleted })
    }

    @Test
    fun tickingCarriesValuesToUntouchedLaterSets() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-bench-press"))
        val sets = session(id).exercises.single().sets
        repo.updateSetValues(sets[0].id, 80.0, 8, null)
        repo.updateSetValues(sets[2].id, 70.0, 10, null) // already edited: must not be overwritten
        assertTrue(repo.toggleSetCompleted(sets[0].id))

        val after = session(id).exercises.single().sets
        assertTrue(after[0].isCompleted)
        assertEquals(80.0 to 8, after[1].weightKg to after[1].reps)
        assertEquals(70.0 to 10, after[2].weightKg to after[2].reps)

        assertFalse(repo.toggleSetCompleted(sets[0].id)) // un-tick
        assertFalse(session(id).exercises.single().sets[0].isCompleted)
    }

    @Test
    fun finishDropsUntickedSetsAndEmptyExercisesThenPrefillsNextTime() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-bench-press", "pull-up"))
        val bench = session(id).exercises[0].sets
        repo.updateSetValues(bench[0].id, 80.0, 8, 2)
        repo.toggleSetCompleted(bench[0].id)
        repo.updateSetValues(bench[1].id, 82.5, 6, null)
        repo.toggleSetCompleted(bench[1].id)
        clock.advanceSeconds(3600)
        repo.finishWorkout(id)

        val finished = session(id)
        assertNotNull(finished.endedAt)
        assertEquals(listOf("barbell-bench-press"), finished.exercises.map { it.exercise.id })
        assertEquals(2, finished.exercises.single().sets.size)

        assertEquals(listOf(PreviousSet(80.0, 8), PreviousSet(82.5, 6)), repo.getPreviousSets("barbell-bench-press"))

        // The next workout pre-fills bench from last time.
        clock.advanceSeconds(86_400)
        val next = repo.startEmptyWorkout()
        assertTrue(next != id)
        repo.addExercises(next, listOf("barbell-bench-press"))
        val prefilled = session(next).exercises.single().sets
        assertEquals(listOf(80.0 to 8, 82.5 to 6), prefilled.map { it.weightKg to it.reps })
        assertTrue(prefilled.none { it.isCompleted })
    }

    @Test
    fun addSetCopiesLastSetAndDeleteRenumbers() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-curl"), defaultSetCount = 1)
        val exercise = session(id).exercises.single()
        repo.updateSetValues(exercise.sets[0].id, 30.0, 12, null)
        repo.setSetType(exercise.sets[0].id, SetType.WARMUP)
        repo.addSet(exercise.id)
        repo.addSet(exercise.id)

        var sets = session(id).exercises.single().sets
        assertEquals(listOf(0, 1, 2), sets.map { it.position })
        assertEquals(SetType.WORKING, sets[1].setType) // a warm-up isn't copied as a warm-up
        assertEquals(30.0 to 12, sets[2].weightKg to sets[2].reps)

        repo.deleteSet(sets[0].id)
        sets = session(id).exercises.single().sets
        assertEquals(listOf(0, 1), sets.map { it.position })
    }

    @Test
    fun supersetsGroupAndDissolve() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-bench-press", "barbell-row", "dumbbell-lateral-raise"))
        val (a, b, c) = session(id).exercises
        repo.supersetWithNext(a.id)
        repo.supersetWithNext(b.id)
        var groups = session(id).exercises.map { it.supersetGroup }
        assertTrue(groups.all { it != null && it == groups[0] })

        repo.removeFromSuperset(c.id) // group of three: only c leaves
        groups = session(id).exercises.map { it.supersetGroup }
        assertNull(groups[2])
        assertEquals(groups[0], groups[1])

        repo.removeFromSuperset(a.id) // group of two dissolves
        assertTrue(session(id).exercises.all { it.supersetGroup == null })
    }

    @Test
    fun moveAndRemoveExercisesKeepPositionsDense() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("back-squat", "romanian-deadlift", "leg-extension"))
        val (squat, _, ext) = session(id).exercises
        repo.moveExercise(ext.id, -2)
        assertEquals(listOf("leg-extension", "back-squat", "romanian-deadlift"), session(id).exercises.map { it.exercise.id })
        repo.removeExercise(squat.id)
        assertEquals(listOf(0, 1), session(id).exercises.map { it.position })
    }

    @Test
    fun warmUpsGoBeforeWorkingSetsOnce() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("barbell-bench-press"), defaultSetCount = 2)
        val exercise = session(id).exercises.single()
        exercise.sets.forEach { repo.updateSetValues(it.id, 100.0, 5, null) }

        assertEquals(4, repo.addWarmUps(exercise.id, extended = false))
        val sets = session(id).exercises.single().sets
        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING), sets.map { it.setType })
        assertEquals(listOf(20.0, 50.0, 70.0, 85.0, 100.0, 100.0), sets.map { it.weightKg })
        assertEquals((0..5).toList(), sets.map { it.position })
        assertEquals(0, repo.addWarmUps(exercise.id, extended = false))
    }

    @Test
    fun discardDeletesEverything() = runTest {
        val id = repo.startEmptyWorkout()
        repo.addExercises(id, listOf("push-up"))
        repo.discardWorkout(id)
        assertNull(repo.getSession(id))
        assertNull(db.workoutDao().getActiveSession())
    }
}

class MutableClock(private var now: Instant) : Clock() {
    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
}
