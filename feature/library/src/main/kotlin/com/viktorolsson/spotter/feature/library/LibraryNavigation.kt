package com.viktorolsson.spotter.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Picks exercises and appends them to the given workout session. */
@Serializable
data class ExercisePickerRoute(val sessionId: Long)

fun NavGraphBuilder.exercisePickerScreen(onDone: () -> Unit) {
    composable<ExercisePickerRoute> { ExercisePickerRoute(onDone = onDone) }
}
