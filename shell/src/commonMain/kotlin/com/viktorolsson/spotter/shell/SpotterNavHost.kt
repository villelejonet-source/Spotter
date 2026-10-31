package com.viktorolsson.spotter.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.viktorolsson.spotter.feature.today.TodayRoute

/**
 * The app's NavHost: every screen's route, starting on Today. Switching bottom tabs is
 * instant; any other move keeps Navigation's default fade. A fade between tabs kept both on
 * screen for 0.7 s, so the old tab showed behind the new one and a quick second tap landed
 * mid-transition.
 */
@Composable
internal fun SpotterNavHost(navController: NavHostController, modifier: Modifier = Modifier, builder: NavGraphBuilder.() -> Unit) {
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        modifier = modifier,
        enterTransition = { if (isTabSwitch()) EnterTransition.None else fadeIn(tween(FADE_MILLIS)) },
        exitTransition = { if (isTabSwitch()) ExitTransition.None else fadeOut(tween(FADE_MILLIS)) },
        builder = builder,
    )
}

/** Navigation's own default fade, kept for everything but tab switches. */
private const val FADE_MILLIS = 700

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTab() && targetState.destination.isTab()

private fun NavDestination.isTab(): Boolean = TopLevelDestination.entries.any { isOnTab(it) }

internal fun NavDestination?.isOnTab(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Standard bottom-bar behaviour: one back stack per tab, restored when you return to it. */
internal fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
