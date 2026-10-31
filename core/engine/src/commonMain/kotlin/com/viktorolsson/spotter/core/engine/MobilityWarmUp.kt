package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle

/**
 * A mobility drill for the start of a session. None need equipment. Not catalog exercises:
 * they're never logged, so they stay out of history, stats and sync.
 */
enum class MobilityDrill(
    /** Reps, or seconds when [timed]. */
    val amount: Int,
    val timed: Boolean = false,
    /** [amount] is per side (or per direction). */
    val perSide: Boolean = false,
) {
    JUMPING_JACKS(60, timed = true),
    INCHWORM(5),

    ARM_CIRCLES(10, perSide = true),
    SCAPULAR_PUSH_UP(10),
    WALL_SLIDE(10),
    PRONE_Y_RAISE(10),
    THORACIC_ROTATION(8, perSide = true),
    CROSS_BODY_ARM_SWING(10),

    LEG_SWING(10, perSide = true),
    HIP_90_90(5, perSide = true),
    WORLDS_GREATEST_STRETCH(5, perSide = true),
    BODYWEIGHT_SQUAT(10),
    GLUTE_BRIDGE(10),
    ANKLE_ROCK(10, perSide = true),
    CAT_COW(8),
    COSSACK_SQUAT(5, perSide = true),
}

/**
 * Picks a short mobility warm-up for what a session trains: an easy opener, then drills
 * for the upper and lower body in proportion to the working sets each gets, with drills
 * that suit the session's main movements first. Deterministic for the same exercises.
 */
object MobilityWarmUp {
    const val MAX_DRILLS = 6

    /** [exercises] paired with how many sets each has; an empty list gets a general warm-up. */
    fun forSession(exercises: List<Pair<Exercise, Int>>): List<MobilityDrill> {
        var upper = 0.0
        var lower = 0.0
        exercises.forEach { (exercise, sets) ->
            val weight = sets.coerceAtLeast(1).toDouble() / exercise.primaryMuscles.size.coerceAtLeast(1)
            exercise.primaryMuscles.forEach {
                when (it) {
                    in UPPER_MUSCLES -> upper += weight
                    in LOWER_MUSCLES -> lower += weight
                    else -> Unit
                }
            }
        }
        if (upper + lower == 0.0) return GENERAL
        val slots = MAX_DRILLS - 1
        val upperShare = upper / (upper + lower)
        val upperCount = when {
            upperShare >= 0.75 -> slots
            upperShare <= 0.25 -> 0
            upperShare >= 0.5 -> 3
            else -> 2
        }
        val patterns = exercises.map { it.first.movementPattern }.toSet()
        return listOf(MobilityDrill.JUMPING_JACKS) +
            ordered(UPPER, UPPER_BOOSTS, patterns).take(upperCount) +
            ordered(LOWER, LOWER_BOOSTS, patterns).take(slots - upperCount)
    }

    /**
     * [drills] with those suiting [patterns] first: drills that suit more of the session's
     * movements, then those listed earlier for a movement, then the default order.
     */
    private fun ordered(
        drills: List<MobilityDrill>,
        boosts: Map<MovementPattern, List<MobilityDrill>>,
        patterns: Set<MovementPattern>,
    ): List<MobilityDrill> {
        val lists = patterns.mapNotNull { boosts[it] }
        return drills.sortedWith(
            compareByDescending<MobilityDrill> { drill -> lists.count { drill in it } }
                .thenBy { drill -> lists.minOfOrNull { it.indexOf(drill).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE } ?: Int.MAX_VALUE }
                .thenBy { drills.indexOf(it) },
        )
    }

    private val UPPER_MUSCLES =
        BodyArea.CHEST.muscles + BodyArea.SHOULDERS.muscles + BodyArea.ARMS.muscles + BodyArea.BACK.muscles - Muscle.LOWER_BACK
    private val LOWER_MUSCLES = BodyArea.LEGS.muscles + BodyArea.GLUTES.muscles + Muscle.LOWER_BACK

    private val GENERAL = listOf(
        MobilityDrill.JUMPING_JACKS, MobilityDrill.INCHWORM, MobilityDrill.ARM_CIRCLES,
        MobilityDrill.WORLDS_GREATEST_STRETCH, MobilityDrill.BODYWEIGHT_SQUAT,
    )

    private val UPPER = listOf(
        MobilityDrill.ARM_CIRCLES, MobilityDrill.SCAPULAR_PUSH_UP, MobilityDrill.WALL_SLIDE,
        MobilityDrill.THORACIC_ROTATION, MobilityDrill.PRONE_Y_RAISE, MobilityDrill.CROSS_BODY_ARM_SWING,
    )

    private val LOWER = listOf(
        MobilityDrill.LEG_SWING, MobilityDrill.HIP_90_90, MobilityDrill.WORLDS_GREATEST_STRETCH,
        MobilityDrill.BODYWEIGHT_SQUAT, MobilityDrill.GLUTE_BRIDGE, MobilityDrill.ANKLE_ROCK,
        MobilityDrill.CAT_COW, MobilityDrill.COSSACK_SQUAT,
    )

    private val UPPER_BOOSTS = mapOf(
        MovementPattern.VERTICAL_PUSH to listOf(MobilityDrill.WALL_SLIDE, MobilityDrill.PRONE_Y_RAISE),
        MovementPattern.LATERAL_RAISE to listOf(MobilityDrill.WALL_SLIDE),
        MovementPattern.HORIZONTAL_PUSH to listOf(MobilityDrill.SCAPULAR_PUSH_UP, MobilityDrill.CROSS_BODY_ARM_SWING),
        MovementPattern.CHEST_FLY to listOf(MobilityDrill.CROSS_BODY_ARM_SWING),
        MovementPattern.VERTICAL_PULL to listOf(MobilityDrill.THORACIC_ROTATION, MobilityDrill.PRONE_Y_RAISE),
        MovementPattern.HORIZONTAL_PULL to listOf(MobilityDrill.THORACIC_ROTATION),
    )

    private val LOWER_BOOSTS = mapOf(
        MovementPattern.SQUAT to listOf(MobilityDrill.ANKLE_ROCK, MobilityDrill.BODYWEIGHT_SQUAT, MobilityDrill.HIP_90_90),
        MovementPattern.LUNGE to listOf(MobilityDrill.WORLDS_GREATEST_STRETCH, MobilityDrill.COSSACK_SQUAT),
        MovementPattern.HINGE to listOf(MobilityDrill.CAT_COW, MobilityDrill.GLUTE_BRIDGE, MobilityDrill.LEG_SWING),
        MovementPattern.HIP_THRUST to listOf(MobilityDrill.GLUTE_BRIDGE),
    )
}
