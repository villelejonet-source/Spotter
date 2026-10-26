package com.viktorolsson.spotter.di

import android.content.Context
import com.viktorolsson.spotter.ActiveWorkoutViewModel
import com.viktorolsson.spotter.BuildConfig
import com.viktorolsson.spotter.MainViewModel
import com.viktorolsson.spotter.PasswordRecoveryViewModel
import com.viktorolsson.spotter.SyncChoiceViewModel
import com.viktorolsson.spotter.core.data.di.androidDataGraph
import com.viktorolsson.spotter.core.data.di.androidDataModule
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.feature.history.historyModule
import com.viktorolsson.spotter.feature.library.libraryModule
import com.viktorolsson.spotter.feature.onboarding.onboardingModule
import com.viktorolsson.spotter.feature.plan.planModule
import com.viktorolsson.spotter.feature.progress.progressModule
import com.viktorolsson.spotter.feature.session.sessionModule
import com.viktorolsson.spotter.feature.settings.settingsModule
import com.viktorolsson.spotter.feature.today.todayModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Everything the app injects: the data layer, each feature's view models and the app shell's own. */
fun appModules(context: Context): List<Module> = listOf(
    androidDataModule(
        androidDataGraph(
            context,
            // From local.properties at build time; blank values turn cloud sync off.
            SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY),
            // Reported on sync, so it's known which app versions are still in use.
            AppBuild(BuildConfig.VERSION_CODE),
        ),
    ),
    module {
        viewModelOf(::MainViewModel)
        viewModelOf(::ActiveWorkoutViewModel)
        viewModelOf(::SyncChoiceViewModel)
        viewModelOf(::PasswordRecoveryViewModel)
    },
    todayModule,
    historyModule,
    progressModule,
    settingsModule,
    libraryModule,
    sessionModule,
    onboardingModule,
    planModule,
)
