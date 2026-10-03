package com.viktorolsson.spotter.feature.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.ui.formatClock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
internal fun SessionRoute(
    onAddExercises: (Long) -> Unit,
    onFinished: (Long) -> Unit,
    onClose: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val swap by viewModel.swap.collectAsStateWithLifecycle()

    // Discarded (or finished elsewhere): nothing to show.
    LaunchedEffect(uiState.loading, uiState.session) {
        if (!uiState.loading && uiState.session?.isActive != true) onClose()
    }
    KeepScreenOn()
    RequestNotificationPermission()

    val session = uiState.session ?: return
    if (!session.isActive) return

    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var stopwatchOpen by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val now by rememberTicker()

    Scaffold(
        topBar = {
            SessionTopBar(
                dayName = session.planDayName,
                elapsed = Duration.between(session.startedAt, now),
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onMinimize = onClose,
                onFinish = { confirmFinish = true },
                onNote = { editingNote = true },
                onStopwatch = { stopwatchOpen = true },
                onDiscard = { confirmDiscard = true },
            )
        },
        bottomBar = {
            uiState.restTimer?.let { rest ->
                RestTimerBar(
                    rest = rest,
                    now = now,
                    onAdjust = viewModel::adjustRest,
                    onSkip = viewModel::skipRest,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            session.notes?.let { note ->
                item(key = "note") {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            if (session.exercises.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.session_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.session_empty_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(session.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    isFirst = exercise.position == 0,
                    isLast = exercise.position == session.exercises.lastIndex,
                    previous = uiState.previous[exercise.exercise.id].orEmpty(),
                    preferences = uiState.preferences,
                    actions = ExerciseCardActions(
                        onValuesChange = viewModel::updateSetValues,
                        onToggleSet = { setId -> viewModel.toggleSet(exercise, setId) },
                        onCopyPrevious = viewModel::copyPrevious,
                        onAddSet = { viewModel.addSet(exercise.id) },
                        onDeleteSet = viewModel::deleteSet,
                        onSetType = viewModel::setSetType,
                        onSetNote = viewModel::setSetNote,
                        onSwap = { viewModel.openSwap(exercise) },
                        onExerciseNote = { viewModel.setExerciseNote(exercise.id, it) },
                        onRest = { viewModel.setExerciseRest(exercise.id, it) },
                        onSupersetNext = { viewModel.supersetWithNext(exercise.id) },
                        onRemoveSuperset = { viewModel.removeFromSuperset(exercise.id) },
                        onMove = { viewModel.moveExercise(exercise.id, it) },
                        onRemove = { viewModel.removeExercise(exercise.id) },
                    ),
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = { onAddExercises(session.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.session_add_exercises), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    swap?.let { state ->
        SwapSheet(
            state = state,
            onFilter = viewModel::setSwapFilter,
            onPick = viewModel::swapTo,
            onDismiss = viewModel::closeSwap,
        )
    }
    if (editingNote) {
        TextInputDialog(
            title = stringResource(R.string.session_workout_note),
            initial = session.notes.orEmpty(),
            onSave = { viewModel.setSessionNote(it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
    if (stopwatchOpen) {
        StopwatchSheet(
            onCountdown = { viewModel.startCountdown(it); stopwatchOpen = false },
            onDismiss = { stopwatchOpen = false },
        )
    }
    if (confirmFinish) {
        val allSets = session.exercises.flatMap { it.sets }
        val completed = allSets.count { it.isCompleted }
        val incomplete = allSets.size - completed
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Text(
                    when {
                        completed == 0 -> stringResource(R.string.finish_body_nothing)
                        incomplete > 0 -> stringResource(R.string.finish_body_incomplete, incomplete)
                        else -> stringResource(R.string.finish_body_ok)
                    },
                )
            },
            confirmButton = {
                if (completed == 0) {
                    TextButton(onClick = { confirmFinish = false; viewModel.discard(onClose) }) {
                        Text(stringResource(R.string.session_discard))
                    }
                } else {
                    Button(onClick = { confirmFinish = false; viewModel.finish(onFinished) }) {
                        Text(stringResource(R.string.session_finish))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discard(onClose) }) {
                    Text(stringResource(R.string.session_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    dayName: String?,
    elapsed: Duration,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onNote: () -> Unit,
    onStopwatch: () -> Unit,
    onDiscard: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMinimize) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.session_minimize))
            }
        },
        title = {
            Column {
                if (dayName != null) {
                    Text(dayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    formatClock(elapsed),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.session_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_workout_note)) },
                        onClick = { onMenuOpenChange(false); onNote() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_stopwatch)) },
                        onClick = { onMenuOpenChange(false); onStopwatch() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_discard), color = MaterialTheme.colorScheme.error) },
                        onClick = { onMenuOpenChange(false); onDiscard() },
                    )
                }
            }
            FilledTonalButton(onClick = onFinish, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.session_finish))
            }
        },
    )
}

/** Wall-clock "now", updated every second; durations derive from stored timestamps. */
@Composable
internal fun rememberTicker() = produceState(Instant.now()) {
    while (true) {
        value = Instant.now()
        delay(1000L - System.currentTimeMillis() % 1000L)
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Android 13+: the rest notification needs permission; ask once when a workout opens. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
