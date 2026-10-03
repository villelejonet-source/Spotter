package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.toKg
import kotlin.math.floor

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
    private enum class Anchor { BENCH, SQUAT, DEADLIFT }

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
        val (anchor, movementFactor) = movement(exercise) ?: return null
        val equipmentFactor = equipmentFactor(exercise) ?: return null
        val oneRepMax = anchors.getValue(anchor) * movementFactor * equipmentFactor
        val working = oneRepMax / (1 + (reps + rir) / 30.0) * CALIBRATION
        return roundDown(working, exercise)
    }

    private fun movement(ex: Exercise): Pair<Anchor, Double>? {
        if (ex.id in Rules.unloaded) return null
        overrides[ex.id]?.let { return it }
        return when (ex.movementPattern) {
            MovementPattern.HORIZONTAL_PUSH -> Anchor.BENCH to 1.0
            MovementPattern.VERTICAL_PUSH -> Anchor.BENCH to 0.62
            MovementPattern.HORIZONTAL_PULL -> Anchor.BENCH to 0.85
            MovementPattern.VERTICAL_PULL -> if (ex.mechanics == Mechanics.COMPOUND) Anchor.BENCH to 0.85 else null
            MovementPattern.SQUAT -> Anchor.SQUAT to 1.0
            MovementPattern.LUNGE -> Anchor.SQUAT to 0.45
            MovementPattern.HINGE -> Anchor.DEADLIFT to 0.7
            MovementPattern.HIP_THRUST -> if (ex.mechanics == Mechanics.COMPOUND) Anchor.DEADLIFT to 0.9 else null
            else -> null // Isolation work starts from the user's own pick.
        }
    }

    private val overrides: Map<String, Pair<Anchor, Double>> = mapOf(
        "deadlift" to (Anchor.DEADLIFT to 1.0),
        "sumo-deadlift" to (Anchor.DEADLIFT to 1.0),
        "trap-bar-deadlift" to (Anchor.DEADLIFT to 1.05),
        "rack-pull" to (Anchor.DEADLIFT to 1.1),
        "good-morning" to (Anchor.DEADLIFT to 0.4),
        "kettlebell-swing" to (Anchor.DEADLIFT to 0.75),
        "cable-pull-through" to (Anchor.DEADLIFT to 0.4),
        "leg-press" to (Anchor.SQUAT to 1.8),
        "single-leg-press" to (Anchor.SQUAT to 0.9),
        "hack-squat" to (Anchor.SQUAT to 0.9),
        "pendulum-squat" to (Anchor.SQUAT to 0.8),
        "goblet-squat" to (Anchor.SQUAT to 0.9),
        "kettlebell-goblet-squat" to (Anchor.SQUAT to 0.9),
        "front-squat" to (Anchor.SQUAT to 0.8),
        "close-grip-bench-press" to (Anchor.BENCH to 0.9),
        "incline-barbell-bench-press" to (Anchor.BENCH to 0.85),
        "incline-dumbbell-press" to (Anchor.BENCH to 0.9),
        "barbell-hip-thrust" to (Anchor.DEADLIFT to 1.0),
    )

    /** Null for unloaded movements (bodyweight, bands): reps only. */
    private fun equipmentFactor(ex: Exercise): Double? = when {
        Equipment.BARBELL in ex.equipment && Equipment.LANDMINE in ex.equipment -> 0.5
        Equipment.BARBELL in ex.equipment || Equipment.EZ_BAR in ex.equipment || Equipment.TRAP_BAR in ex.equipment -> 1.0
        Equipment.SMITH_MACHINE in ex.equipment -> 0.9
        Equipment.MACHINE in ex.equipment -> 1.0
        Equipment.CABLE in ex.equipment -> 0.8
        // Per hand: a dumbbell pair is ~80 % of the barbell, split across two hands.
        Equipment.DUMBBELL in ex.equipment || Equipment.KETTLEBELL in ex.equipment ->
            if (ex.id.contains("goblet")) 0.45 else 0.4
        else -> null
    }

    private fun roundDown(kg: Double, ex: Exercise): Double? {
        val barbell = Equipment.BARBELL in ex.equipment || Equipment.TRAP_BAR in ex.equipment
        val unit = profile.units
        val step = when (unit) {
            WeightUnit.KG -> if (Equipment.DUMBBELL in ex.equipment || Equipment.KETTLEBELL in ex.equipment) 2.0 else 2.5
            WeightUnit.LB -> 5.0
        }
        val minimum = if (barbell) (if (unit == WeightUnit.LB) 45.0 else 20.0) else step
        val inUnit = floor(unit.fromKg(kg) / step) * step
        if (inUnit < step) return null
        return unit.toKg(inUnit.coerceAtLeast(minimum))
    }

    private companion object {
        const val CALIBRATION = 0.9
    }
}
