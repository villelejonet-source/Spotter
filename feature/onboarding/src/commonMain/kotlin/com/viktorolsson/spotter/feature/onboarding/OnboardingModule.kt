package com.viktorolsson.spotter.feature.onboarding

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The onboarding screens' view models, for Koin. */
val onboardingModule = module {
    viewModelOf(::OnboardingViewModel)
}
