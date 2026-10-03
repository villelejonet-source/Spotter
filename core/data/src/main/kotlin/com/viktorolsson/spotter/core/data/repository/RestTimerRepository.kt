package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.viktorolsson.spotter.core.model.RestTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The running rest timer, stored as an end timestamp (not a ticking counter) so it
 * survives process death and the screen being off. Null means no rest is running.
 */
@Singleton
class RestTimerRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    val restTimer: Flow<RestTimer?> = dataStore.data.map { prefs ->
        val endsAt = prefs[END_EPOCH_MS] ?: return@map null
        RestTimer(Instant.ofEpochMilli(endsAt), prefs[TOTAL_SECONDS] ?: 0)
    }.distinctUntilChanged()

    suspend fun start(seconds: Int) {
        if (seconds <= 0) return stop()
        dataStore.edit {
            it[END_EPOCH_MS] = clock.millis() + seconds * 1000L
            it[TOTAL_SECONDS] = seconds
        }
    }

    /** Adds (or with a negative value, removes) time; dropping to zero ends the rest. */
    suspend fun adjust(seconds: Int) {
        dataStore.edit {
            val endsAt = it[END_EPOCH_MS] ?: return@edit
            val newEnd = endsAt + seconds * 1000L
            if (newEnd <= clock.millis()) {
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

    private companion object {
        val END_EPOCH_MS = longPreferencesKey("rest_end_epoch_ms")
        val TOTAL_SECONDS = intPreferencesKey("rest_total_seconds")
    }
}
