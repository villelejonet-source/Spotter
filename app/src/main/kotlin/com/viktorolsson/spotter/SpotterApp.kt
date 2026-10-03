package com.viktorolsson.spotter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viktorolsson.spotter.core.data.repository.ActiveSession
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.feature.history.historyScreen
import com.viktorolsson.spotter.feature.library.ExercisePickerRoute
import com.viktorolsson.spotter.feature.library.exercisePickerScreen
import com.viktorolsson.spotter.feature.progress.progressScreen
import com.viktorolsson.spotter.feature.session.SessionRoute
import com.viktorolsson.spotter.feature.session.SummaryRoute
import com.viktorolsson.spotter.feature.session.sessionScreen
import com.viktorolsson.spotter.feature.session.summaryScreen
import com.viktorolsson.spotter.feature.session.timer.WorkoutService
import com.viktorolsson.spotter.feature.settings.profileScreen
import com.viktorolsson.spotter.feature.today.TodayRoute
import com.viktorolsson.spotter.feature.today.todayScreen
import com.viktorolsson.spotter.navigation.TopLevelDestination
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun SpotterApp(
    navController: NavHostController = rememberNavController(),
    activeWorkoutViewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TopLevelDestination.entries.any { currentDestination.isOnTab(it) }
    val activeSession by activeWorkoutViewModel.activeSession.collectAsStateWithLifecycle()

    KeepWorkoutServiceRunning(activeSession != null)

    Scaffold(
        bottomBar = {
            if (onTab) {
                Column {
                    activeSession?.let { session ->
                        WorkoutMiniBar(session) { navController.navigate(SessionRoute(session.id)) }
                    }
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = currentDestination.isOnTab(destination)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(destination) },
                                icon = {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            }
        },
        // Each screen owns its top app bar and status-bar insets.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            todayScreen(onOpenSession = { navController.navigate(SessionRoute(it)) })
            historyScreen()
            progressScreen()
            profileScreen()
            sessionScreen(
                onAddExercises = { navController.navigate(ExercisePickerRoute(it)) },
                onFinished = { id ->
                    navController.navigate(SummaryRoute(id)) {
                        popUpTo<SessionRoute> { inclusive = true }
                    }
                },
                onClose = { navController.popBackStack() },
            )
            exercisePickerScreen(onDone = { navController.popBackStack() })
            summaryScreen(onDone = { navController.popBackStack() })
        }
    }
}

/** "Workout in progress · 32:10" above the tabs; tap to jump back in. */
@Composable
private fun WorkoutMiniBar(session: ActiveSession, onClick: () -> Unit) {
    val elapsed by produceState(Duration.ZERO, session.startedAt) {
        while (true) {
            value = Duration.between(session.startedAt, Instant.now())
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.open_workout), onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.workout_in_progress) + " · " + formatClock(elapsed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Starts the workout service whenever the app is in the foreground with a workout
 * active, which also restores the rest timer after the process was killed.
 */
@Composable
private fun KeepWorkoutServiceRunning(active: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { WorkoutService.start(context) }
    }
}

private fun NavDestination?.isOnTab(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Standard bottom-bar behaviour: one back stack per tab, restored when you return to it. */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
