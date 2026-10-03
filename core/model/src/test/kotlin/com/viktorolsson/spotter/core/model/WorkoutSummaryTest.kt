package com.viktorolsson.spotter.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WorkoutSummaryTest {
    private val start = Instant.parse("2026-10-03T10:00:00Z")
    private val done = start.plusSeconds(60)

    private fun exercise(name: String) = Exercise(
        id = name, name = name, primaryMuscles = listOf(Muscle.CHEST), secondaryMuscles = emptyList(),
        movementPattern = MovementPattern.HORIZONTAL_PUSH, equipment = emptyList(), mechanics = Mechanics.COMPOUND,
        difficulty = Difficulty.BEGINNER, unilateral = false, instructions = null, isCustom = false,
    )

    private fun set(weight: Double?, reps: Int?, type: SetType = SetType.WORKING, completed: Boolean = true) =
        WorkoutSet(0, 0, weight, reps, null, type, if (completed) done else null, null)

    @Test
    fun `volume skips warm-ups and incomplete sets, best set is heaviest then most reps`() {
        val session = WorkoutSession(
            id = 1, startedAt = start, endedAt = start.plus(Duration.ofMinutes(52)), notes = null,
            exercises = listOf(
                SessionExercise(
                    1, exercise("Bench"), 0, null, null, null,
                    listOf(
                        set(40.0, 10, SetType.WARMUP),
                        set(80.0, 8),
                        set(80.0, 9),
                        set(85.0, 5, completed = false),
                    ),
                ),
                SessionExercise(2, exercise("Pull-up"), 1, null, null, null, listOf(set(null, 10))),
            ),
        )
        val summary = WorkoutSummary.of(session)
        assertEquals(Duration.ofMinutes(52), summary.duration)
        assertEquals(4, summary.completedSets)
        assertEquals(80.0 * 8 + 80.0 * 9, summary.volumeKg, 0.001)
        assertEquals(PreviousSet(80.0, 9), summary.exercises[0].bestSet)
        assertEquals(PreviousSet(null, 10), summary.exercises[1].bestSet)
    }

    @Test
    fun `exercise with only warm-ups has no best set`() {
        val session = WorkoutSession(
            1, start, start, null,
            listOf(SessionExercise(1, exercise("Squat"), 0, null, null, null, listOf(set(20.0, 10, SetType.WARMUP)))),
        )
        assertNull(WorkoutSummary.of(session).exercises.single().bestSet)
    }

    @Test
    fun `weights format and parse in both units`() {
        assertEquals("82.5", WeightUnit.KG.format(82.5))
        assertEquals("80", WeightUnit.KG.format(80.0))
        assertEquals(102.058, WeightUnit.LB.parseToKg("225")!!, 0.001)
        assertEquals("225", WeightUnit.LB.format(WeightUnit.LB.parseToKg("225")!!))
        assertEquals(82.5, WeightUnit.KG.parseToKg("82,5")!!, 0.0)
        assertNull(WeightUnit.KG.parseToKg(""))
        assertNull(WeightUnit.KG.parseToKg("abc"))
    }
}
