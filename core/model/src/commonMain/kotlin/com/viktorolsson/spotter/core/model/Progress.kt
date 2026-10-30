package com.viktorolsson.spotter.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Display groups for weekly volume; finer than [BodyArea], coarser than [Muscle]. */
enum class MuscleGroup(val muscles: Set<Muscle>) {
    CHEST(setOf(Muscle.CHEST)),
    BACK(setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.TRAPS, Muscle.LOWER_BACK)),
    SHOULDERS(setOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS)),
    BICEPS(setOf(Muscle.BICEPS, Muscle.FOREARMS)),
    TRICEPS(setOf(Muscle.TRICEPS)),
    QUADS(setOf(Muscle.QUADS)),
    HAMSTRINGS(setOf(Muscle.HAMSTRINGS)),
    GLUTES(setOf(Muscle.GLUTES, Muscle.ABDUCTORS, Muscle.ADDUCTORS)),
    CALVES(setOf(Muscle.CALVES)),
    CORE(setOf(Muscle.ABS, Muscle.OBLIQUES)),
    ;

    companion object {
        fun of(muscle: Muscle): MuscleGroup = entries.first { muscle in it.muscles }
    }
}

enum class VolumeStatus { UNDER, IN_RANGE, OVER }

data class MuscleVolume(
    val group: MuscleGroup,
    /** Primary muscles count a full set, secondary muscles half a set. */
    val sets: Double,
    val targetLow: Int,
    val targetHigh: Int,
) {
    val status: VolumeStatus
        get() = when {
            sets < targetLow -> VolumeStatus.UNDER
            sets > targetHigh -> VolumeStatus.OVER
            else -> VolumeStatus.IN_RANGE
        }
}

data class PersonalRecord(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,
    val type: PersonalRecordType,
    /** kg for 1RM/volume, reps for rep records. */
    val value: Double,
    val weightKg: Double?,
    val reps: Int?,
    val achievedAt: Instant,
    val sessionId: Long?,
)

/** One session's sets of a single exercise, for exercise history. Rules: Lifting.kt. */
data class ExerciseSessionLog(
    val sessionId: Long,
    val date: Instant,
    val sets: List<WorkoutSet>,
    /** A hold: reps are seconds, so there's no volume or 1RM. */
    val timed: Boolean = false,
) {
    private val working get() = sets.filter { it.setType != SetType.WARMUP && it.reps != null }
    val bestSet: WorkoutSet? get() = working.maxWithOrNull(bestSetOrder({ it.weightKg }, { it.reps }))
    val estimatedOneRepMax: Double?
        get() = if (timed) null else working.mapNotNull { set ->
            val kg = set.weightKg
            val reps = set.reps
            if (kg != null && reps != null) epley(kg, reps) else null
        }.maxOrNull()
    val volumeKg: Double get() = if (timed) 0.0 else working.sumOf { setVolumeKg(it.weightKg, it.reps) }
}

data class BodyWeightPoint(val date: LocalDate, val weightKg: Double, val sevenDayAverageKg: Double)
