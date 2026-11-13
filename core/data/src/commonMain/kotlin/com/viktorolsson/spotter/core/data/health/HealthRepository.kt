package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.db.dao.HealthExportDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.FinishedSessionRow
import com.viktorolsson.spotter.core.data.db.entity.HealthExportEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.engine.WorkoutEnergy
import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** What the settings screen shows about the phone's health store. */
data class HealthState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    /** The switch is on and Spotter still has access, so finished workouts are being saved. */
    val savingWorkouts: Boolean = false,
)

/**
 * Saves finished workouts to the phone's health store ([HealthStore]) once the person turns
 * that on, and keeps them in step from then on without being asked: [run] watches the finished
 * workouts and [reconcile] writes what's new or changed and removes what was deleted, whether
 * that happened in the app, on another phone or on the website. `health_export` records what
 * has been saved, so a failed write is simply tried again on the next change or app start.
 * A later change to the profile (a new body weight) doesn't recalculate workouts already saved.
 */
class HealthRepository(
    private val store: HealthStore,
    private val dao: HealthExportDao,
    private val workoutDao: WorkoutDao,
    private val profiles: UserProfileRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val errors: AppErrors,
) {
    private data class Access(val availability: HealthAvailability, val canWrite: Boolean)

    private val access = MutableStateFlow(Access(HealthAvailability.UNSUPPORTED, canWrite = false))

    val state: Flow<HealthState> = combine(access, preferences.preferences) { access, prefs ->
        HealthState(access.availability, savingWorkouts = prefs.healthExportSince != null && access.canWrite)
    }.distinctUntilChanged()

    /** Asks the store again: access is given and taken back outside the app. */
    suspend fun refresh() {
        access.value = currentAccess()
    }

    private suspend fun currentAccess(): Access {
        val availability = store.availability()
        return Access(availability, canWrite = availability == HealthAvailability.AVAILABLE && store.canWriteWorkouts())
    }

    /**
     * Turns saving on (the person has just given access) or off. Workouts ended from now on are
     * saved, not older ones; turning it off leaves what's already in the health store alone.
     */
    suspend fun setSavingWorkouts(on: Boolean) {
        preferences.setHealthExportSince(if (on) clock.now() else null)
        refresh()
    }

    /** Runs for as long as the app does (`DataGraph.onAppStart`). */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        preferences.preferences.map { it.healthExportSince }.distinctUntilChanged()
            .flatMapLatest { since -> if (since == null) emptyFlow() else dao.observeFinishedSessions(since).distinctUntilChanged() }
            .collect { finished ->
                try {
                    reconcile(finished)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing for the person to do; it's tried again on the next change or start.
                    errors.noticed(e, "HealthRepository")
                }
            }
    }

    /** Brings the health store in step with [finished] (what `observeFinishedSessions` gave). */
    internal suspend fun reconcile(finished: List<FinishedSessionRow>) {
        if (!currentAccess().canWrite) return
        val saved = dao.getAll().associate { it.sessionSyncId to it.state }
        val profile = profiles.get()
        finished.filter { saved[it.syncId] != it.state }.forEach { row ->
            val session = workoutDao.getSessionWithExercises(row.id)?.toModel() ?: return@forEach
            val end = WorkoutEnergy.trainedUntil(session)?.takeIf { it > session.startedAt } ?: return@forEach
            // A workout finished with nothing ticked isn't training worth telling other apps
            // about; one whose sets a sync hasn't delivered yet comes back when they arrive.
            if (session.exercises.none { ex -> ex.sets.any { it.isCompleted } }) return@forEach
            store.writeWorkout(
                HealthWorkout(
                    id = row.syncId,
                    // Later writes always win, whatever changed.
                    version = clock.now().toEpochMilliseconds(),
                    title = session.planDayName,
                    start = session.startedAt,
                    end = end,
                    activeKcal = profile?.let { WorkoutEnergy.activeKcal(session, it) },
                ),
            )
            dao.upsert(HealthExportEntity(row.syncId, row.state))
        }
        (saved.keys - finished.map { it.syncId }.toSet()).forEach { id ->
            store.deleteWorkout(id)
            dao.delete(id)
        }
    }
}
