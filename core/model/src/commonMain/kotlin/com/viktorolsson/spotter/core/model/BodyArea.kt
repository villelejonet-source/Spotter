package com.viktorolsson.spotter.core.model

/** Coarse grouping of muscles, used for filters and focus areas. */
enum class BodyArea(val muscles: Set<Muscle>) {
    CHEST(setOf(Muscle.CHEST)),
    BACK(setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.TRAPS, Muscle.LOWER_BACK)),
    SHOULDERS(setOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS)),
    ARMS(setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS)),
    LEGS(setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS, Muscle.ABDUCTORS, Muscle.CALVES)),
    GLUTES(setOf(Muscle.GLUTES)),
    CORE(setOf(Muscle.ABS, Muscle.OBLIQUES)),
}
