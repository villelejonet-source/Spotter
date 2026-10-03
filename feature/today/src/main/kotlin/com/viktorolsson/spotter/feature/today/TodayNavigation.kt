package com.viktorolsson.spotter.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

fun NavGraphBuilder.todayScreen(onOpenSession: (sessionId: Long) -> Unit) {
    composable<TodayRoute> { TodayRoute(onOpenSession = onOpenSession) }
}
