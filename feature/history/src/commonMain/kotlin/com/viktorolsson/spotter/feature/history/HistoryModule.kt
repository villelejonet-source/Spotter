package com.viktorolsson.spotter.feature.history

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The history screens' view models, for Koin. */
val historyModule = module {
    viewModelOf(::HistoryViewModel)
    viewModelOf(::WorkoutDetailViewModel)
    viewModelOf(::ExerciseHistoryViewModel)
}
