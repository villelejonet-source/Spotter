package com.viktorolsson.spotter.feature.session

import com.viktorolsson.spotter.core.data.di.ApplicationScope
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The session screens' view models, for Koin. */
val sessionModule = module {
    viewModelOf(::SummaryViewModel)
    viewModel {
        SessionViewModel(get(), get(), get(), get(), get(), get(), get(), get(ApplicationScope), get(), get())
    }
}
