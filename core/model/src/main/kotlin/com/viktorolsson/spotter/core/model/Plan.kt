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

/** Why next session's targets are what they are; shown on the exercise card. */
enum class ProgressionReason {
    /** First time: the plan's estimated starting weight. */
    CALIBRATION,
    /** First time with no estimate (isolation/bodyweight): pick a weight. */
    FIRST_TIME,
    /** Every set reached the top of the range (or the linear target): weight goes up. */
    INCREASE_WEIGHT,
    /** Logged RIR shows it was much easier than planned: weight goes up early. */
    INCREASE_TOO_EASY,
    /** Same weight, one more rep per set. */
    ADD_REPS,
    /** Target earned, but logged RIR shows it was much harder than planned: hold. */
    HOLD_TOO_HARD,
    /** Missed last time: same weight again. */
    REPEAT,
    /** Missed twice in a row: back off 10 % and build up again. */
    RESET_AFTER_MISSES,
    /** Bodyweight exercise at the top of the range: add load or a harder variation. */
    BODYWEIGHT_TOP_OF_RANGE,
    /** Deload week: fewer sets, lighter weight; doesn't count towards progression. */
    DELOAD,
}
