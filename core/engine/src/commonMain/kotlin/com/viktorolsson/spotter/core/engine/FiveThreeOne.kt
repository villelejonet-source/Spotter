package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.WeightUnit

/**
 * Wendler's 5/3/1 for one lift, from its training max (about 90 % of its 1RM). A cycle is four
 * weeks, each a percentage of the training max, with the last set of weeks 1–3 done for as many
 * reps as possible (AMRAP):
 *
 * | Week | Sets |
 * |---|---|
 * | 1 | 65 / 75 / 85 % × 5, 5, 5+ |
 * | 2 | 70 / 80 / 90 % × 3, 3, 3+ |
 * | 3 | 75 / 85 / 95 % × 5, 3, 1+ |
 * | 4 (deload) | 40 / 50 / 60 % × 5 |
 *
 * After week 4 the training max goes up (+2.5 kg upper body, +5 kg lower); an AMRAP set short of
 * its reps lowers it to 90 % and repeats the week. Each lift moves through its own weeks, one per
 * session of it.
 */
object FiveThreeOne {
    const val DELOAD_WEEK = 4
    private const val RESET_FACTOR = 0.9
    private const val TRAINING_MAX_FACTOR = 0.9

    private class Week(val percents: List<Double>, val reps: List<Int>, val amrap: Boolean)

    private val weeks = mapOf(
        1 to Week(listOf(0.65, 0.75, 0.85), listOf(5, 5, 5), amrap = true),
        2 to Week(listOf(0.70, 0.80, 0.90), listOf(3, 3, 3), amrap = true),
        3 to Week(listOf(0.75, 0.85, 0.95), listOf(5, 3, 1), amrap = true),
        DELOAD_WEEK to Week(listOf(0.40, 0.50, 0.60), listOf(5, 5, 5), amrap = false),
    )

    private fun week(number: Int) = weeks.getValue(number.coerceIn(1, DELOAD_WEEK))

    /** The sets of [week] (1–4), each rounded to a loadable weight; the last of weeks 1–3 is AMRAP. */
    fun targets(trainingMaxKg: Double, week: Int, exercise: Exercise, unit: WeightUnit): List<SetTarget> {
        val w = week(week)
        return w.percents.mapIndexed { i, percent ->
            SetTarget(LoadFactors.snap(trainingMaxKg * percent, exercise, unit), w.reps[i], amrap = w.amrap && i == w.percents.lastIndex)
        }
    }

    /** Reps the AMRAP set of [week] must reach for the week to count; null for the deload week. */
    fun amrapMinimum(week: Int): Int? = week(week).takeIf { it.amrap }?.reps?.last()

    /**
     * The training max and week after a session of [week] whose AMRAP set got [amrapReps]
     * (null when it had none, as in the deload week). Returns (training max, next week).
     */
    fun advance(trainingMaxKg: Double, week: Int, amrapReps: Int?, exercise: Exercise, unit: WeightUnit): Pair<Double, Int> {
        val current = week.coerceIn(1, DELOAD_WEEK)
        if (current == DELOAD_WEEK) {
            val raised = LoadFactors.snap(trainingMaxKg + Progression.increment(exercise, ProgressionRule.LINEAR, unit), exercise, unit)
            return raised to 1
        }
        val minimum = amrapMinimum(current)
        if (amrapReps != null && minimum != null && amrapReps < minimum) {
            val lowered = LoadFactors.roundDown(trainingMaxKg * RESET_FACTOR, exercise, unit) ?: trainingMaxKg
            return lowered to current
        }
        return trainingMaxKg to current + 1
    }

    /** A starting training max from an estimated 1RM: 90 % of it, rounded down to a loadable weight. */
    fun trainingMaxFrom(oneRepMaxKg: Double, exercise: Exercise, unit: WeightUnit): Double =
        LoadFactors.roundDown(oneRepMaxKg * TRAINING_MAX_FACTOR, exercise, unit) ?: oneRepMaxKg * TRAINING_MAX_FACTOR
}
