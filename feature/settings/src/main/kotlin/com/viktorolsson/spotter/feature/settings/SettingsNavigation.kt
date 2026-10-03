package com.viktorolsson.spotter.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProfileRoute

fun NavGraphBuilder.profileScreen(onOpenPlan: () -> Unit, onRebuildPlan: () -> Unit, onOpenAccount: (newAccount: Boolean) -> Unit) {
    composable<ProfileRoute> { SettingsRoute(onOpenPlan = onOpenPlan, onRebuildPlan = onRebuildPlan, onOpenAccount = onOpenAccount) }
}
