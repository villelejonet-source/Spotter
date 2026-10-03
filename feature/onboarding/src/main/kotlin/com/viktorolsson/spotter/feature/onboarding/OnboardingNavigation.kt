package com.viktorolsson.spotter.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** [rebuild] starts from the saved profile and hides "Skip". */
@Serializable
data class OnboardingRoute(val rebuild: Boolean = false)

fun NavGraphBuilder.onboardingScreen(onDone: () -> Unit, onSignIn: (newAccount: Boolean) -> Unit) {
    composable<OnboardingRoute> { OnboardingRoute(onDone = onDone, onSignIn = onSignIn) }
}
