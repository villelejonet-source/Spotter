package com.viktorolsson.spotter

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpotterApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Injected eagerly so it starts syncing as soon as a signed-in session is restored.
    @Inject lateinit var syncRepository: SyncRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    @Inject lateinit var recommendationRepository: RecommendationRepository

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            exerciseSeeder.seedIfNeeded()
            // Time-based suggestions (scheduled deload, switching back from a variation) can fall due between workouts.
            recommendationRepository.refresh()
        }    }
}
