package com.viktorolsson.spotter.feature.settings

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The settings screens' view models, for Koin. */
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AccountViewModel)
}
