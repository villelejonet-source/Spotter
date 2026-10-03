package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile

/** A set the user reports, e.g. "bench 80 kg × 5". */
data class LiftResult(val weightKg: Double, val reps: Int) {
    /** Epley estimated one-rep max. */
    val estimatedOneRepMax: Double get() = weightKg * (1 + reps / 30.0)
}

data class KnownLifts(
    val bench: LiftResult? = null,
    val squat: LiftResult? = null,
    val deadlift: LiftResult? = null,
)

/**
 * First-session weights ("calibration"). Each exercise is estimated from one of three
 * anchor lifts, then scaled by movement and equipment. Deliberately conservative
 * (90 %, rounded down): it's much easier to add weight mid-session than to recover
 * from an opening set that's too heavy.
 */
internal class StartingWeights(
    private val profile: UserProfile,
    private val known: KnownLifts,
    private val ageYears: Int,
) {
    private val anchors: Map<Anchor, Double> = Anchor.entries.associateWith { anchor ->
        val reported = when (anchor) {
            Anchor.BENCH -> known.bench
            Anchor.SQUAT -> known.squat
            Anchor.DEADLIFT -> known.deadlift
        }
        reported?.estimatedOneRepMax ?: (profile.bodyWeightKg * ratio(anchor) * if (ageYears >= 50) 0.85 else 1.0)
    }

    /** Estimated 1RM as a multiple of bodyweight, by sex and experience (rough population standards). */
    private fun ratio(anchor: Anchor): Double {
        val (new, mid, adv) = when (profile.sex) {
            Sex.MALE -> when (anchor) {
                Anchor.BENCH -> Triple(0.6, 0.9, 1.25)
                Anchor.SQUAT -> Triple(0.8, 1.2, 1.6)
                Anchor.DEADLIFT -> Triple(1.0, 1.5, 2.0)
            }
            Sex.FEMALE -> when (anchor) {
                Anchor.BENCH -> Triple(0.35, 0.55, 0.8)
                Anchor.SQUAT -> Triple(0.6, 0.9, 1.25)
                Anchor.DEADLIFT -> Triple(0.7, 1.1, 1.5)
            }
        }
        return when (profile.experience) {
            ExperienceLevel.NEW -> new
            ExperienceLevel.INTERMEDIATE -> mid
            ExperienceLevel.ADVANCED -> adv
        }
    }

    fun estimateKg(exercise: Exercise, reps: Int, rir: Int): Double? {
        val (anchor, movementFactor) = LoadFactors.movement(exercise) ?: return null
        val equipmentFactor = LoadFactors.equipment(exercise) ?: return null
        val oneRepMax = anchors.getValue(anchor) * movementFactor * equipmentFactor
        val working = oneRepMax / (1 + (reps + rir) / 30.0) * CALIBRATION
        return LoadFactors.roundDown(working, exercise, profile.units)
    }

    private companion object {
        const val CALIBRATION = 0.9
    }
}
