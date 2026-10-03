package com.viktorolsson.spotter.core.ui

import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle

/** Sample data for previews and screenshot tests. */
object PreviewData {
    fun exercise(
        id: String = "barbell-bench-press",
        name: String = "Barbell Bench Press",
        pattern: MovementPattern = MovementPattern.HORIZONTAL_PUSH,
        equipment: List<Equipment> = listOf(Equipment.BARBELL, Equipment.BENCH, Equipment.SQUAT_RACK),
        mechanics: Mechanics = Mechanics.COMPOUND,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscles = listOf(Muscle.CHEST),
        secondaryMuscles = listOf(Muscle.FRONT_DELTS, Muscle.TRICEPS),
        movementPattern = pattern,
        equipment = equipment,
        mechanics = mechanics,
        difficulty = Difficulty.INTERMEDIATE,
        unilateral = false,
        instructions = null,
        isCustom = false,
    )
}
