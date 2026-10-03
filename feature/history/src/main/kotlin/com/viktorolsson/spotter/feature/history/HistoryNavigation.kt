package com.viktorolsson.spotter.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute

@Serializable
data class WorkoutDetailRoute(val sessionId: Long)

@Serializable
data class ExerciseHistoryRoute(val exerciseId: String)

fun NavGraphBuilder.historyScreen(onOpenWorkout: (Long) -> Unit) {
    composable<HistoryRoute> { HistoryRoute(onOpenWorkout = onOpenWorkout) }
}

fun NavGraphBuilder.workoutDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    composable<WorkoutDetailRoute> {
        WorkoutDetailRoute(onBack = onBack, onOpenExercise = onOpenExercise, onOpenSession = onOpenSession)
    }
}

fun NavGraphBuilder.exerciseHistoryScreen(onBack: () -> Unit) {
    composable<ExerciseHistoryRoute> { ExerciseHistoryRoute(onBack = onBack) }
}
