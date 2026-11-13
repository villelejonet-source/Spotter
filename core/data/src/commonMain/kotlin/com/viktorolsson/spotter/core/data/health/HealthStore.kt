package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout

/**
 * The phone's own health store, which other health apps read: Health Connect on Android
 * (`HealthConnectStore`). The platform supplies it through `DataGraph`; a platform without one
 * leaves the default, [NoHealthStore].
 */
interface HealthStore {
    suspend fun availability(): HealthAvailability

    /** The person has allowed Spotter to save workouts; they can take that back at any time. */
    suspend fun canWriteWorkouts(): Boolean

    /** Saves [workout], replacing an older version of the same one. */
    suspend fun writeWorkout(workout: HealthWorkout)

    /** Removes the workout saved under [id]; nothing happens when there is none. */
    suspend fun deleteWorkout(id: String)
}

object NoHealthStore : HealthStore {
    override suspend fun availability() = HealthAvailability.UNSUPPORTED
    override suspend fun canWriteWorkouts() = false
    override suspend fun writeWorkout(workout: HealthWorkout) = Unit
    override suspend fun deleteWorkout(id: String) = Unit
}
