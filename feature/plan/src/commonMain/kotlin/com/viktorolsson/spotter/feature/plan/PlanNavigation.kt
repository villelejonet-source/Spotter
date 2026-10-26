package com.viktorolsson.spotter.feature.plan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlanRoute

fun NavGraphBuilder.planScreen(
    onBack: () -> Unit,
    onRebuild: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
) {
    composable<PlanRoute> { PlanRoute(onBack = onBack, onRebuild = onRebuild, onOpenSession = onOpenSession) }
}
