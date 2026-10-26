package com.viktorolsson.spotter.shell

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

/** The shell's own view models, for Koin. */
val shellModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::ActiveWorkoutViewModel)
    viewModelOf(::SyncChoiceViewModel)
    viewModelOf(::PasswordRecoveryViewModel)
}

/** Every screen's view models: what the platform adds to its data module when it starts Koin. */
val uiModules: List<Module> = listOf(
    shellModule,
    todayModule,
    historyModule,
    progressModule,
    settingsModule,
    libraryModule,
    sessionModule,
    onboardingModule,
    planModule,
)
