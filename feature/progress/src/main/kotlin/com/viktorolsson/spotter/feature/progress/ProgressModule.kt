package com.viktorolsson.spotter.feature.progress

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The progress screens' view models, for Koin. */
val progressModule = module {
    viewModelOf(::ProgressViewModel)
}
