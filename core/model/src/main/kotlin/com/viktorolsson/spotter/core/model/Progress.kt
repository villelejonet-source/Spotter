package com.viktorolsson.spotter.core.model

import java.time.Instant
import java.time.LocalDate

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

/** One session's sets of a single exercise, for exercise history. */
data class ExerciseSessionLog(
    val sessionId: Long,
    val date: Instant,
    val sets: List<WorkoutSet>,
) {
    private val working get() = sets.filter { it.setType != SetType.WARMUP && it.reps != null }
    val bestSet: WorkoutSet? get() = working.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))
    val estimatedOneRepMax: Double? get() = working.mapNotNull { set -> set.weightKg?.let { epley(it, set.reps!!) } }.maxOrNull()
    val volumeKg: Double get() = working.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
}

/** Epley estimated one-rep max; a single is its own 1RM. */
fun epley(weightKg: Double, reps: Int): Double = if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

data class BodyWeightPoint(val date: LocalDate, val weightKg: Double, val sevenDayAverageKg: Double)
