package com.viktorolsson.spotter.feature.progress

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProgressRoute

@Serializable
data object AchievementsRoute

@Serializable
data class YearInReviewRoute(val year: Int)

fun NavGraphBuilder.progressScreen(onOpenExercise: (String) -> Unit, onOpenLevels: () -> Unit) {
    composable<ProgressRoute> { ProgressRoute(onOpenExercise = onOpenExercise, onOpenLevels = onOpenLevels) }
}

fun NavGraphBuilder.achievementsScreen(onBack: () -> Unit, onOpenYearInReview: (year: Int) -> Unit) {
    composable<AchievementsRoute> { AchievementsRoute(onBack = onBack, onOpenYearInReview = onOpenYearInReview) }
}

fun NavGraphBuilder.yearInReviewScreen(onBack: () -> Unit) {
    composable<YearInReviewRoute> { YearInReviewRoute(onBack = onBack) }
}
