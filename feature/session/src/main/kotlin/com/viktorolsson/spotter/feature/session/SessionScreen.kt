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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.engine.Plates
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.R as CoreR
import com.viktorolsson.spotter.core.ui.component.Confetti
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatOneRepMax
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.label
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    if (uiState.loading) {
        Scaffold { padding -> LoadingState(Modifier.padding(padding).fillMaxSize()) }
        return
    }
    val session = uiState.session ?: return
    if (!session.isActive) return

    val keypad = rememberKeypadState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentSession by rememberUpdatedState(session)
    val currentPreferences by rememberUpdatedState(uiState.preferences)

    // One actions object for every card: built once, reads the latest session through the updated state.
    val cardActions = remember(viewModel, keypad) {
        ExerciseCardActions(
            onFieldClick = { setId, field ->
                viewModel.flushEdit()
                keypad.focus(KeypadTarget(setId, field), fieldText(currentSession, currentPreferences, KeypadTarget(setId, field)))
            },
            onWarmUps = viewModel::addWarmUps,
            onToggleSet = { exercise, setId ->
                // Ticking a set off means it's logged: close the keypad too.
                if (exercise.sets.none { it.id == setId && it.isCompleted }) keypad.focus(null, "")
                viewModel.toggleSet(exercise, setId)
            },
            onCopyPrevious = viewModel::copyPrevious,
            onAddSet = viewModel::addSet,
            onDeleteSet = { setId ->
                if (keypad.target?.setId == setId) keypad.focus(null, "")
                viewModel.deleteSet(setId)
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.set_deleted),
                        actionLabel = context.getString(CoreR.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSet(setId)
                }
            },
            onSetType = viewModel::setSetType,
            onSetNote = viewModel::setSetNote,
            onSwap = viewModel::openSwap,
            onExerciseNote = viewModel::setExerciseNote,
            onRest = viewModel::setExerciseRest,
            onSupersetNext = viewModel::supersetWithNext,
            onRemoveSuperset = viewModel::removeFromSuperset,
            onMove = viewModel::moveExercise,
            onRemove = viewModel::removeExercise,
        )
    }
    val actions = remember(viewModel) {
        SessionActions(
            onMinimize = onClose,
            onFinish = { viewModel.finish(onFinished) },
            onDiscard = { viewModel.discard(onClose) },
            onSessionNote = viewModel::setSessionNote,
            onCountdown = viewModel::startCountdown,
            onAdjustRest = viewModel::adjustRest,
            onSkipRest = viewModel::skipRest,
            onAddExercises = { onAddExercises(currentSession.id) },
            onFieldEdit = viewModel::updateSetField,
            onKeypadDone = viewModel::flushEdit,
            onSwapFilter = viewModel::setSwapFilter,
            onSwapPick = viewModel::swapTo,
            onSwapClose = viewModel::closeSwap,
        )
    }

    var celebration by remember { mutableStateOf<PrEvent?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.prEvents.collect {
            celebration = it
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(celebration?.id) {
        if (celebration != null) {
            delay(CELEBRATION_MS)
            celebration = null
        }
    }
    val now by rememberTicker()

    SessionScreen(
        state = uiState,
        session = session,
        swap = swap,
        now = now,
        keypad = keypad,
        cardActions = cardActions,
        actions = actions,
        snackbarHostState = snackbar,
        celebration = celebration,
    )
}

/** Screen-level callbacks; one instance per screen. */
@Immutable
internal class SessionActions(
    val onMinimize: () -> Unit,
    val onFinish: () -> Unit,
    val onDiscard: () -> Unit,
    val onSessionNote: (String) -> Unit,
    val onCountdown: (seconds: Int) -> Unit,
    val onAdjustRest: (seconds: Int) -> Unit,
    val onSkipRest: () -> Unit,
    val onAddExercises: () -> Unit,
    val onFieldEdit: (setId: Long, SetField, text: String) -> Unit,
    val onKeypadDone: () -> Unit,
    val onSwapFilter: (SwapFilter) -> Unit,
    val onSwapPick: (exerciseId: String, replaceInPlan: Boolean) -> Unit,
    val onSwapClose: () -> Unit,
)

@Composable
internal fun SessionScreen(
    state: SessionUiState,
    session: WorkoutSession,
    swap: SwapUiState?,
    now: Instant,
    keypad: KeypadState,
    cardActions: ExerciseCardActions,
    actions: SessionActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    celebration: PrEvent? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var stopwatchOpen by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var platesFor by remember { mutableStateOf<Pair<SessionExercise, Double>?>(null) }
    val unit = state.preferences.weightUnit

    // The focused set can disappear (deleted, swapped): close the keypad then.
    LaunchedEffect(session.exercises) {
        val target = keypad.target ?: return@LaunchedEffect
        if (session.exercises.none { ex -> ex.sets.any { it.id == target.setId } }) keypad.focus(null, "")
    }
    fun write(text: String) {
        val target = keypad.target ?: return
        keypad.buffer = text
        actions.onFieldEdit(target.setId, target.field, text)
    }

    Scaffold(
        topBar = {
            SessionTopBar(
                dayName = session.planDayName,
                elapsed = Duration.between(session.startedAt, now),
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onMinimize = actions.onMinimize,
                onFinish = { confirmFinish = true },
                onNote = { editingNote = true },
                onStopwatch = { stopwatchOpen = true },
                onDiscard = { confirmDiscard = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                state.restTimer?.let { rest ->
                    RestTimerBar(
                        rest = rest,
                        now = now,
                        onAdjust = actions.onAdjustRest,
                        onSkip = actions.onSkipRest,
                        compact = keypad.target != null,
                    )
                }
                keypad.target?.let { target ->
                    val exercise = session.exercises.firstOrNull { ex -> ex.sets.any { it.id == target.setId } }
                    val set = exercise?.sets?.firstOrNull { it.id == target.setId }
                    val number = exercise?.sets?.filter { it.setType != SetType.WARMUP }?.indexOfFirst { it.id == target.setId }?.plus(1) ?: 0
                    val isWeight = target.field == SetField.WEIGHT
                    val step = if (isWeight) (if (unit == WeightUnit.KG) 2.5 else 5.0) else 1.0
                    Keypad(
                        title = when (target.field) {
                            SetField.WEIGHT -> stringResource(R.string.keypad_editing_weight, number)
                            SetField.REPS -> stringResource(
                                if (exercise?.exercise?.isTimed == true) R.string.keypad_editing_seconds else R.string.keypad_editing_reps,
                                number,
                            )
                            SetField.EFFORT -> stringResource(R.string.keypad_editing_effort, state.preferences.effortScale.label(), number)
                        },
                        field = target.field,
                        stepLabel = if (isWeight) BigDecimal(step).stripTrailingZeros().toPlainString() else "1",
                        showPlates = isWeight && exercise != null && Plates.bar(exercise.exercise, unit) != null,
                        onDigit = { c ->
                            val base = if (keypad.fresh) "" else keypad.buffer
                            if (c == '.' && base.contains('.')) return@Keypad
                            keypad.fresh = false
                            write((base + c).take(if (isWeight) 6 else 3))
                        },
                        onBackspace = {
                            val base = if (keypad.fresh) "" else keypad.buffer
                            keypad.fresh = false
                            write(base.dropLast(1))
                        },
                        onStep = { sign ->
                            val value = (keypad.buffer.replace(',', '.').toDoubleOrNull() ?: 0.0) + sign * step
                            keypad.fresh = true
                            write(BigDecimal(value.coerceAtLeast(0.0)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                        },
                        onPlates = {
                            val kg = set?.weightKg ?: return@Keypad
                            platesFor = exercise to kg
                        },
                        onNext = {
                            actions.onKeypadDone()
                            val order = session.exercises.flatMap { ex ->
                                ex.sets.flatMap { st ->
                                    listOfNotNull(
                                        KeypadTarget(st.id, SetField.WEIGHT),
                                        KeypadTarget(st.id, SetField.REPS),
                                        KeypadTarget(st.id, SetField.EFFORT).takeIf { state.preferences.effortScale != EffortScale.OFF },
                                    )
                                }
                            }
                            val next = order.getOrNull(order.indexOf(target) + 1)
                            keypad.focus(next, next?.let { fieldText(session, state.preferences, it) }.orEmpty())
                        },
                        onDone = { actions.onKeypadDone(); keypad.focus(null, "") },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            session.notes?.let { note ->
                item(key = "note") {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }
            if (session.exercises.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.FitnessCenter,
                        title = stringResource(R.string.session_empty_title),
                        body = stringResource(R.string.session_empty_body),
                    )
                }
            }
            items(session.exercises, key = { it.id }) { exercise ->
                // Only the card being edited sees the keypad's target and buffer.
                val focus = keypad.target?.takeIf { t -> exercise.sets.any { it.id == t.setId } }
                ExerciseCard(
                    exercise = exercise,
                    isFirst = exercise.position == 0,
                    isLast = exercise.position == session.exercises.lastIndex,
                    previous = state.previous[exercise.exercise.id].orEmpty(),
                    preferences = state.preferences,
                    focus = focus,
                    buffer = if (focus != null) keypad.buffer else "",
                    actions = cardActions,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "add") {
                SecondaryButton(stringResource(R.string.session_add_exercises), actions.onAddExercises, icon = Icons.Rounded.Add)
            }
        }
    }

    celebration?.let { event ->
        Box(Modifier.fillMaxSize()) {
            Confetti(key = event.id)
            PrBanner(event, unit, Modifier.align(Alignment.TopCenter).padding(top = BANNER_TOP, start = Dimens.ScreenPadding, end = Dimens.ScreenPadding))
        }
    }
    platesFor?.let { (exercise, kg) ->
        PlateSheet(exercise.exercise, kg, unit, onDismiss = { platesFor = null })
    }
    swap?.let { swapState ->
        SwapSheet(
            state = swapState,
            onFilter = actions.onSwapFilter,
            onPick = actions.onSwapPick,
            onDismiss = actions.onSwapClose,
        )
    }
    if (editingNote) {
        TextInputDialog(
            title = stringResource(R.string.session_workout_note),
            initial = session.notes.orEmpty(),
            onSave = { actions.onSessionNote(it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
    if (stopwatchOpen) {
        StopwatchSheet(
            onCountdown = { actions.onCountdown(it); stopwatchOpen = false },
            onDismiss = { stopwatchOpen = false },
        )
    }
    if (confirmFinish) {
        val allSets = session.exercises.flatMap { it.sets }
        val completed = allSets.count { it.isCompleted }
        FinishWorkoutDialog(
            completed = completed,
            incomplete = allSets.size - completed,
            onFinish = { confirmFinish = false; actions.onFinish() },
            onDiscard = { confirmFinish = false; actions.onDiscard() },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDiscard) {
        DiscardWorkoutDialog(
            onConfirm = { confirmDiscard = false; actions.onDiscard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** "Finish workout?"; with nothing completed the only way on is to discard, in the error colour. */
@Composable
internal fun FinishWorkoutDialog(completed: Int, incomplete: Int, onFinish: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Text(
                when {
                    completed == 0 -> stringResource(R.string.finish_body_nothing)
                    incomplete > 0 -> androidx.compose.ui.res.pluralStringResource(R.plurals.finish_body_incomplete, incomplete, incomplete)
                    else -> stringResource(R.string.finish_body_ok)
                },
            )
        },
        confirmButton = {
            if (completed == 0) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.session_discard), color = MaterialTheme.colorScheme.error) }
            } else {
                Button(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@Composable
internal fun DiscardWorkoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.viktorolsson.spotter.core.ui.component.ConfirmDialog(
        title = stringResource(R.string.discard_title),
        text = stringResource(R.string.discard_body),
        confirmText = stringResource(R.string.session_discard),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

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
    SpotterTopBar(
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
            FilledTonalButton(onClick = onFinish, modifier = Modifier.padding(end = Spacing.sm)) {
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

internal data class KeypadTarget(val setId: Long, val field: SetField)

/** What the keypad is editing; the first key after focusing replaces the old value. */
internal class KeypadState {
    var target by mutableStateOf<KeypadTarget?>(null)
    var buffer by mutableStateOf("")
    var fresh by mutableStateOf(true)

    /** Moves focus to [target] with its current [text]; null closes the keypad. */
    fun focus(target: KeypadTarget?, text: String) {
        this.target = target
        buffer = text
        fresh = true
    }
}

@Composable
internal fun rememberKeypadState() = remember { KeypadState() }

/** The stored value behind a field, as the keypad should first show it. */
private fun fieldText(session: WorkoutSession, preferences: UserPreferences, target: KeypadTarget): String {
    val set = session.exercises.asSequence().flatMap { it.sets }.firstOrNull { it.id == target.setId } ?: return ""
    return when (target.field) {
        SetField.WEIGHT -> set.weightKg?.let(preferences.weightUnit::format).orEmpty()
        SetField.REPS -> set.reps?.toString().orEmpty()
        SetField.EFFORT -> set.rir?.let(preferences.effortScale::display)?.toString().orEmpty()
    }
}

/** Announced politely to screen readers; the confetti itself is decorative. */
@Composable
internal fun PrBanner(event: PrEvent, unit: WeightUnit, modifier: Modifier = Modifier) {
    val oneRepMax = event.records.firstOrNull { it.type == PersonalRecordType.ESTIMATED_1RM }
    val parts = listOfNotNull(
        oneRepMax?.let { stringResource(R.string.pr_1rm, formatOneRepMax(it.value, unit)) },
        stringResource(R.string.pr_reps).takeIf { event.records.any { it.type == PersonalRecordType.REPS_AT_WEIGHT } },
    )
    val body = stringResource(
        R.string.pr_banner_body,
        event.exerciseName,
        formatSet(event.weightKg, event.reps, unit, event.timed),
        parts.joinToString(" · "),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = BANNER_ELEVATION),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(BANNER_ICON))
            Column(Modifier.padding(start = Spacing.md)) {
                Text(stringResource(R.string.pr_banner_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

private const val CELEBRATION_MS = 3_500L
private val BANNER_TOP = androidx.compose.ui.unit.Dp(96f)
private val BANNER_ICON = androidx.compose.ui.unit.Dp(36f)
private val BANNER_ELEVATION = androidx.compose.ui.unit.Dp(6f)
