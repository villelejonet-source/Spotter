package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MovementPattern

/**
 * Data tables behind exercise selection. Keeping them as data (not branches per
 * lift) makes every rule easy to read, test and tweak.
 */
internal object Rules {
    /** Well-known staples get a nudge so plans look like what a coach would write. */
    val staples = setOf(
        "back-squat", "barbell-bench-press", "deadlift", "romanian-deadlift", "overhead-press", "barbell-row",
        "pull-up", "lat-pulldown", "leg-press", "dumbbell-bench-press", "incline-dumbbell-press",
        "seated-cable-row", "one-arm-dumbbell-row", "seated-dumbbell-shoulder-press", "goblet-squat",
        "dumbbell-romanian-deadlift", "barbell-hip-thrust", "bulgarian-split-squat", "walking-lunge",
        "lying-leg-curl", "seated-leg-curl", "leg-extension", "standing-calf-raise", "dumbbell-lateral-raise",
        "cable-lateral-raise", "face-pull", "cable-crossover", "dumbbell-fly", "ez-bar-curl", "dumbbell-curl",
        "hammer-curl", "triceps-pushdown", "overhead-cable-triceps-extension", "cable-crunch", "plank",
        "pallof-press", "hanging-knee-raise", "push-up", "inverted-row", "chin-up", "glute-bridge",
        "dumbbell-hip-thrust", "machine-chest-press", "machine-row",
    )

    /** Need equipment, but the load is the body (the bar is just a handle): no starting weight, no barbell bonus. */
    val unloaded = setOf("inverted-row", "back-extension", "reverse-hyperextension")

    /** Heavy, technical pulls that only make sense as a low-rep main lift. */
    val mainLiftOnly = setOf("deadlift", "sumo-deadlift", "deficit-deadlift", "paused-deadlift", "rack-pull")

    /** Exercises that load the limited area directly: never programmed. */
    val excludedFor: Map<Limitation, Set<String>> = mapOf(
        Limitation.SHOULDER to setOf(
            "upright-row", "chest-dip", "triceps-dip", "bench-dip", "handstand-push-up", "z-press", "push-press",
            "pike-push-up", "dumbbell-pullover",
        ),
        Limitation.KNEE to setOf(
            "pistol-squat", "sissy-squat", "reverse-nordic", "cossack-squat", "barbell-walking-lunge", "walking-lunge",
            "paused-squat",
        ),
        Limitation.LOWER_BACK to setOf(
            "good-morning", "deficit-deadlift", "paused-deadlift", "deadlift", "stiff-leg-deadlift", "pendlay-row",
            "barbell-row", "band-good-morning", "barbell-rollout",
        ),
        Limitation.WRIST to setOf("front-squat", "handstand-push-up", "barbell-curl", "diamond-push-up", "z-press"),
    )

    /** Score penalty for exercises that stress a limited area less directly. */
    fun limitationPenalty(limitation: Limitation, exercise: Exercise): Double = when (limitation) {
        Limitation.SHOULDER -> when {
            exercise.movementPattern == MovementPattern.VERTICAL_PUSH && exercise.id != "landmine-press" -> 3.0
            exercise.movementPattern == MovementPattern.LATERAL_RAISE -> 1.0
            Equipment.BARBELL in exercise.equipment && exercise.movementPattern == MovementPattern.HORIZONTAL_PUSH -> 1.5
            else -> 0.0
        }
        Limitation.KNEE -> when (exercise.movementPattern) {
            MovementPattern.LUNGE -> 3.0
            MovementPattern.SQUAT -> if (exercise.id in setOf("box-squat", "leg-press", "belt-squat")) 0.0 else 1.5
            MovementPattern.KNEE_EXTENSION -> 1.0
            else -> 0.0
        }
        Limitation.LOWER_BACK -> when {
            exercise.movementPattern == MovementPattern.HINGE && Equipment.BARBELL in exercise.equipment -> 3.0
            exercise.movementPattern == MovementPattern.SQUAT && Equipment.BARBELL in exercise.equipment -> 2.0
            exercise.movementPattern == MovementPattern.HORIZONTAL_PULL && Equipment.BENCH !in exercise.equipment &&
                Equipment.CABLE !in exercise.equipment && Equipment.MACHINE !in exercise.equipment -> 1.5
            else -> 0.0
        }
        Limitation.WRIST -> when {
            Equipment.BARBELL in exercise.equipment && exercise.movementPattern in setOf(
                MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH, MovementPattern.ELBOW_FLEXION,
            ) -> 2.0
            exercise.id in setOf("push-up", "incline-push-up", "decline-push-up", "band-push-up", "plank") -> 2.0
            else -> 0.0
        }
    }

    /** Bodyweight-dependent compounds swapped out at a high BMI (pull-ups → lat pulldown, etc.). */
    fun isBodyweightDependent(exercise: Exercise): Boolean =
        exercise.equipment.all { it in setOf(Equipment.PULL_UP_BAR, Equipment.DIP_STATION, Equipment.BENCH) } &&
            exercise.movementPattern in setOf(
                MovementPattern.VERTICAL_PULL, MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
                MovementPattern.ELBOW_EXTENSION, MovementPattern.SQUAT, MovementPattern.LUNGE, MovementPattern.CORE_FLEXION,
            )

    /** Extra accessory patterns for each focus area, and the days they fit on. */
    val focusPatterns: Map<BodyArea, List<MovementPattern>> = mapOf(
        BodyArea.GLUTES to listOf(MovementPattern.HIP_THRUST, MovementPattern.HIP_ABDUCTION),
        BodyArea.LEGS to listOf(MovementPattern.KNEE_EXTENSION, MovementPattern.KNEE_FLEXION, MovementPattern.CALF_RAISE),
        BodyArea.CHEST to listOf(MovementPattern.CHEST_FLY, MovementPattern.HORIZONTAL_PUSH),
        BodyArea.BACK to listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL),
        BodyArea.SHOULDERS to listOf(MovementPattern.LATERAL_RAISE, MovementPattern.REAR_DELT_FLY),
        BodyArea.ARMS to listOf(MovementPattern.ELBOW_FLEXION, MovementPattern.ELBOW_EXTENSION),
        BodyArea.CORE to listOf(MovementPattern.CORE_ANTI_EXTENSION, MovementPattern.CORE_ROTATION, MovementPattern.CORE_FLEXION),
    )

    val focusRegions: Map<BodyArea, Set<DayRegion>> = mapOf(
        BodyArea.GLUTES to setOf(DayRegion.FULL, DayRegion.LOWER, DayRegion.LEGS),
        BodyArea.LEGS to setOf(DayRegion.FULL, DayRegion.LOWER, DayRegion.LEGS),
        BodyArea.CHEST to setOf(DayRegion.FULL, DayRegion.UPPER, DayRegion.PUSH),
        BodyArea.BACK to setOf(DayRegion.FULL, DayRegion.UPPER, DayRegion.PULL),
        BodyArea.SHOULDERS to setOf(DayRegion.FULL, DayRegion.UPPER, DayRegion.PUSH, DayRegion.PULL),
        BodyArea.ARMS to setOf(DayRegion.FULL, DayRegion.UPPER, DayRegion.PUSH, DayRegion.PULL),
        BodyArea.CORE to DayRegion.entries.toSet(),
    )
}
