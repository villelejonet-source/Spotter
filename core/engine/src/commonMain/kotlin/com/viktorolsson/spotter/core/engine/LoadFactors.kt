package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.toKg
import kotlin.math.floor
import kotlin.math.roundToLong

/** The three lifts every loaded exercise is estimated from. */
internal enum class Anchor { BENCH, SQUAT, DEADLIFT }

/**
 * How heavy an exercise is relative to its anchor lift, and the plate/dumbbell steps
 * it moves in. Shared by starting weights, swap conversions and progression.
 */
internal object LoadFactors {
    /** Anchor and movement factor (vs the barbell anchor), or null when it can't be estimated. */
    fun movement(ex: Exercise): Pair<Anchor, Double>? {
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

    /** Load relative to a barbell; null for unloaded movements (bodyweight, bands): reps only. */
    fun equipment(ex: Exercise): Double? = when {
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

    private fun isHandheld(ex: Exercise) = Equipment.DUMBBELL in ex.equipment || Equipment.KETTLEBELL in ex.equipment

    private fun isBarbell(ex: Exercise) = Equipment.BARBELL in ex.equipment || Equipment.TRAP_BAR in ex.equipment

    /** Smallest sensible jump, in the user's unit. */
    fun step(ex: Exercise, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> if (isHandheld(ex)) 2.0 else 2.5
        WeightUnit.LB -> 5.0
    }

    /** Lowest loadable weight, in the user's unit (an empty bar for barbells). */
    fun minimum(ex: Exercise, unit: WeightUnit): Double =
        if (isBarbell(ex)) (if (unit == WeightUnit.LB) 45.0 else 20.0) else step(ex, unit)

    /** Rounds down to a loadable weight; null when even the lightest option is too heavy. */
    fun roundDown(kg: Double, ex: Exercise, unit: WeightUnit): Double? {
        val step = step(ex, unit)
        val inUnit = floor(unit.fromKg(kg) / step + EPSILON) * step
        if (inUnit < step) return null
        return unit.toKg(inUnit.coerceAtLeast(minimum(ex, unit)))
    }

    /** Snaps to the nearest loadable weight (used after adding increments, to avoid float drift). */
    fun snap(kg: Double, ex: Exercise, unit: WeightUnit): Double {
        val step = step(ex, unit)
        val inUnit = (unit.fromKg(kg) / step).roundToLong() * step
        return unit.toKg(inUnit.coerceAtLeast(minimum(ex, unit)))
    }

    private const val EPSILON = 1e-6
}

/**
 * Converts a working weight between exercises of the same family, e.g. barbell bench
 * 100 kg → dumbbell bench 40 kg per hand, or back squat 100 kg → leg press 180 kg.
 */
object WeightConversion {
    fun convert(from: Exercise, fromWeightKg: Double, to: Exercise, unit: WeightUnit): Double? {
        val (fromAnchor, fromMovement) = LoadFactors.movement(from) ?: return null
        val (toAnchor, toMovement) = LoadFactors.movement(to) ?: return null
        val fromEquipment = LoadFactors.equipment(from) ?: return null
        val toEquipment = LoadFactors.equipment(to) ?: return null
        if (fromAnchor != toAnchor) return null
        val anchorWeight = fromWeightKg / (fromMovement * fromEquipment)
        return LoadFactors.roundDown(anchorWeight * toMovement * toEquipment, to, unit)
    }
}
