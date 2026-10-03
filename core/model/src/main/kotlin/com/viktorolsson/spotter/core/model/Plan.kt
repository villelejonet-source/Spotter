package com.viktorolsson.spotter.core.model

import java.time.Instant

enum class SplitType {
    FULL_BODY,
    UPPER_LOWER,
    PUSH_PULL_LEGS,
    /** 5 days: Upper, Lower, Full, Upper, Lower. */
    UPPER_LOWER_FULL,
    /** 5 days: Push, Pull, Legs, Upper, Lower. */
    PPL_UPPER_LOWER,
    CUSTOM,
}

enum class ProgressionRule { DOUBLE_PROGRESSION, LINEAR, RIR_AUTOREGULATION, NONE }

data class Plan(
    val id: Long,
    val name: String,
    val splitType: SplitType,
    val goal: Goal,
    val createdAt: Instant,
    val days: List<PlanDay>,
)

data class PlanDay(
    val id: Long,
    val position: Int,
    val name: String,
    val exercises: List<PlanExercise>,
)

data class PlanExercise(
    val id: Long,
    val exercise: Exercise,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
    val restSeconds: Int,
    val progressionRule: ProgressionRule,
    val supersetGroup: Int?,
    /** First-session estimate ("calibration"); null when it can't be estimated (bodyweight, isolation). */
    val startingWeightKg: Double?,
)

/** Plan prescription shown on a session's exercise card when the session came from a plan. */
data class PlanTarget(
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
)
