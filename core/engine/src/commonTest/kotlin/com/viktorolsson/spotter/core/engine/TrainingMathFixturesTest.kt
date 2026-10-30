package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.NumberInput
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
import com.viktorolsson.spotter.core.model.WorkoutSet
import com.viktorolsson.spotter.core.model.epley
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * core/model/fixtures/training-math.json: the cases the website's tests check too
 * (web/src/lib/training-math.test.ts), so the two can't drift apart.
 */
class TrainingMathFixturesTest {
    @Serializable
    private data class Fixtures(
        val timedExerciseIds: Set<String>,
        val decimalInput: List<DecimalCase>,
        val epley: List<EpleyCase>,
        val exerciseSessions: List<SessionCase>,
    )

    @Serializable
    private data class DecimalCase(val text: String, val value: Double? = null, val result: String? = null)

    @Serializable
    private data class EpleyCase(val weightKg: Double, val reps: Int, val oneRepMaxKg: Double)

    @Serializable
    private data class FixtureSet(val weightKg: Double?, val reps: Int?, val warmup: Boolean = false)

    @Serializable
    private data class SessionCase(
        val name: String,
        val exerciseId: String,
        val sets: List<FixtureSet>,
        val volumeKg: Double,
        val oneRepMaxKg: Double?,
        val bestSet: FixtureSet?,
    )

    private val fixtures = json.decodeFromString<Fixtures>(readTextFile(TRAINING_MATH_PATH))

    private fun assertClose(expected: Double?, actual: Double?, what: String) {
        if (expected == null || actual == null) return assertEquals(expected, actual, what)
        assertTrue(abs(expected - actual) < 1e-4, "$what: expected $expected, got $actual")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun timedExercisesMatch() = assertEquals(fixtures.timedExerciseIds, TIMED_EXERCISE_IDS)

    @Test
    fun decimalInput() = fixtures.decimalInput.forEach { case ->
        val expected = when (case.result) {
            "blank" -> NumberInput.Blank
            "invalid" -> NumberInput.Invalid
            null -> NumberInput.Value(case.value ?: fail("\"${case.text}\" has no value"))
            else -> fail("Unknown result ${case.result}")
        }
        assertEquals(expected, NumberInput.decimal(case.text), "decimal(\"${case.text}\")")
    }

    @Test
    fun epleyCases() = fixtures.epley.forEach { case ->
        assertClose(case.oneRepMaxKg, epley(case.weightKg, case.reps), "epley(${case.weightKg}, ${case.reps})")
        assertClose(case.oneRepMaxKg, LiftResult(case.weightKg, case.reps).estimatedOneRepMax, "LiftResult(${case.weightKg}, ${case.reps})")
    }

    @Test
    fun exerciseSessions() = fixtures.exerciseSessions.forEach { case ->
        val log = ExerciseSessionLog(
            sessionId = 1,
            date = Instant.fromEpochMilliseconds(0),
            sets = case.sets.mapIndexed { i, s ->
                WorkoutSet(
                    id = i.toLong(), position = i, weightKg = s.weightKg, reps = s.reps, rir = null,
                    setType = if (s.warmup) SetType.WARMUP else SetType.WORKING,
                    completedAt = Instant.fromEpochMilliseconds(0), notes = null,
                )
            },
            timed = case.exerciseId in TIMED_EXERCISE_IDS,
        )
        assertClose(case.volumeKg, log.volumeKg, "${case.name}: volume")
        assertClose(case.oneRepMaxKg, log.estimatedOneRepMax, "${case.name}: 1RM")
        assertEquals(case.bestSet, log.bestSet?.let { FixtureSet(it.weightKg, it.reps) }, "${case.name}: best set")
    }
}
