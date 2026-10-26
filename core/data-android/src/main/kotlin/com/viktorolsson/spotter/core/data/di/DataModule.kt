package com.viktorolsson.spotter.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.SharedPreferencesSettings
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.StartupMaintenance
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.RecommendationDao
import com.viktorolsson.spotter.core.data.db.dao.ScheduleDao
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
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
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import com.viktorolsson.spotter.core.data.sync.AccountService
import com.viktorolsson.spotter.core.data.sync.AndroidSessionStore
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.DeviceIdentity
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.data.sync.WorkManagerSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock

/** Scope for work that must outlive any screen, such as seeding and auto-save writes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The shared [DataGraph], built from Android's storage, Keystore session store and WorkManager,
 * and each of its objects for injection. Every object is made once, by the graph.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpotterDatabase =
        SpotterDatabase.configure(
            Room.databaseBuilder<SpotterDatabase>(context, context.getDatabasePath(SpotterDatabase.NAME).absolutePath),
            AndroidSQLiteDriver(),
        ).build()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") }

    @Provides
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDataGraph(
        @ApplicationContext context: Context,
        database: SpotterDatabase,
        dataStore: DataStore<Preferences>,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
        supabaseConfig: SupabaseConfig,
        appBuild: AppBuild,
    ): DataGraph = DataGraph(
        database = database,
        dataStore = dataStore,
        clock = clock,
        scope = scope,
        supabaseConfig = supabaseConfig,
        sessionStore = AndroidSessionStore(context),
        deviceStore = { SharedPreferencesSettings(context.getSharedPreferences(DeviceIdentity.FILE, Context.MODE_PRIVATE), commit = true) },
        appBuild = appBuild,
        catalog = { context.assets.open("exercises.json").bufferedReader().use { it.readText() } },
        syncScheduler = WorkManagerSyncScheduler(context),
    )

    @Provides fun provideExerciseDao(db: SpotterDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideUserProfileDao(db: SpotterDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideWorkoutDao(db: SpotterDatabase): WorkoutDao = db.workoutDao()
    @Provides fun providePlanDao(db: SpotterDatabase): PlanDao = db.planDao()
    @Provides fun provideRecommendationDao(db: SpotterDatabase): RecommendationDao = db.recommendationDao()
    @Provides fun provideScheduleDao(db: SpotterDatabase): ScheduleDao = db.scheduleDao()

    @Provides fun provideUserProfiles(graph: DataGraph): UserProfileRepository = graph.userProfiles
    @Provides fun providePreferences(graph: DataGraph): UserPreferencesRepository = graph.preferences
    @Provides fun provideExercises(graph: DataGraph): ExerciseRepository = graph.exercises
    @Provides fun provideHistory(graph: DataGraph): HistoryRepository = graph.history
    @Provides fun providePlans(graph: DataGraph): PlanRepository = graph.plans
    @Provides fun provideBodyWeight(graph: DataGraph): BodyWeightRepository = graph.bodyWeight
    @Provides fun provideRestTimer(graph: DataGraph): RestTimerRepository = graph.restTimer
    @Provides fun provideWorkouts(graph: DataGraph): WorkoutRepository = graph.workouts
    @Provides fun provideSchedule(graph: DataGraph): ScheduleRepository = graph.schedule
    @Provides fun provideRecommendations(graph: DataGraph): RecommendationRepository = graph.recommendations
    @Provides fun provideSeeder(graph: DataGraph): ExerciseSeeder = graph.seeder
    @Provides fun provideStartupMaintenance(graph: DataGraph): StartupMaintenance = graph.startupMaintenance
    @Provides fun provideAccounts(graph: DataGraph): AccountService = graph.accounts
    @Provides fun provideSync(graph: DataGraph): SyncRepository = graph.sync
}
