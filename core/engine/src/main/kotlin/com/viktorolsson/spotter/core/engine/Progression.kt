package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.model.toKg

/** A completed working set from history. Weight null = bodyweight. */
data class LoggedSet(val weightKg: Double?, val reps: Int, val rir: Int? = null)

data class ProgressionInput(
    val exercise: Exercise,
    val rule: ProgressionRule,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
    /** Working sets per past session, most recent first; usually the last two sessions. */
    val history: List<List<LoggedSet>>,
    /** Calibration estimate used when there's no history. */
    val startingWeightKg: Double?,
    val unit: WeightUnit,
)

data class SetTarget(val weightKg: Double?, val reps: Int)

data class ProgressionTarget(val sets: List<SetTarget>, val reason: ProgressionReason)

/**
 * Next session's targets for one exercise. Every outcome carries a reason the UI can
 * explain ("all sets hit 12 last time, so +2.5 kg").
 *
 * - Double progression: climb the rep range at a fixed weight; once every set reaches
 *   the top, add the smallest sensible increment and drop to the bottom of the range.
 * - Linear: add weight every session the target reps are hit on every set.
 * - Both: two misses in a row reset the weight by 10 %; logged RIR far from the target
 *   holds back an increase (too hard) or adds one (too easy).
 */
object Progression {
    private const val RESET_FACTOR = 0.9
    /** RIR this far off target counts as "much harder/easier than planned". */
    private const val RIR_TOLERANCE = 2
    /** Holds climb their range in seconds, a few at a time. */
    const val TIMED_STEP_SECONDS = 5

    fun next(input: ProgressionInput): ProgressionTarget {
        val last = input.history.firstOrNull()
        if (last.isNullOrEmpty()) return firstTime(input)
        val weight = last.mapNotNull { it.weightKg }.maxOrNull()
        if (weight == null) return bodyweight(input, last)

        val missedTwice = input.history.size >= 2 && input.history.take(2).all { missed(input, it) }
        if (missedTwice) {
            val reset = LoadFactors.roundDown(weight * RESET_FACTOR, input.exercise, input.unit) ?: weight
            return uniform(input, reset, targetReps(input), ProgressionReason.RESET_AFTER_MISSES)
        }

        val rir = averageRir(last)
        val tooHard = rir != null && input.targetRir != null && rir <= input.targetRir - RIR_TOLERANCE
        val tooEasy = rir != null && input.targetRir != null && rir >= input.targetRir + RIR_TOLERANCE
        val earnedIncrease = hitTarget(input, last)

        return when {
            earnedIncrease && tooHard -> holdAt(input, last, weight, ProgressionReason.HOLD_TOO_HARD)
            earnedIncrease -> uniform(input, increase(input, weight), targetReps(input), ProgressionReason.INCREASE_WEIGHT)
            tooEasy -> uniform(input, increase(input, weight), targetReps(input), ProgressionReason.INCREASE_TOO_EASY)
            input.rule == ProgressionRule.LINEAR || missed(input, last) ->
                uniform(input, weight, targetReps(input), ProgressionReason.REPEAT)
            else -> holdAt(input, last, weight, ProgressionReason.ADD_REPS)
        }
    }

    /** Linear progression works at one fixed rep target inside the range. */
    private fun targetReps(input: ProgressionInput): Int = when (input.rule) {
        ProgressionRule.LINEAR -> input.repMin + (input.repMax - input.repMin) / 2
        else -> input.repMin
    }

    private fun hitTarget(input: ProgressionInput, sets: List<LoggedSet>): Boolean {
        if (sets.size < input.sets) return false
        val needed = if (input.rule == ProgressionRule.LINEAR) targetReps(input) else input.repMax
        return sets.take(input.sets).all { it.reps >= needed }
    }

    /** A miss: most sets fell short of the target (linear) or the bottom of the range (double). */
    private fun missed(input: ProgressionInput, sets: List<LoggedSet>): Boolean {
        val floor = if (input.rule == ProgressionRule.LINEAR) targetReps(input) else input.repMin
        return sets.count { it.reps < floor } * 2 > sets.size
    }

    /** One more rep per session, or a few more seconds for a hold. */
    private fun repStep(input: ProgressionInput): Int = if (input.exercise.isTimed) TIMED_STEP_SECONDS else 1

    /** Same weight, one more rep (or a longer hold) per set than last time, within the range. */
    private fun holdAt(input: ProgressionInput, last: List<LoggedSet>, weight: Double, reason: ProgressionReason) =
        ProgressionTarget(
            List(input.sets) { i ->
                val previous = (last.getOrNull(i) ?: last.last()).reps
                val reps = if (reason == ProgressionReason.ADD_REPS) previous + repStep(input) else previous
                SetTarget(weight, reps.coerceIn(input.repMin, input.repMax))
            },
            reason,
        )

    private fun bodyweight(input: ProgressionInput, last: List<LoggedSet>): ProgressionTarget {
        val atTop = last.size >= input.sets && last.all { it.reps >= input.repMax }
        return ProgressionTarget(
            List(input.sets) { i ->
                val previous = (last.getOrNull(i) ?: last.last()).reps
                SetTarget(null, if (atTop) input.repMax else (previous + repStep(input)).coerceIn(input.repMin, input.repMax))
            },
            if (atTop) ProgressionReason.BODYWEIGHT_TOP_OF_RANGE else ProgressionReason.ADD_REPS,
        )
    }

    private fun firstTime(input: ProgressionInput) = uniform(
        input,
        input.startingWeightKg,
        targetReps(input),
        if (input.startingWeightKg != null) ProgressionReason.CALIBRATION else ProgressionReason.FIRST_TIME,
    )

    private fun uniform(input: ProgressionInput, weightKg: Double?, reps: Int, reason: ProgressionReason) =
        ProgressionTarget(List(input.sets) { SetTarget(weightKg, reps) }, reason)

    private fun averageRir(sets: List<LoggedSet>): Double? =
        sets.mapNotNull { it.rir }.takeIf { it.isNotEmpty() }?.average()

    /**
     * +2.5 kg (5 lb) for most lifts, the next dumbbell for handheld work, and a bigger
     * +5 kg (10 lb) jump for linear lower-body lifts, which progress fastest.
     */
    fun increment(exercise: Exercise, rule: ProgressionRule, unit: WeightUnit): Double {
        val lowerBodyCompound = exercise.mechanics == Mechanics.COMPOUND && exercise.movementPattern in setOf(
            MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.HIP_THRUST,
        )
        val step = LoadFactors.step(exercise, unit)
        return unit.toKg(if (rule == ProgressionRule.LINEAR && lowerBodyCompound) step * 2 else step)
    }

    private fun increase(input: ProgressionInput, weightKg: Double): Double =
        LoadFactors.snap(weightKg + increment(input.exercise, input.rule, input.unit), input.exercise, input.unit)
}

/** Deload week: about 60 % of the sets at 90 % of the weight, reps at the bottom of the range. */
object Deload {
    private const val SET_FACTOR = 0.6
    private const val WEIGHT_FACTOR = 0.9

    fun lighten(target: ProgressionTarget, exercise: Exercise, repMin: Int, unit: WeightUnit): ProgressionTarget {
        val sets = Math.round(target.sets.size * SET_FACTOR).toInt().coerceAtLeast(2).coerceAtMost(target.sets.size)
        return ProgressionTarget(
            target.sets.take(sets).map { set ->
                SetTarget(set.weightKg?.let { LoadFactors.roundDown(it * WEIGHT_FACTOR, exercise, unit) ?: it }, repMin)
            },
            ProgressionReason.DELOAD,
        )
    }
}
