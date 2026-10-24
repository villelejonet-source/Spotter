package com.viktorolsson.spotter.core.ui

import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.PlanTarget
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSet
import java.time.Instant

/** Sample data for previews and screenshot tests. Dates are fixed so goldens are stable. */
object PreviewData {
    /** 2026-10-03 10:00 UTC. */
    val startedAt: Instant = Instant.parse("2026-10-03T10:00:00Z")

    fun exercise(
        id: String = "barbell-bench-press",
        name: String = "Barbell Bench Press",
        pattern: MovementPattern = MovementPattern.HORIZONTAL_PUSH,
        equipment: List<Equipment> = listOf(Equipment.BARBELL, Equipment.BENCH, Equipment.SQUAT_RACK),
        mechanics: Mechanics = Mechanics.COMPOUND,
        primary: List<Muscle> = listOf(Muscle.CHEST),
        instructions: String? = null,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscles = primary,
        secondaryMuscles = listOf(Muscle.FRONT_DELTS, Muscle.TRICEPS),
        movementPattern = pattern,
        equipment = equipment,
        mechanics = mechanics,
        difficulty = Difficulty.INTERMEDIATE,
        unilateral = false,
        instructions = instructions,
        isCustom = false,
    )

    fun set(id: Long, position: Int, kg: Double?, reps: Int?, type: SetType = SetType.WORKING, completed: Boolean = false, rir: Int? = null) =
        WorkoutSet(id, position, kg, reps, rir, type, if (completed) startedAt.plusSeconds(600L * position) else null, null)

    /** Bench press: two warm-ups done, one working set done, two to go. */
    fun bench(id: Long = 1, completedWorking: Int = 1, reason: ProgressionReason? = ProgressionReason.ADD_REPS) = SessionExercise(
        id = id,
        exercise = exercise(),
        position = 0,
        supersetGroup = null,
        restSeconds = 135,
        notes = null,
        sets = listOf(
            set(id * 10 + 1, 0, 20.0, 10, SetType.WARMUP, completed = true),
            set(id * 10 + 2, 1, 55.0, 3, SetType.WARMUP, completed = true),
            set(id * 10 + 3, 2, 82.5, 10, completed = completedWorking >= 1, rir = 2),
            set(id * 10 + 4, 3, 80.0, 10, completed = completedWorking >= 2, rir = 1),
            set(id * 10 + 5, 4, 80.0, if (completedWorking >= 3) 9 else null, completed = completedWorking >= 3),
        ),
        target = PlanTarget(3, 6, 12, 1),
        progressionReason = reason,
    )

    fun rows(id: Long = 2, supersetGroup: Int? = null) = SessionExercise(
        id = id,
        exercise = exercise("barbell-row", "Barbell Row", MovementPattern.HORIZONTAL_PULL, listOf(Equipment.BARBELL), primary = listOf(Muscle.LATS)),
        position = 1,
        supersetGroup = supersetGroup,
        restSeconds = 90,
        notes = "Keep the elbows tucked.",
        sets = listOf(set(id * 10 + 1, 0, 60.0, 8, completed = true), set(id * 10 + 2, 1, 60.0, 8), set(id * 10 + 3, 2, 60.0, null)),
        target = PlanTarget(3, 8, 12, 2),
        progressionReason = ProgressionReason.INCREASE_WEIGHT,
    )

    fun plank(id: Long = 3) = SessionExercise(
        id = id,
        exercise = exercise("plank", "Plank", MovementPattern.CORE_ANTI_EXTENSION, emptyList(), Mechanics.ISOLATION, listOf(Muscle.ABS)),
        position = 2,
        supersetGroup = null,
        restSeconds = 60,
        notes = null,
        sets = listOf(set(id * 10 + 1, 0, null, 45, completed = true), set(id * 10 + 2, 1, null, 45)),
        target = PlanTarget(2, 30, 60, null),
        progressionReason = ProgressionReason.ADD_REPS,
    )

    fun session(
        id: Long = 1,
        finished: Boolean = true,
        exercises: List<SessionExercise> = listOf(bench(completedWorking = 3), rows(), plank()),
        dayName: String? = "Upper A",
        notes: String? = null,
    ) = WorkoutSession(
        id = id,
        startedAt = startedAt,
        endedAt = if (finished) startedAt.plusSeconds(54 * 60) else null,
        notes = notes,
        exercises = exercises,
        planDayName = dayName,
    )

    fun record(id: Long, type: PersonalRecordType, value: Double, kg: Double? = 82.5, reps: Int? = 10, exerciseName: String = "Barbell Bench Press") =
        PersonalRecord(id, "barbell-bench-press", exerciseName, type, value, kg, reps, startedAt, 1)

    val records = listOf(
        record(1, PersonalRecordType.ESTIMATED_1RM, 110.0),
        record(2, PersonalRecordType.REPS_AT_WEIGHT, 10.0, 80.0, 10),
        record(3, PersonalRecordType.VOLUME, 2080.0, null, null, "Barbell Row"),
    )

    fun planExercise(name: String, position: Int, sets: Int = 3, min: Int = 8, max: Int = 12, pattern: MovementPattern = MovementPattern.SQUAT, superset: Int? = null) =
        PlanExercise(
            id = position.toLong() + 1, exercise = exercise(id = name.lowercase().replace(' ', '-'), name = name, pattern = pattern),
            position = position, sets = sets, repMin = min, repMax = max, targetRir = 1, restSeconds = 120,
            progressionRule = ProgressionRule.DOUBLE_PROGRESSION, supersetGroup = superset, startingWeightKg = null,
        )

    val upperA = PlanDay(
        1, 0, "Upper A",
        listOf(
            planExercise("Barbell Bench Press", 0, 3, 6, 12, MovementPattern.HORIZONTAL_PUSH),
            planExercise("Barbell Row", 1, 3, 8, 12, MovementPattern.HORIZONTAL_PULL),
            planExercise("Overhead Press", 2, 3, 8, 12, MovementPattern.VERTICAL_PUSH),
            planExercise("Lat Pulldown", 3, 3, 10, 15, MovementPattern.VERTICAL_PULL, superset = 1),
            planExercise("Lateral Raise", 4, 3, 12, 20, MovementPattern.LATERAL_RAISE, superset = 1),
        ),
    )

    val lowerA = PlanDay(
        2, 1, "Lower A",
        listOf(
            planExercise("Barbell Back Squat", 0, 3, 6, 12), planExercise("Romanian Deadlift", 1, 3, 8, 14, pattern = MovementPattern.HINGE),
            planExercise("Bulgarian Split Squat", 2, 3, 8, 14, pattern = MovementPattern.LUNGE),
            planExercise("Leg Extension", 3, 3, 10, 15, pattern = MovementPattern.KNEE_EXTENSION),
            planExercise("Barbell Hip Thrust", 4, 3, 10, 15, pattern = MovementPattern.HIP_THRUST),
            planExercise("Lying Leg Curl", 5, 3, 10, 15, pattern = MovementPattern.KNEE_FLEXION),
        ),
    )

    val plan = Plan(1, "Upper / Lower · 4 days", SplitType.UPPER_LOWER, Goal.HYPERTROPHY, startedAt, listOf(upperA, lowerA))
}
