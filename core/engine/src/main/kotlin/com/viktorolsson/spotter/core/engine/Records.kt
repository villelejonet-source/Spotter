package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.epley

/** Best results so far for one exercise. */
data class ExerciseBests(
    val estimatedOneRepMax: Double?,
    /** Most reps achieved at each weight (kg; 0 for bodyweight). */
    val repsAtWeight: Map<Double, Int>,
    val sessionVolumeKg: Double?,
) {
    val isEmpty: Boolean get() = repsAtWeight.isEmpty()

    /** These bests plus [sets] (e.g. sets already done today); session volume is unchanged. */
    fun withSets(sets: List<LoggedSet>): ExerciseBests {
        if (sets.isEmpty()) return this
        val extra = PersonalRecords.bests(listOf(sets))
        return ExerciseBests(
            estimatedOneRepMax = listOfNotNull(estimatedOneRepMax, extra.estimatedOneRepMax).maxOrNull(),
            repsAtWeight = (repsAtWeight.keys + extra.repsAtWeight.keys).associateWith {
                maxOf(repsAtWeight[it] ?: 0, extra.repsAtWeight[it] ?: 0)
            },
            sessionVolumeKg = sessionVolumeKg,
        )
    }

    /** Most reps ever done at [weightKg] or heavier. */
    fun bestRepsAtOrAbove(weightKg: Double): Int? = repsAtWeight.filterKeys { it >= weightKg - EPS }.values.maxOrNull()

    companion object {
        private const val EPS = 1e-6
        val NONE = ExerciseBests(null, emptyMap(), null)
    }
}

data class DetectedRecord(val type: PersonalRecordType, val value: Double, val weightKg: Double?, val reps: Int?)

/**
 * PR detection, run when a workout is saved. The first session of an exercise only sets
 * the baseline; PRs are celebrated from the second time on. At most one record of each
 * type per exercise per session.
 */
object PersonalRecords {
    private const val EPS = 1e-6

    /** [sessions]: working sets per session. */
    fun bests(sessions: List<List<LoggedSet>>): ExerciseBests {
        val all = sessions.flatten()
        if (all.isEmpty()) return ExerciseBests.NONE
        val reps = all.groupBy { it.weightKg ?: 0.0 }.mapValues { (_, sets) -> sets.maxOf { it.reps } }
        val oneRepMax = all.mapNotNull { set -> set.weightKg?.let { epley(it, set.reps) } }.maxOrNull()
        val volume = sessions.map { volume(it) }.filter { it > 0 }.maxOrNull()
        return ExerciseBests(oneRepMax, reps, volume)
    }

    fun detect(session: List<LoggedSet>, previous: ExerciseBests): List<DetectedRecord> {
        if (previous.isEmpty || session.isEmpty()) return emptyList()
        val records = mutableListOf<DetectedRecord>()

        val bestSet = session.filter { it.weightKg != null }.maxByOrNull { epley(it.weightKg!!, it.reps) }
        if (bestSet != null) {
            val e1rm = epley(bestSet.weightKg!!, bestSet.reps)
            if (previous.estimatedOneRepMax == null || e1rm > previous.estimatedOneRepMax + EPS) {
                records += DetectedRecord(PersonalRecordType.ESTIMATED_1RM, e1rm, bestSet.weightKg, bestSet.reps)
            }
        }

        // More reps than ever at this weight or heavier; report the heaviest such set.
        session
            .filter { set -> set.reps > (previous.bestRepsAtOrAbove(set.weightKg ?: 0.0) ?: Int.MAX_VALUE) }
            .maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps }))
            ?.let { records += DetectedRecord(PersonalRecordType.REPS_AT_WEIGHT, it.reps.toDouble(), it.weightKg, it.reps) }

        val sessionVolume = volume(session)
        if (sessionVolume > 0 && sessionVolume > (previous.sessionVolumeKg ?: 0.0) + EPS) {
            records += DetectedRecord(PersonalRecordType.VOLUME, sessionVolume, null, null)
        }
        return records
    }

    private fun volume(sets: List<LoggedSet>) = sets.sumOf { (it.weightKg ?: 0.0) * it.reps }
}
