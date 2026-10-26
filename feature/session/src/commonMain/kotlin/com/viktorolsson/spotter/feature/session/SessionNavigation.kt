package com.viktorolsson.spotter.feature.session

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class SessionRoute(val sessionId: Long)

@Serializable
data class SummaryRoute(val sessionId: Long)

fun NavGraphBuilder.sessionScreen(
    onAddExercises: (sessionId: Long) -> Unit,
    onFinished: (sessionId: Long) -> Unit,
    onClose: () -> Unit,
) {
    composable<SessionRoute> {
        SessionRoute(onAddExercises = onAddExercises, onFinished = onFinished, onClose = onClose)
    }
}

fun NavGraphBuilder.summaryScreen(onDone: () -> Unit) {
    composable<SummaryRoute> { SummaryRoute(onDone = onDone) }
}
