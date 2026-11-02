package com.viktorolsson.spotter.feature.plan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.viktorolsson.spotter.core.model.Program
import kotlinx.serialization.Serializable

@Serializable
data object PlanRoute

/** Every program as a card, to read about and pick from. */
@Serializable
data object ProgramsRoute

/** The plan [program] would give (a [Program] name), and the switch to it. */
@Serializable
data class ProgramPreviewRoute(val program: String)

fun NavGraphBuilder.planScreen(
    onBack: () -> Unit,
    onRebuild: () -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
) {
    composable<PlanRoute> {
        PlanRoute(onBack = onBack, onRebuild = onRebuild, onOpenPrograms = onOpenPrograms, onOpenSession = onOpenSession)
    }
}

fun NavGraphBuilder.programsScreen(
    onBack: () -> Unit,
    onPreview: (Program) -> Unit,
    onQuestionnaire: () -> Unit,
) {
    composable<ProgramsRoute> { ProgramsRoute(onBack = onBack, onPreview = onPreview, onQuestionnaire = onQuestionnaire) }
}

fun NavGraphBuilder.programPreviewScreen(
    onBack: () -> Unit,
    onSwitched: () -> Unit,
) {
    composable<ProgramPreviewRoute> { ProgramPreviewRoute(onBack = onBack, onSwitched = onSwitched) }
}
