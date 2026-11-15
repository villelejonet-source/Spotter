package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import com.viktorolsson.spotter.core.model.RecoveryNight
import kotlinx.datetime.LocalDate

/**
 * The phone's own health store, which other health apps read: Health Connect on Android
 * (`HealthConnectStore`). The platform supplies it through `DataGraph`; a platform without one
 * leaves the default, [NoHealthStore].
 *
 * HealthKit never tells an app whether it may read, so [readRecovery] returns what it got or
 * nothing, and [canReadRecovery] only says "as far as the platform lets us know".
 */
interface HealthStore {
    suspend fun availability(): HealthAvailability

    /** The person has allowed Spotter to save workouts; they can take that back at any time. */
    suspend fun canWriteWorkouts(): Boolean

    /**
     * Saves [workout] so that the store holds exactly it: an older version is replaced, and
     * what the new one no longer has (its calories) is removed.
     */
    suspend fun writeWorkout(workout: HealthWorkout)

    /** Removes the workout saved under [id]; nothing happens when there is none. */
    suspend fun deleteWorkout(id: String)

    /** The person lets Spotter read at least one of sleep, heart rate variability and resting heart rate. */
    suspend fun canReadRecovery(): Boolean

    /**
     * What the store has for the nights that ended on [from] through [to], from any source
     * (a watch, a ring, another app). Only the signals the person allowed; nights with nothing
     * are left out.
     */
    suspend fun readRecovery(from: LocalDate, to: LocalDate): List<RecoveryNight>
}

object NoHealthStore : HealthStore {
    override suspend fun availability() = HealthAvailability.UNSUPPORTED
    override suspend fun canWriteWorkouts() = false
    override suspend fun writeWorkout(workout: HealthWorkout) = Unit
    override suspend fun deleteWorkout(id: String) = Unit
    override suspend fun canReadRecovery() = false
    override suspend fun readRecovery(from: LocalDate, to: LocalDate) = emptyList<RecoveryNight>()
}
