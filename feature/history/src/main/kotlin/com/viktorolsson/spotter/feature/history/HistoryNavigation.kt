package com.viktorolsson.spotter.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute

fun NavGraphBuilder.historyScreen() {
    composable<HistoryRoute> { HistoryScreen() }
}
