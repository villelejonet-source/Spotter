package com.viktorolsson.spotter.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.ui.component.SpotterNavigationBar
import com.viktorolsson.spotter.core.ui.component.WorkoutMiniBar
import com.viktorolsson.spotter.core.ui.component.spotterNavigationItemColors
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
import com.viktorolsson.spotter.feature.settings.AccountRoute
import com.viktorolsson.spotter.feature.settings.accountScreen
import com.viktorolsson.spotter.feature.settings.profileScreen
import com.viktorolsson.spotter.feature.today.TodayRoute
import com.viktorolsson.spotter.feature.today.todayScreen
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import com.viktorolsson.spotter.shell.resources.Res
import com.viktorolsson.spotter.shell.resources.error_action
import com.viktorolsson.spotter.shell.resources.error_save
import com.viktorolsson.spotter.shell.resources.error_sign_in_link
import com.viktorolsson.spotter.shell.resources.sync_choice_backup
import com.viktorolsson.spotter.shell.resources.sync_choice_body
import com.viktorolsson.spotter.shell.resources.sync_choice_body_account
import com.viktorolsson.spotter.shell.resources.sync_choice_merge
import com.viktorolsson.spotter.shell.resources.sync_choice_note
import com.viktorolsson.spotter.shell.resources.sync_choice_title
import com.viktorolsson.spotter.shell.resources.sync_upload_body
import com.viktorolsson.spotter.shell.resources.sync_upload_body_no_email
import com.viktorolsson.spotter.shell.resources.sync_upload_confirm
import com.viktorolsson.spotter.shell.resources.sync_upload_decline
import com.viktorolsson.spotter.shell.resources.sync_upload_title

@Composable
/** The app below the window: bottom bar, every screen's route, and the app-wide sync dialogs. */
fun SpotterApp(
    needsOnboarding: Boolean,
    navController: NavHostController = rememberNavController(),
    activeWorkoutViewModel: ActiveWorkoutViewModel = koinViewModel(),
    syncChoiceViewModel: SyncChoiceViewModel = koinViewModel(),
    passwordRecoveryViewModel: PasswordRecoveryViewModel = koinViewModel(),
    errors: AppErrors = koinInject(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = TopLevelDestination.entries.any { currentDestination.isOnTab(it) }
    val activeSession by activeWorkoutViewModel.activeSession.collectAsStateWithLifecycle()

    WhileWorkoutActive(activeSession != null)

    val syncChoice by syncChoiceViewModel.uiState.collectAsStateWithLifecycle()
    if (syncChoice.needsChoice) {
        if (syncChoice.fromLink) {
            UploadChoiceDialog(email = syncChoice.email, onUpload = { syncChoiceViewModel.choose(FirstSyncChoice.MERGE) }, onSignOut = syncChoiceViewModel::signOut)
        } else {
            SyncChoiceDialog(email = syncChoice.email, onChoose = syncChoiceViewModel::choose)
        }
    }

    // Any failure the person should know about, from any screen or background work (AppErrors).
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(errors) {
        errors.messages.collect { failure ->
            snackbar.showSnackbar(
                getString(
                    when (failure) {
                        Failure.SAVE -> Res.string.error_save
                        Failure.ACTION -> Res.string.error_action
                        Failure.SIGN_IN_LINK -> Res.string.error_sign_in_link
                    },
                ),
            )
        }
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
                                value = (Clock.System.now() - session.startedAt)
                                delay(1000L - Clock.System.now().toEpochMilliseconds() % 1000L)
                            }
                        }
                        WorkoutMiniBar(elapsed, onClick = { navController.navigate(SessionRoute(session.id)) })
                    }
                    SpotterNavigationBar {
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
                                colors = spotterNavigationItemColors(),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
 * Signed in from an emailed link into an account that has no data yet: nothing is uploaded
 * until the user says so (a crafted link must not pull this phone's data into a stranger's
 * account). Declining signs out again.
 */
@Composable
internal fun UploadChoiceDialog(email: String?, onUpload: () -> Unit, onSignOut: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.sync_upload_title)) },
        text = { Text(if (email != null) stringResource(Res.string.sync_upload_body, email) else stringResource(Res.string.sync_upload_body_no_email)) },
        confirmButton = { Button(onClick = onUpload) { Text(stringResource(Res.string.sync_upload_confirm)) } },
        dismissButton = { TextButton(onClick = onSignOut) { Text(stringResource(Res.string.sync_upload_decline)) } },
    )
}

/**
 * The first sync found data both here and in the backup. It can only be answered, not
 * dismissed: syncing (and so the two data sets) stays untouched until the user decides.
 */
@Composable
internal fun SyncChoiceDialog(email: String?, onChoose: (FirstSyncChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.sync_choice_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
                Text(if (email != null) stringResource(Res.string.sync_choice_body_account, email) else stringResource(Res.string.sync_choice_body))
                Text(
                    stringResource(Res.string.sync_choice_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onChoose(FirstSyncChoice.USE_BACKUP) }) { Text(stringResource(Res.string.sync_choice_backup)) }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(FirstSyncChoice.MERGE) }) { Text(stringResource(Res.string.sync_choice_merge)) }
        },
    )
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
