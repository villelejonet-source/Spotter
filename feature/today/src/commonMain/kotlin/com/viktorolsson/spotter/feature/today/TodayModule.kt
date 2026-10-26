package com.viktorolsson.spotter.feature.today

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The today screens' view models, for Koin. */
val todayModule = module {
    viewModelOf(::TodayViewModel)
}
