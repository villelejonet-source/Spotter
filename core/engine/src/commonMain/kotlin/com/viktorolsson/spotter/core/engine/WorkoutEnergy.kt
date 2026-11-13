package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How long a finished workout really took and roughly what it burned, for the phone's health
 * store. An estimate from the logged sets and the profile, not a measurement: there's no
 * heart rate behind it.
 */
object WorkoutEnergy {
    /** How hard the session was overall; each has a MET value (multiples of resting energy). */
    enum class Intensity(val met: Double) { LIGHT(3.5), MODERATE(5.0), VIGOROUS(6.0) }

    /**
     * When training ended, or null for a session still in progress. A workout has no pause, so
     * one that was left open is cut off shortly after its last ticked set, and never runs
     * longer than [LONGEST]. This is for the health store only: the app's own history shows the
     * time from start to Finish (`WorkoutSummary`), which the website repeats.
     */
    fun trainedUntil(session: WorkoutSession): Instant? {
        val ended = session.endedAt ?: return null
        val lastSet = session.exercises.flatMap { it.sets }.mapNotNull { it.completedAt }.maxOrNull()
        return listOfNotNull(ended, lastSet?.plus(AFTER_LAST_SET), session.startedAt + LONGEST)
            .min()
            .coerceAtLeast(session.startedAt)
    }

    /**
     * Density (working sets per ten minutes), how close to failure the sets were, and how much
     * of it was compound lifts each push the session up or down a step. A lighter session is
     * always light.
     */
    fun intensity(session: WorkoutSession, trained: Duration): Intensity {
        val working = session.exercises.flatMap { ex -> ex.sets.filter { it.isCompleted && it.setType != SetType.WARMUP }.map { ex to it } }
        if (session.isLighter || working.isEmpty() || trained <= Duration.ZERO) return Intensity.LIGHT
        val perTenMinutes = working.size / (trained.inWholeSeconds / 600.0)
        val rirs = working.mapNotNull { it.second.rir }
        val averageRir = if (rirs.isEmpty()) null else rirs.average()
        val compoundShare = working.count { it.first.exercise.mechanics == Mechanics.COMPOUND }.toDouble() / working.size

        var points = 0
        if (perTenMinutes < SPARSE_SETS) points-- else if (perTenMinutes >= DENSE_SETS) points++
        if (averageRir != null) {
            if (averageRir <= HARD_RIR) points++ else if (averageRir >= EASY_RIR) points--
        }
        if (compoundShare >= MOSTLY_COMPOUND) points++
        return when {
            points < 0 -> Intensity.LIGHT
            points >= 2 -> Intensity.VIGOROUS
            else -> Intensity.MODERATE
        }
    }

    /**
     * Active kilocalories: the energy above resting, (MET − 1) × resting rate × time. The
     * resting rate is Mifflin-St Jeor from sex, weight, height and age. Null when the session
     * isn't finished or the profile has no usable weight or height. It uses the profile as it
     * is now: pricing an old workout (a backfill) needs the body weight of its day passed in.
     */
    fun activeKcal(session: WorkoutSession, profile: UserProfile): Int? {
        val trained = (trainedUntil(session) ?: return null) - session.startedAt
        if (profile.bodyWeightKg <= 0 || profile.heightCm <= 0) return null
        val age = profile.birthDate.yearsUntil(session.startedAt.toLocalDateTime(TimeZone.UTC).date)
        val restingPerDay = 10 * profile.bodyWeightKg + 6.25 * profile.heightCm - 5 * age +
            if (profile.sex == Sex.MALE) 5 else -161
        val minutes = trained.inWholeSeconds / 60.0
        return ((intensity(session, trained).met - 1) * restingPerDay / MINUTES_PER_DAY * minutes).roundToInt().coerceAtLeast(0)
    }

    private val AFTER_LAST_SET = 5.minutes
    private val LONGEST = 4.hours
    private const val SPARSE_SETS = 1.5
    private const val DENSE_SETS = 3.0
    private const val HARD_RIR = 1.5
    private const val EASY_RIR = 4.0
    private const val MOSTLY_COMPOUND = 0.6
    private const val MINUTES_PER_DAY = 1440.0
}
