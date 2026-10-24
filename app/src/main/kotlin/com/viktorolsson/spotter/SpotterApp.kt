package com.viktorolsson.spotter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.ui.component.WorkoutMiniBar
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.history.ExerciseHistoryRoute
import com.viktorolsson.spotter.feature.history.WorkoutDetailRoute
import com.viktorolsson.spotter.feature.history.exerciseHistoryScreen
import com.viktorolsson.spotter.feature.history.historyScreen
import com.viktorolsson.spotter.feature.history.workoutDetailScreen
import com.viktorolsson.spotter.feature.library.ExercisePickerRoute
import com.viktorolsson.spotter.feature.library.exercisePickerScreen
import com.viktorolsson.spotter.feature.onboarding.OnboardingRoute
import com.viktorolsson.spotter.feature.onboarding.onboardingScreen
import com.viktorolsson.spotter.feature.plan.PlanRoute
import com.viktorolsson.spotter.feature.plan.planScreen
import com.viktorolsson.spotter.feature.progress.progressScreen
import com.viktorolsson.spotter.feature.session.SessionRoute
import com.viktorolsson.spotter.feature.session.SummaryRoute
import com.viktorolsson.spotter.feature.session.sessionScreen
import com.viktorolsson.spotter.feature.session.summaryScreen
import com.viktorolsson.spotter.feature.session.timer.WorkoutService
import com.viktorolsson.spotter.feature.settings.AccountRoute
import com.viktorolsson.spotter.feature.settings.accountScreen
import com.viktorolsson.spotter.feature.settings.profileScreen
import com.viktorolsson.spotter.feature.today.TodayRoute
import com.viktorolsson.spotter.feature.today.todayScreen
import com.viktorolsson.spotter.navigation.TopLevelDestination
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun SpotterApp(
    needsOnboarding: Boolean,
    navController: NavHostController = rememberNavController(),
    activeWorkoutViewModel: ActiveWorkoutViewModel = hiltViewModel(),
    syncChoiceViewModel: SyncChoiceViewModel = hiltViewModel(),
    passwordRecoveryViewModel: PasswordRecoveryViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TopLevelDestination.entries.any { currentDestination.isOnTab(it) }
    val activeSession by activeWorkoutViewModel.activeSession.collectAsStateWithLifecycle()

    KeepWorkoutServiceRunning(activeSession != null)

    val syncChoice by syncChoiceViewModel.uiState.collectAsStateWithLifecycle()
    if (syncChoice.needsChoice) {
        SyncChoiceDialog(email = syncChoice.email, onChoose = syncChoiceViewModel::choose)
    }

    val recoveringPassword by passwordRecoveryViewModel.recovering.collectAsStateWithLifecycle()
    LaunchedEffect(recoveringPassword) {
        if (recoveringPassword && currentDestination?.hasRoute<AccountRoute>() != true) {
            navController.navigate(AccountRoute(newPassword = true))
        }
    }

    // First launch: open the questionnaire once (it can be skipped; Today offers it again).
    var onboardingShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding && !onboardingShown) {
            onboardingShown = true
            navController.navigate(OnboardingRoute())
        }
    }

    Scaffold(
        bottomBar = {
            if (onTab) {
                Column {
                    activeSession?.let { session ->
                        val elapsed by produceState(Duration.ZERO, session.startedAt) {
                            while (true) {
                                value = Duration.between(session.startedAt, Instant.now())
                                delay(1000L - System.currentTimeMillis() % 1000L)
                            }
                        }
                        WorkoutMiniBar(elapsed, onClick = { navController.navigate(SessionRoute(session.id)) })
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
            todayScreen(
                onOpenSession = { navController.navigate(SessionRoute(it)) },
                onOpenPlan = { navController.navigate(PlanRoute) },
                onBuildPlan = { navController.navigate(OnboardingRoute()) },
            )
            historyScreen(onOpenWorkout = { navController.navigate(WorkoutDetailRoute(it)) })
            progressScreen(onOpenExercise = { navController.navigate(ExerciseHistoryRoute(it)) })
            workoutDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenExercise = { navController.navigate(ExerciseHistoryRoute(it)) },
                onOpenSession = { navController.navigate(SessionRoute(it)) },
            )
            exerciseHistoryScreen(onBack = { navController.popBackStack() })
            profileScreen(
                onOpenPlan = { navController.navigate(PlanRoute) },
                onRebuildPlan = { navController.navigate(OnboardingRoute(rebuild = true)) },
                onOpenAccount = { navController.navigate(it) },
            )
            onboardingScreen(
                onDone = { navController.popBackStack() },
                onSignIn = { navController.navigate(AccountRoute(newAccount = it)) },
            )
            accountScreen(onDone = { navController.popBackStack() })
            planScreen(
                onBack = { navController.popBackStack() },
                onRebuild = { navController.navigate(OnboardingRoute(rebuild = true)) },
                onOpenSession = { navController.navigate(SessionRoute(it)) },
            )
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

/**
 * The first sync found data both here and in the backup. It can only be answered, not
 * dismissed: syncing (and so the two data sets) stays untouched until the user decides.
 */
@Composable
internal fun SyncChoiceDialog(email: String?, onChoose: (FirstSyncChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.sync_choice_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
                Text(if (email != null) stringResource(R.string.sync_choice_body_account, email) else stringResource(R.string.sync_choice_body))
                Text(
                    stringResource(R.string.sync_choice_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onChoose(FirstSyncChoice.USE_BACKUP) }) { Text(stringResource(R.string.sync_choice_backup)) }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(FirstSyncChoice.MERGE) }) { Text(stringResource(R.string.sync_choice_merge)) }
        },
    )
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
