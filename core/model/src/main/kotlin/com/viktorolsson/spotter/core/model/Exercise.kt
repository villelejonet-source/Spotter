package com.viktorolsson.spotter.core.model

data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val movementPattern: MovementPattern,
    /** Every item needed to perform the exercise. Empty means no equipment (bodyweight). */
    val equipment: List<Equipment>,
    val mechanics: Mechanics,
    val difficulty: Difficulty,
    val unilateral: Boolean,
    val instructions: String?,
    val isCustom: Boolean,
)

enum class Muscle {
    CHEST, FRONT_DELTS, SIDE_DELTS, REAR_DELTS, TRICEPS, BICEPS, FOREARMS,
    LATS, UPPER_BACK, TRAPS, LOWER_BACK, ABS, OBLIQUES,
    GLUTES, QUADS, HAMSTRINGS, ADDUCTORS, ABDUCTORS, CALVES,
}

/**
 * How an exercise moves. Plan generation fills slots by pattern, and swaps rank
 * same-pattern alternatives first, so finer isolation patterns keep swaps sensible.
 */
enum class MovementPattern {
    HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL,
    SQUAT, HINGE, LUNGE, HIP_THRUST, CARRY,
    CHEST_FLY, LATERAL_RAISE, REAR_DELT_FLY, SHRUG,
    ELBOW_FLEXION, ELBOW_EXTENSION,
    KNEE_EXTENSION, KNEE_FLEXION, HIP_ABDUCTION, HIP_ADDUCTION, CALF_RAISE,
    CORE_ANTI_EXTENSION, CORE_FLEXION, CORE_ROTATION,
}

enum class Equipment {
    BARBELL, EZ_BAR, TRAP_BAR, DUMBBELL, KETTLEBELL, CABLE, MACHINE, SMITH_MACHINE,
    BENCH, SQUAT_RACK, PULL_UP_BAR, DIP_STATION, RESISTANCE_BAND, LANDMINE,
}

enum class Mechanics { COMPOUND, ISOLATION }

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }
