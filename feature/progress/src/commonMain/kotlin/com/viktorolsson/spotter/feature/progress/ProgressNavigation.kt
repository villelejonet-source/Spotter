package com.viktorolsson.spotter.feature.progress

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProgressRoute

fun NavGraphBuilder.progressScreen(onOpenExercise: (String) -> Unit) {
    composable<ProgressRoute> { ProgressRoute(onOpenExercise = onOpenExercise) }
}
