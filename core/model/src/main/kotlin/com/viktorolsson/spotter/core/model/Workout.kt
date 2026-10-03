package com.viktorolsson.spotter.core.model

import java.time.Duration
import java.time.Instant

enum class SetType { WARMUP, WORKING, DROP, FAILURE }

enum class PersonalRecordType { ESTIMATED_1RM, REPS_AT_WEIGHT, VOLUME }

enum class RecommendationType { ADD_ACCESSORY, ADD_VOLUME, DELOAD, CHANGE_REP_RANGE, INCREASE_FREQUENCY, VARIATION }

enum class RecommendationStatus { ACTIVE, DISMISSED, APPLIED }

data class WorkoutSession(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val notes: String?,
    val exercises: List<SessionExercise>,
    /** Set when the session was started from a plan day. */
    val planDayName: String? = null,
) {
    val isActive: Boolean get() = endedAt == null
}

data class SessionExercise(
    val id: Long,
    val exercise: Exercise,
    val position: Int,
    val supersetGroup: Int?,
    val restSeconds: Int?,
    val notes: String?,
    val sets: List<WorkoutSet>,
    /** Set when the exercise came from a plan day. */
    val target: PlanTarget? = null,
    /** Name of the exercise this one replaced in the session, if swapped. */
    val substitutedFromName: String? = null,
    val progressionReason: ProgressionReason? = null,
)

/** Weight is always kg; null weight means bodyweight. */
data class WorkoutSet(
    val id: Long,
    val position: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rir: Int?,
    val setType: SetType,
    val completedAt: Instant?,
    val notes: String?,
) {
    val isCompleted: Boolean get() = completedAt != null
}

/** A completed set from an earlier session, shown greyed out in the "previous" column. */
data class PreviousSet(val weightKg: Double?, val reps: Int?)

data class RestTimer(
    val endsAt: Instant,
    val totalSeconds: Int,
) {
    fun remainingSeconds(now: Instant): Long =
        ((Duration.between(now, endsAt).toMillis() + 999) / 1000).coerceAtLeast(0)
}

data class ExerciseSummary(
    val exerciseName: String,
    val completedSets: Int,
    /** Heaviest completed working set, then most reps at that weight. */
    val bestSet: PreviousSet?,
)

data class WorkoutSummary(
    val duration: Duration,
    val completedSets: Int,
    /** Sum of weight × reps over completed non-warm-up sets, in kg. */
    val volumeKg: Double,
    val exercises: List<ExerciseSummary>,
) {
    companion object {
        fun of(session: WorkoutSession, now: Instant = Instant.now()): WorkoutSummary {
            val counted = session.exercises.flatMap { ex -> ex.sets.filter { it.isCompleted } }
            return WorkoutSummary(
                duration = Duration.between(session.startedAt, session.endedAt ?: now),
                completedSets = counted.size,
                volumeKg = counted
                    .filter { it.setType != SetType.WARMUP }
                    .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                exercises = session.exercises.map { ex ->
                    val done = ex.sets.filter { it.isCompleted }
                    ExerciseSummary(
                        exerciseName = ex.exercise.name,
                        completedSets = done.size,
                        bestSet = done.filter { it.setType != SetType.WARMUP }
                            .maxWithOrNull(compareBy<WorkoutSet>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))
                            ?.let { PreviousSet(it.weightKg, it.reps) },
                    )
                },
            )
        }
    }
}
