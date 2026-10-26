package com.viktorolsson.spotter.feature.plan

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The plan screens' view models, for Koin. */
val planModule = module {
    viewModelOf(::PlanViewModel)
}
