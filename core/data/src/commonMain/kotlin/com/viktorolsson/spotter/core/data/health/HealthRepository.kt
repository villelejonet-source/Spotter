package com.viktorolsson.spotter.core.data.health

import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.entity.FinishedSessionRow
import com.viktorolsson.spotter.core.data.db.entity.HealthExportEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.db.withTransaction
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.engine.ReadinessRules
import com.viktorolsson.spotter.core.engine.WorkoutEnergy
import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import com.viktorolsson.spotter.core.model.Readiness
import com.viktorolsson.spotter.core.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the settings screen shows about the phone's health store. */
data class HealthState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    /** The switch is on and Spotter still has access, so finished workouts are being saved. */
    val savingWorkouts: Boolean = false,
    /** The switch is on and Spotter may read: sleep and recovery go into the suggestion before a workout. */
    val readingRecovery: Boolean = false,
    /**
     * Access was asked for and not given, or was taken back while a switch was on. The
     * person has to give it in the health store itself: Android stops showing its permission
     * screen after two refusals, and then the switch alone does nothing.
     */
    val needsAccess: Boolean = false,
)

/**
 * Saves finished workouts to the phone's health store ([HealthStore]) once the person turns
 * that on, and keeps them in step from then on without being asked: [run] wakes on every
 * change to the finished workouts and [reconcile] writes what's new or changed and removes
 * what was deleted, whether that happened in the app, on another phone or on the website.
 * `health_export` records what has been saved, so a pass that was cut short or failed is
 * simply made again. A later change to the profile doesn't recalculate workouts already saved.
 */
class HealthRepository(
    private val store: HealthStore,
    private val db: SpotterDatabase,
    private val profiles: UserProfileRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val errors: AppErrors,
    /**
     * A backup is being restored over this phone's data: its workouts are gone for a moment
     * and come back as the same ones, so nothing is removed from the health store meanwhile.
     */
    private val restoring: suspend () -> Boolean = { false },
    /** This phone's id (`DeviceIdentity`), which a transfer to a new phone doesn't carry over. */
    private val device: suspend () -> String,
    /** How long after a failed pass the next one is made; doubles up to [LONGEST_RETRY] while it keeps failing. */
    private val retryAfter: Duration = 30.seconds,
) {
    private val dao = db.healthExportDao()
    private val workoutDao = db.workoutDao()

    private data class Access(val availability: HealthAvailability, val canWrite: Boolean, val canRead: Boolean)

    private val access = MutableStateFlow(Access(HealthAvailability.UNSUPPORTED, canWrite = false, canRead = false))

    /** The person was asked for access and didn't give it; forgotten when they turn saving on or off. */
    private val refused = MutableStateFlow(false)

    val state: Flow<HealthState> = combine(access, preferences.preferences, refused) { access, prefs, refused ->
        val saving = prefs.healthExportSince != null
        val reading = prefs.healthReadiness
        HealthState(
            access.availability,
            savingWorkouts = saving && access.canWrite,
            readingRecovery = reading && access.canRead,
            needsAccess = access.availability == HealthAvailability.AVAILABLE &&
                (refused || (saving && !access.canWrite) || (reading && !access.canRead)),
        )
    }.distinctUntilChanged()

    /**
     * Asks the store again: access is given and taken back outside the app. [state] says
     * "unsupported" until this has run once, so a screen that shows it calls this when shown.
     */
    suspend fun refresh() {
        access.value = currentAccess()
    }

    private suspend fun currentAccess(): Access {
        val availability = store.availability()
        val available = availability == HealthAvailability.AVAILABLE
        return Access(availability, canWrite = available && store.canWriteWorkouts(), canRead = available && store.canReadRecovery())
    }

    /**
     * Turns saving on (the person has just given access) or off. Workouts ended from now on are
     * saved, not older ones; turning it off leaves what's already in the health store alone.
     */
    suspend fun setSavingWorkouts(on: Boolean) {
        refused.value = false
        onThisPhone(preferences.preferences.first())
        preferences.setHealthExportSince(if (on) clock.now() else null, device())
        refresh()
    }

    /** Turns reading sleep and recovery on (the person has just given access) or off. Nothing read is kept. */
    suspend fun setReadingRecovery(on: Boolean) {
        refused.value = false
        onThisPhone(preferences.preferences.first())
        preferences.setHealthReadiness(on, device())
        refresh()
    }

    /**
     * This morning's readiness from the last four weeks in the health store, read now, after
     * asking the store about access again ([refresh]): null
     * when reading is off, nothing was recorded for last night, or the store can't be read.
     */
    suspend fun readiness(today: LocalDate): Readiness? {
        var readiness: Readiness? = null
        // Asked each time a screen is shown, unprompted: a store that fails is noted, not put in front of the person.
        attempt {
            refresh()
            val prefs = preferences.preferences.first()
            if (!prefs.healthReadiness || !onThisPhone(prefs) || !access.value.canRead) return@attempt
            val nights = store.readRecovery(today.minus(DatePeriod(days = ReadinessRules.BASELINE_DAYS)), today)
            readiness = ReadinessRules.assess(nights, today)
        }
        return readiness
    }

    /**
     * False when the switches came with a move to a new phone, whose health store has none of
     * what this one recorded: that phone starts with both off and nothing saved, like any new one.
     */
    private suspend fun onThisPhone(prefs: UserPreferences): Boolean {
        if (prefs.healthExportSince == null && !prefs.healthReadiness) return true
        val device = device()
        if (prefs.healthDevice == device) return true
        dao.clear()
        preferences.setHealthExportSince(null, device)
        preferences.setHealthReadiness(false, device)
        refresh()
        return false
    }

    /** The person was shown the permission screen (or Android wouldn't show it again) and access wasn't given. */
    suspend fun accessRefused() {
        refused.value = true
        refresh()
    }

    /**
     * Runs for as long as the app does (`DataGraph.onAppStart`). A change to the finished
     * workouts, or access coming back, only wakes it: each pass reads what's there itself, so
     * changes that pile up during a pass cost one more pass, never one on an outdated list.
     * A pass that left something undone is made again by itself, without waiting for a change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run(): Unit = coroutineScope {
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch {
            merge(
                preferences.preferences.map { it.healthExportSince }.distinctUntilChanged()
                    .flatMapLatest { since -> if (since == null) emptyFlow() else dao.observeFinishedSessions(since).distinctUntilChanged() },
                access.map { it.canWrite }.distinctUntilChanged().filter { it },
            ).collect { wake.send(Unit) }
        }
        var retryIn: Duration? = null
        while (true) {
            retryIn.let { if (it == null) wake.receive() else withTimeoutOrNull(it) { wake.receive() } }
            var done = false
            attempt { done = reconcile() }
            retryIn = if (done) null else minOf((retryIn ?: (retryAfter / 2)) * 2, LONGEST_RETRY)
        }
    }

    /** A changed workout as it should be written, or null when it shouldn't be in the health store. */
    private class Change(val row: FinishedSessionRow, val workout: HealthWorkout?)

    /**
     * Brings the health store in step with the finished workouts; true when nothing is left to
     * do. Each workout stands alone: one the store refuses is reported and left for the next
     * pass, without holding up the others.
     */
    internal suspend fun reconcile(): Boolean {
        val prefs = preferences.preferences.first()
        val since = prefs.healthExportSince ?: return true
        if (!onThisPhone(prefs) || !currentAccess().canWrite) return true
        // During a restore a workout can be missing, or back without its sets yet: neither means
        // it's gone. Asked before and after reading, so the read can't have been of a half-restored phone.
        val restoringBefore = restoring()
        var saved = emptyMap<String, HealthExportEntity>()
        var finished = emptySet<String>()
        // Read in one go, so the workouts loaded are the ones just listed.
        val changes = db.withTransaction {
            saved = dao.getAll().associateBy { it.sessionSyncId }
            val rows = dao.finishedSessions(since)
            finished = rows.map { it.syncId }.toSet()
            rows.filter { saved[it.syncId]?.state != it.exportState }.map { row -> Change(row, workout(row, saved[row.syncId])) }
        }
        val mayRemove = !restoringBefore && !restoring()

        var done = true
        suspend fun step(block: suspend () -> Unit) {
            if (!attempt(block)) done = false
        }
        changes.forEach { change ->
            val id = change.row.syncId
            when {
                change.workout != null -> step {
                    store.writeWorkout(change.workout)
                    dao.upsert(HealthExportEntity(id, change.row.exportState, change.workout.version))
                }
                id !in saved -> Unit
                // Saved earlier, and no longer a workout to save: take it back out.
                mayRemove -> step { remove(id) }
                else -> done = false
            }
        }
        (saved.keys - finished).forEach { id -> if (mayRemove) step { remove(id) } else done = false }
        return done
    }

    private suspend fun workout(row: FinishedSessionRow, saved: HealthExportEntity?): HealthWorkout? {
        val session = workoutDao.getSessionWithExercises(row.id)?.toModel() ?: return null
        val end = WorkoutEnergy.trainedUntil(session)?.takeIf { it > session.startedAt }
        // A workout finished with nothing ticked isn't training worth telling other apps about;
        // one whose sets a sync hasn't delivered yet comes back when they arrive.
        if (end == null || session.exercises.none { ex -> ex.sets.any { it.isCompleted } }) return null
        val day = session.startedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return HealthWorkout(
            id = row.syncId,
            // The time of writing, so later writes win whatever changed; above the last one even when the phone's clock went back.
            version = maxOf(clock.now().toEpochMilliseconds(), (saved?.version ?: 0) + 1),
            title = session.planDayName,
            start = session.startedAt,
            end = end,
            activeKcal = profiles.on(day)?.let { WorkoutEnergy.activeKcal(session, it, day) },
        )
    }

    private suspend fun remove(id: String) {
        store.deleteWorkout(id)
        dao.delete(id)
    }

    /** False when [block] failed: nothing for the person to do, and the next pass tries again. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.noticed(e, "HealthRepository")
            false
        }

    /**
     * What a saved workout is compared by. Raise [EXPORT_FORMAT] when a release writes more
     * about a workout (segments, a better calorie figure), and every saved one is written again.
     */
    private val FinishedSessionRow.exportState: Long get() = state + EXPORT_FORMAT

    private companion object {
        const val EXPORT_FORMAT = 0L
        val LONGEST_RETRY = 15.minutes
    }
}
