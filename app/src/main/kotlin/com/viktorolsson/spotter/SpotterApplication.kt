package com.viktorolsson.spotter

import android.app.Application
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.seed.ExerciseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpotterApplication : Application() {
    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { exerciseSeeder.seedIfNeeded() }
    }
}
