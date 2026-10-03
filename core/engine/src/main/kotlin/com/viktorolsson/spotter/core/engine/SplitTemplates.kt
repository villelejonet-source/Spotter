package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MovementPattern.CALF_RAISE
import com.viktorolsson.spotter.core.model.MovementPattern.CHEST_FLY
import com.viktorolsson.spotter.core.model.MovementPattern.CORE_ANTI_EXTENSION
import com.viktorolsson.spotter.core.model.MovementPattern.CORE_FLEXION
import com.viktorolsson.spotter.core.model.MovementPattern.CORE_ROTATION
import com.viktorolsson.spotter.core.model.MovementPattern.ELBOW_EXTENSION
import com.viktorolsson.spotter.core.model.MovementPattern.ELBOW_FLEXION
import com.viktorolsson.spotter.core.model.MovementPattern.HINGE
import com.viktorolsson.spotter.core.model.MovementPattern.HIP_THRUST
import com.viktorolsson.spotter.core.model.MovementPattern.HORIZONTAL_PULL
import com.viktorolsson.spotter.core.model.MovementPattern.HORIZONTAL_PUSH
import com.viktorolsson.spotter.core.model.MovementPattern.KNEE_EXTENSION
import com.viktorolsson.spotter.core.model.MovementPattern.KNEE_FLEXION
import com.viktorolsson.spotter.core.model.MovementPattern.LATERAL_RAISE
import com.viktorolsson.spotter.core.model.MovementPattern.LUNGE
import com.viktorolsson.spotter.core.model.MovementPattern.REAR_DELT_FLY
import com.viktorolsson.spotter.core.model.MovementPattern.SQUAT
import com.viktorolsson.spotter.core.model.MovementPattern.VERTICAL_PULL
import com.viktorolsson.spotter.core.model.MovementPattern.VERTICAL_PUSH

/** How much a slot matters: drives sets/reps/rest and the order slots are dropped in when time is short. */
enum class SlotRole { MAIN, SECONDARY, ACCESSORY }

/** What a day trains, used to decide which focus-area accessories belong on it. */
enum class DayRegion { FULL, UPPER, LOWER, PUSH, PULL, LEGS }

/**
 * One exercise position in a day. [patterns] are tried in order: the first one
 * with an available exercise wins, so later entries are fallbacks.
 */
data class Slot(val role: SlotRole, val patterns: List<MovementPattern>) {
    constructor(role: SlotRole, vararg patterns: MovementPattern) : this(role, patterns.toList())
}

/** A day of a split. [key] identifies repeats (e.g. PPL×2 runs "push" twice with the same exercises). */
data class DayTemplate(val key: String, val name: String, val region: DayRegion, val slots: List<Slot>)

private val M = SlotRole.MAIN
private val S = SlotRole.SECONDARY
private val A = SlotRole.ACCESSORY

internal object Templates {
    val fullA = DayTemplate(
        "full-a", "Full body A", DayRegion.FULL,
        listOf(
            Slot(M, SQUAT, LUNGE), Slot(M, HORIZONTAL_PUSH), Slot(S, HORIZONTAL_PULL, VERTICAL_PULL),
            Slot(S, HINGE, HIP_THRUST), Slot(A, LATERAL_RAISE), Slot(A, ELBOW_FLEXION), Slot(A, CORE_ANTI_EXTENSION),
        ),
    )
    val fullB = DayTemplate(
        "full-b", "Full body B", DayRegion.FULL,
        listOf(
            Slot(M, HINGE, HIP_THRUST), Slot(M, VERTICAL_PUSH, HORIZONTAL_PUSH), Slot(S, VERTICAL_PULL, HORIZONTAL_PULL),
            Slot(S, LUNGE, SQUAT), Slot(A, CHEST_FLY), Slot(A, ELBOW_EXTENSION), Slot(A, CORE_FLEXION),
        ),
    )
    val fullC = DayTemplate(
        "full-c", "Full body C", DayRegion.FULL,
        listOf(
            Slot(M, SQUAT, LUNGE), Slot(M, HORIZONTAL_PULL, VERTICAL_PULL), Slot(S, HORIZONTAL_PUSH),
            Slot(S, HIP_THRUST, HINGE), Slot(A, REAR_DELT_FLY), Slot(A, KNEE_FLEXION), Slot(A, CALF_RAISE),
        ),
    )
    val upperA = DayTemplate(
        "upper-a", "Upper A", DayRegion.UPPER,
        listOf(
            Slot(M, HORIZONTAL_PUSH), Slot(M, HORIZONTAL_PULL, VERTICAL_PULL), Slot(S, VERTICAL_PUSH, HORIZONTAL_PUSH),
            Slot(S, VERTICAL_PULL, HORIZONTAL_PULL), Slot(A, LATERAL_RAISE), Slot(A, ELBOW_FLEXION), Slot(A, ELBOW_EXTENSION),
        ),
    )
    val upperB = DayTemplate(
        "upper-b", "Upper B", DayRegion.UPPER,
        listOf(
            Slot(M, VERTICAL_PUSH, HORIZONTAL_PUSH), Slot(M, VERTICAL_PULL, HORIZONTAL_PULL), Slot(S, HORIZONTAL_PUSH),
            Slot(S, HORIZONTAL_PULL, VERTICAL_PULL), Slot(A, CHEST_FLY), Slot(A, REAR_DELT_FLY), Slot(A, ELBOW_FLEXION),
        ),
    )
    val lowerA = DayTemplate(
        "lower-a", "Lower A", DayRegion.LOWER,
        listOf(
            Slot(M, SQUAT, LUNGE), Slot(S, HINGE, HIP_THRUST), Slot(S, LUNGE, SQUAT),
            Slot(A, KNEE_FLEXION), Slot(A, CALF_RAISE), Slot(A, CORE_ANTI_EXTENSION),
        ),
    )
    val lowerB = DayTemplate(
        "lower-b", "Lower B", DayRegion.LOWER,
        listOf(
            Slot(M, HINGE, HIP_THRUST), Slot(S, SQUAT, LUNGE), Slot(S, HIP_THRUST, HINGE),
            Slot(A, KNEE_EXTENSION), Slot(A, KNEE_FLEXION), Slot(A, CORE_ROTATION),
        ),
    )
    val push = DayTemplate(
        "push", "Push", DayRegion.PUSH,
        listOf(
            Slot(M, HORIZONTAL_PUSH), Slot(S, VERTICAL_PUSH), Slot(S, HORIZONTAL_PUSH),
            Slot(A, LATERAL_RAISE), Slot(A, CHEST_FLY), Slot(A, ELBOW_EXTENSION),
        ),
    )
    val pull = DayTemplate(
        "pull", "Pull", DayRegion.PULL,
        listOf(
            Slot(M, VERTICAL_PULL, HORIZONTAL_PULL), Slot(S, HORIZONTAL_PULL), Slot(S, HORIZONTAL_PULL, VERTICAL_PULL),
            Slot(A, REAR_DELT_FLY), Slot(A, ELBOW_FLEXION), Slot(A, ELBOW_FLEXION),
        ),
    )
    val legs = DayTemplate(
        "legs", "Legs", DayRegion.LEGS,
        listOf(
            Slot(M, SQUAT, LUNGE), Slot(S, HINGE, HIP_THRUST), Slot(S, LUNGE, SQUAT),
            Slot(A, KNEE_EXTENSION), Slot(A, KNEE_FLEXION), Slot(A, CALF_RAISE),
        ),
    )
}
