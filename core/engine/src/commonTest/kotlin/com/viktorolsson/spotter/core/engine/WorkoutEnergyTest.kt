package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.WorkoutEnergy.Intensity
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class WorkoutEnergyTest {
    private val start = Fixtures.now

    /** [sets] working sets of [exerciseId], ticked evenly up to [lastSetAfter]; finished [length] after the start. */
    private fun session(
        sets: Int = 15,
        exerciseId: String = "barbell-bench-press",
        rir: Int? = null,
        length: Duration? = 60.minutes,
        lastSetAfter: Duration = 58.minutes,
        lighter: Boolean = false,
        warmups: Int = 0,
    ) = WorkoutSession(
        id = 1,
        startedAt = start,
        endedAt = length?.let { start + it },
        notes = null,
        isLighter = lighter,
        exercises = listOf(
            SessionExercise(
                id = 1, exercise = byId.getValue(exerciseId), position = 0, supersetGroup = null, restSeconds = null, notes = null,
                sets = List(warmups + sets) { i ->
                    WorkoutSet(
                        id = i.toLong(), position = i, weightKg = 60.0, reps = 8, rir = rir,
                        setType = if (i < warmups) SetType.WARMUP else SetType.WORKING,
                        completedAt = start + lastSetAfter * (i + 1) / (warmups + sets), notes = null,
                    )
                },
            ),
        ),
    )

    private val man = Fixtures.profile(sex = Sex.MALE, heightCm = 180.0, weightKg = 80.0) // rests at 1790 kcal a day
    private val woman = Fixtures.profile() // 1369 kcal a day

    @Test
    fun `an hour of steady compound lifting is moderate`() {
        assertEquals(Intensity.MODERATE, WorkoutEnergy.intensity(session(), 60.minutes))
        assertEquals(298, WorkoutEnergy.activeKcal(session(), man))
        assertEquals(228, WorkoutEnergy.activeKcal(session(), woman))
    }

    @Test
    fun `dense sets near failure make it vigorous`() {
        val hard = session(sets = 20, rir = 1)
        assertEquals(Intensity.VIGOROUS, WorkoutEnergy.intensity(hard, 60.minutes))
        assertEquals(373, WorkoutEnergy.activeKcal(hard, man))
    }

    @Test
    fun `few easy isolation sets are light`() {
        val easy = session(sets = 6, exerciseId = "dumbbell-lateral-raise", rir = 4)
        assertEquals(Intensity.LIGHT, WorkoutEnergy.intensity(easy, 60.minutes))
        assertEquals(186, WorkoutEnergy.activeKcal(easy, man))
    }

    @Test
    fun `a lighter session is light however it was logged`() {
        assertEquals(Intensity.LIGHT, WorkoutEnergy.intensity(session(sets = 20, rir = 0, lighter = true), 60.minutes))
    }

    @Test
    fun `warm-up sets do not count towards density`() {
        // 8 working sets in an hour is sparse, whatever the warm-ups add.
        assertEquals(Intensity.MODERATE, WorkoutEnergy.intensity(session(sets = 8, warmups = 12), 60.minutes))
        assertEquals(Intensity.MODERATE, WorkoutEnergy.intensity(session(sets = 8), 60.minutes))
    }

    @Test
    fun `a longer workout burns more`() {
        val short = WorkoutEnergy.activeKcal(session(length = 30.minutes, lastSetAfter = 28.minutes, sets = 8), man)!!
        val long = WorkoutEnergy.activeKcal(session(length = 90.minutes, lastSetAfter = 88.minutes, sets = 23), man)!!
        assertEquals(149, short)
        assertEquals(448, long)
    }

    @Test
    fun `a workout left open ends five minutes after its last set`() {
        val forgotten = session(length = 9.hours, lastSetAfter = 50.minutes)
        assertEquals(start + 55.minutes, WorkoutEnergy.trainedUntil(forgotten))
        assertEquals(WorkoutEnergy.activeKcal(session(length = 55.minutes, lastSetAfter = 50.minutes), man), WorkoutEnergy.activeKcal(forgotten, man))
    }

    @Test
    fun `finishing right after the last set keeps the real end`() {
        assertEquals(start + 60.minutes, WorkoutEnergy.trainedUntil(session()))
    }

    @Test
    fun `a workout with nothing ticked is capped at four hours`() {
        assertEquals(start + 4.hours, WorkoutEnergy.trainedUntil(session(sets = 0, length = 9.hours)))
    }

    @Test
    fun `a workout in progress has no end and no calories`() {
        assertNull(WorkoutEnergy.trainedUntil(session(length = null)))
        assertNull(WorkoutEnergy.activeKcal(session(length = null), man))
    }

    @Test
    fun `a profile without weight or height gives no calories`() {
        assertNull(WorkoutEnergy.activeKcal(session(), man.copy(bodyWeightKg = 0.0)))
        assertNull(WorkoutEnergy.activeKcal(session(), man.copy(heightCm = 0.0)))
    }
}
