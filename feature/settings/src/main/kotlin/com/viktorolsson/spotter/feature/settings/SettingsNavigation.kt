package com.viktorolsson.spotter.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProfileRoute

fun NavGraphBuilder.profileScreen() {
    composable<ProfileRoute> { SettingsRoute() }
}
