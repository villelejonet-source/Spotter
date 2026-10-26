package com.viktorolsson.spotter.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The work the app does once after launch, kept cheap for an installed, up-to-date app:
 * seeding returns after one preference read, the one-off plan conversion runs once ever,
 * and time-based suggestions are re-evaluated at most once a day (finishing a workout
 * refreshes them anyway).
 */
@Singleton
class StartupMaintenance @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val seeder: ExerciseSeeder,
    private val plans: PlanRepository,
    private val recommendations: RecommendationRepository,
    private val clock: Clock,
) {
    suspend fun run() {
        seeder.seedIfNeeded()
        val marks = dataStore.data.first()
        if (marks[HOLDS_CONVERTED] != true) {
            plans.convertHoldsToSeconds()
            dataStore.edit { it[HOLDS_CONVERTED] = true }
        }
        val now = clock.now().toEpochMilliseconds()
        if (now - (marks[RECOMMENDATIONS_REFRESHED_AT] ?: 0L) >= REFRESH_INTERVAL.inWholeMilliseconds) {
            recommendations.refresh()
            dataStore.edit { it[RECOMMENDATIONS_REFRESHED_AT] = now }
        }
    }

    private companion object {
        val HOLDS_CONVERTED = booleanPreferencesKey("maintenance_holds_converted")
        val RECOMMENDATIONS_REFRESHED_AT = longPreferencesKey("maintenance_recommendations_refreshed_at")
        val REFRESH_INTERVAL: Duration = 1.days
    }
}
