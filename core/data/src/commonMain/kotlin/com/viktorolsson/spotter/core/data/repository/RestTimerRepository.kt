package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.viktorolsson.spotter.core.model.RestTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The running rest timer, stored as an end timestamp (not a ticking counter) so it
 * survives process death and the screen being off. Null means no rest is running.
 */
class RestTimerRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    val restTimer: Flow<RestTimer?> = dataStore.data.map { prefs ->
        val endsAt = prefs[END_EPOCH_MS] ?: return@map null
        RestTimer(Instant.fromEpochMilliseconds(endsAt), prefs[TOTAL_SECONDS] ?: 0)
    }.distinctUntilChanged()

    suspend fun start(seconds: Int) {
        if (seconds <= 0) return stop()
        dataStore.edit {
            it[END_EPOCH_MS] = clock.now().toEpochMilliseconds() + seconds * 1000L
            it[TOTAL_SECONDS] = seconds
        }
    }

    /** Adds (or with a negative value, removes) time; dropping to zero ends the rest. */
    suspend fun adjust(seconds: Int) {
        dataStore.edit {
            val endsAt = it[END_EPOCH_MS] ?: return@edit
            val newEnd = endsAt + seconds * 1000L
            if (newEnd <= clock.now().toEpochMilliseconds()) {
                it.remove(END_EPOCH_MS)
                it.remove(TOTAL_SECONDS)
            } else {
                it[END_EPOCH_MS] = newEnd
                it[TOTAL_SECONDS] = ((it[TOTAL_SECONDS] ?: 0) + seconds).coerceAtLeast(1)
            }
        }
    }

    suspend fun stop() {
        dataStore.edit {
            it.remove(END_EPOCH_MS)
            it.remove(TOTAL_SECONDS)
        }
    }

    /**
     * Waits out [rest] and ends it, the same way on both platforms; only the alerting differs
     * (Android's workout service, the app on iOS). [onResting] gets the time left when the wait
     * starts, [onEnded] runs when the rest runs out (the alert), then the rest is cleared. A
     * rest that ran out while nothing was waiting (the app wasn't running) is cleared without
     * [onEnded]. Clearing re-emits [restTimer], which cancels a `collectLatest` caller, so
     * anything that must happen after the alert belongs in [onEnded].
     */
    suspend fun runOut(
        rest: RestTimer,
        onResting: suspend (remaining: Duration) -> Unit = {},
        onEnded: suspend () -> Unit = {},
    ) {
        val remaining = rest.endsAt - clock.now()
        if (remaining.isPositive()) {
            onResting(remaining)
            delay(remaining)
            onEnded()
        }
        stop()
    }

    private companion object {
        val END_EPOCH_MS = longPreferencesKey("rest_end_epoch_ms")
        val TOTAL_SECONDS = intPreferencesKey("rest_total_seconds")
    }
}
