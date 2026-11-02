package com.viktorolsson.spotter.feature.progress

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProgressRoute

@Serializable
data object AchievementsRoute

fun NavGraphBuilder.progressScreen(onOpenExercise: (String) -> Unit, onOpenLevels: () -> Unit) {
    composable<ProgressRoute> { ProgressRoute(onOpenExercise = onOpenExercise, onOpenLevels = onOpenLevels) }
}

fun NavGraphBuilder.achievementsScreen(onBack: () -> Unit) {
    composable<AchievementsRoute> { AchievementsRoute(onBack = onBack) }
}
