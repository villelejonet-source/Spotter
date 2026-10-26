package com.viktorolsson.spotter.feature.library

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The library screens' view models, for Koin. */
val libraryModule = module {
    viewModelOf(::ExercisePickerViewModel)
}
