package com.viktorolsson.spotter.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.russhwolf.settings.Settings
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import com.viktorolsson.spotter.core.data.repository.ScheduleRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.seed.CatalogSource
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import com.viktorolsson.spotter.core.data.sync.AccountService
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.DeviceIdentity
import com.viktorolsson.spotter.core.data.sync.LocalSyncStore
import com.viktorolsson.spotter.core.data.sync.SessionStore
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SupabaseProvider
import com.viktorolsson.spotter.core.data.sync.SupabaseSyncRemote
import com.viktorolsson.spotter.core.data.sync.SyncCursorStore
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The data layer's objects, each made once on first use. The platform supplies storage,
 * secrets and scheduling (`androidDataGraph`, `iosDataGraph`), and `dataModule` hands these
 * properties to Koin on both. One graph per process.
 */
class DataGraph(
    val database: SpotterDatabase,
    val dataStore: DataStore<Preferences>,
    private val supabaseConfig: SupabaseConfig,
    private val sessionStore: SessionStore,
    private val deviceStore: () -> Settings,
    private val appBuild: AppBuild,
    private val catalog: CatalogSource,
    private val syncScheduler: SyncScheduler,
    val clock: Clock = Clock.System,
    /** Work that must outlive any screen, such as seeding, auto-save writes and sync. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val userProfiles by lazy { UserProfileRepository(database.userProfileDao()) }
    val preferences by lazy { UserPreferencesRepository(dataStore, database.userProfileDao()) }
    val exercises by lazy { ExerciseRepository(database.exerciseDao()) }
    val history by lazy { HistoryRepository(database.workoutDao(), clock) }
    val plans by lazy { PlanRepository(database, database.planDao()) }
    val bodyWeight by lazy { BodyWeightRepository(database, database.userProfileDao()) }
    val restTimer by lazy { RestTimerRepository(dataStore, clock) }
    val workouts by lazy {
        WorkoutRepository(database, database.workoutDao(), database.planDao(), database.exerciseDao(), preferences, clock)
    }
    val schedule by lazy { ScheduleRepository(database.scheduleDao(), workouts, clock) }
    val recommendations by lazy {
        RecommendationRepository(
            database, database.recommendationDao(), database.workoutDao(), database.planDao(), database.exerciseDao(),
            userProfiles, history, preferences, clock,
        )
    }
    val seeder by lazy { ExerciseSeeder(catalog, database.exerciseDao(), dataStore) }
    val startupMaintenance by lazy { StartupMaintenance(dataStore, seeder, plans, recommendations, clock) }

    val supabase by lazy { SupabaseProvider(supabaseConfig, sessionStore, scope) }
    val accounts by lazy { AccountService(supabase, scope) }
    val deviceIdentity by lazy { DeviceIdentity(deviceStore, appBuild, database) }
    val sync by lazy {
        SyncRepository(
            accounts, SupabaseSyncRemote(supabase), LocalSyncStore(database), SyncCursorStore(dataStore),
            preferences, userProfiles, syncScheduler, scope, clock, deviceIdentity,
        )
    }

    /**
     * What both apps do once at launch, after Koin has started. Sync is made eagerly so it
     * starts as soon as a signed-in session is restored (the Supabase client it needs is built
     * lazily, off the main thread); then seeding, one-off conversions and daily refreshes, each
     * of which returns early when there's nothing to do.
     */
    fun onAppStart() {
        sync
        scope.launch { startupMaintenance.run() }
    }
}
