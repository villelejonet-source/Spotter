package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.toKg
import kotlin.math.abs

/** What to load on each side of the bar for a target weight. */
data class PlateLoading(
    /** Bar weight, in the user's unit. */
    val bar: Double,
    /** Plates per side, heaviest first, in the user's unit. */
    val perSide: List<Double>,
    /** Total actually loaded, in the user's unit; differs from the target when it can't be made exactly. */
    val total: Double,
    val exact: Boolean,
)

object Plates {
    private val KG_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    private val LB_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    /** Bar weight in [unit], or null when the exercise isn't loaded on a bar. */
    fun bar(exercise: Exercise, unit: WeightUnit): Double? {
        val kg = unit == WeightUnit.KG
        return when {
            Equipment.TRAP_BAR in exercise.equipment -> if (kg) 25.0 else 55.0
            Equipment.EZ_BAR in exercise.equipment -> if (kg) 10.0 else 25.0
            Equipment.BARBELL in exercise.equipment && Equipment.SMITH_MACHINE !in exercise.equipment -> if (kg) 20.0 else 45.0
            else -> null
        }
    }

    /** Greedy plate breakdown per side for [totalKg], or null for non-bar exercises. */
    fun load(totalKg: Double, exercise: Exercise, unit: WeightUnit): PlateLoading? {
        val bar = bar(exercise, unit) ?: return null
        val target = unit.fromKg(totalKg)
        var remaining = ((target - bar) / 2).coerceAtLeast(0.0)
        val plates = if (unit == WeightUnit.KG) KG_PLATES else LB_PLATES
        val perSide = mutableListOf<Double>()
        plates.forEach { plate ->
            while (remaining >= plate - EPS) {
                perSide += plate
                remaining -= plate
            }
        }
        val total = bar + perSide.sum() * 2
        return PlateLoading(bar, perSide, total, exact = abs(total - target) < 0.01)
    }

    private const val EPS = 1e-6
}

/**
 * Warm-up ramp for a working weight: an empty bar for 10 (barbell lifts), then
 * 50 % × 5, 70 % × 3 and 85 % × 1. [extended] adds 30 % × 8 first (older lifters,
 * per the plan). Weights round down to loadable steps; duplicates and anything at or
 * above the working weight are dropped.
 */
object WarmUps {
    fun generate(workingKg: Double, exercise: Exercise, unit: WeightUnit, extended: Boolean = false): List<SetTarget> {
        if (LoadFactors.equipment(exercise) == null) return emptyList()
        val barKg = Plates.bar(exercise, unit)?.let(unit::toKg)
        val ramp = buildList {
            if (extended) add(0.3 to 8)
            add(0.5 to 5)
            add(0.7 to 3)
            add(0.85 to 1)
        }
        val sets = mutableListOf<SetTarget>()
        if (barKg != null && barKg < workingKg * 0.5) sets += SetTarget(barKg, 10)
        ramp.forEach { (fraction, reps) ->
            val kg = LoadFactors.roundDown(workingKg * fraction, exercise, unit) ?: return@forEach
            val previous = sets.lastOrNull()?.weightKg ?: 0.0
            if (kg > previous + EPS && kg < workingKg - EPS) sets += SetTarget(kg, reps)
        }
        return sets
    }

    private const val EPS = 1e-6
}
