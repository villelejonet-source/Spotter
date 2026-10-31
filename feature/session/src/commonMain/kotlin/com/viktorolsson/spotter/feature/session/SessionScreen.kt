package com.viktorolsson.spotter.feature.session

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
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.engine.MobilityDrill
import com.viktorolsson.spotter.core.engine.Plates
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.NumberInput
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.Confetti
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.formatOneRepMax
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.label
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.action_cancel
import com.viktorolsson.spotter.core.ui.resources.action_undo
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.discard_body
import com.viktorolsson.spotter.feature.session.resources.discard_title
import com.viktorolsson.spotter.feature.session.resources.finish_body_incomplete
import com.viktorolsson.spotter.feature.session.resources.finish_body_nothing
import com.viktorolsson.spotter.feature.session.resources.finish_body_ok
import com.viktorolsson.spotter.feature.session.resources.finish_title
import com.viktorolsson.spotter.feature.session.resources.keypad_editing_effort
import com.viktorolsson.spotter.feature.session.resources.keypad_editing_reps
import com.viktorolsson.spotter.feature.session.resources.keypad_editing_seconds
import com.viktorolsson.spotter.feature.session.resources.keypad_editing_weight
import com.viktorolsson.spotter.feature.session.resources.pr_1rm
import com.viktorolsson.spotter.feature.session.resources.pr_banner_body
import com.viktorolsson.spotter.feature.session.resources.pr_banner_title
import com.viktorolsson.spotter.feature.session.resources.pr_reps
import com.viktorolsson.spotter.feature.session.resources.session_add_exercises
import com.viktorolsson.spotter.feature.session.resources.session_discard
import com.viktorolsson.spotter.feature.session.resources.session_empty_body
import com.viktorolsson.spotter.feature.session.resources.session_empty_title
import com.viktorolsson.spotter.feature.session.resources.session_finish
import com.viktorolsson.spotter.feature.session.resources.session_minimize
import com.viktorolsson.spotter.feature.session.resources.session_more
import com.viktorolsson.spotter.feature.session.resources.session_stopwatch
import com.viktorolsson.spotter.feature.session.resources.session_workout_note
import com.viktorolsson.spotter.feature.session.resources.set_deleted
import org.jetbrains.compose.resources.pluralStringResource
import com.viktorolsson.spotter.core.ui.plainNumber
import com.viktorolsson.spotter.core.ui.plainNumberRounded

@Composable
internal fun SessionRoute(
    onAddExercises: (Long) -> Unit,
    onFinished: (Long) -> Unit,
    onClose: () -> Unit,
    viewModel: SessionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val swap by viewModel.swap.collectAsStateWithLifecycle()
    val warmUpDone by viewModel.warmUpDone.collectAsStateWithLifecycle()

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
    val undoLabel = stringResource(CoreRes.string.action_undo)
    val setDeletedMessage = stringResource(Res.string.set_deleted)
    val currentSession by rememberUpdatedState(session)
    val currentPreferences by rememberUpdatedState(uiState.preferences)

    // One actions object for every card: built once, reads the latest session through the updated state.
    val cardActions = remember(viewModel, keypad) {
        ExerciseCardActions(
            onFieldClick = { setId, field ->
                viewModel.flushEdit()
                keypad.focus(KeypadTarget(setId, field), fieldText(currentSession, currentPreferences, KeypadTarget(setId, field)))
            },
            onWarmUps = viewModel::addWarmUpSet,
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
                    // The snackbar's own outcome settles the delete: Undo restores the set; a
                    // dismissal (timeout, swipe, or the screen going away mid-snackbar) commits it.
                    var undone = false
                    try {
                        undone = snackbar.showSnackbar(
                            message = setDeletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        ) == SnackbarResult.ActionPerformed
                    } finally {
                        if (undone) viewModel.undoDeleteSet(setId) else viewModel.confirmDeleteSet(setId)
                    }
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
            onToggleDrill = viewModel::toggleDrill,
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
        warmUpDone = warmUpDone,
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
    val onToggleDrill: (MobilityDrill) -> Unit,
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
    warmUpDone: Set<MobilityDrill> = emptySet(),
    warmUpExpanded: Boolean = false,
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
                elapsed = (now - session.startedAt),
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
                            SetField.WEIGHT -> stringResource(Res.string.keypad_editing_weight, number)
                            SetField.REPS -> stringResource(
                                if (exercise?.exercise?.isTimed == true) Res.string.keypad_editing_seconds else Res.string.keypad_editing_reps,
                                number,
                            )
                            SetField.EFFORT -> stringResource(Res.string.keypad_editing_effort, state.preferences.effortScale.label(), number)
                        },
                        field = target.field,
                        stepLabel = if (isWeight) plainNumber(step) else "1",
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
                            val value = (NumberInput.decimalOrNull(keypad.buffer) ?: 0.0) + sign * step
                            keypad.fresh = true
                            write(plainNumberRounded(value.coerceAtLeast(0.0), 2))
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
            item(key = "warmup") {
                WarmUpPanel(state.warmUp, warmUpDone, actions.onToggleDrill, initiallyExpanded = warmUpExpanded)
            }
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
                        title = stringResource(Res.string.session_empty_title),
                        body = stringResource(Res.string.session_empty_body),
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
                SecondaryButton(stringResource(Res.string.session_add_exercises), actions.onAddExercises, icon = Icons.Rounded.Add)
            }
        }
    }

    celebration?.let { event ->
        Box(Modifier.fillMaxSize()) {
            Confetti(key = event.id)
            PrBanner(event, unit, Modifier.align(Alignment.TopCenter).padding(top = Dimens.OverlayTopOffset, start = Dimens.ScreenPadding, end = Dimens.ScreenPadding))
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
            title = stringResource(Res.string.session_workout_note),
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
        title = { Text(stringResource(Res.string.finish_title)) },
        text = {
            Text(
                when {
                    completed == 0 -> stringResource(Res.string.finish_body_nothing)
                    incomplete > 0 -> org.jetbrains.compose.resources.pluralStringResource(Res.plurals.finish_body_incomplete, incomplete, incomplete)
                    else -> stringResource(Res.string.finish_body_ok)
                },
            )
        },
        confirmButton = {
            if (completed == 0) {
                TextButton(onClick = onDiscard) { Text(stringResource(Res.string.session_discard), color = MaterialTheme.colorScheme.error) }
            } else {
                Button(onClick = onFinish) { Text(stringResource(Res.string.session_finish)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.action_cancel)) } },
    )
}

@Composable
internal fun DiscardWorkoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.viktorolsson.spotter.core.ui.component.ConfirmDialog(
        title = stringResource(Res.string.discard_title),
        text = stringResource(Res.string.discard_body),
        confirmText = stringResource(Res.string.session_discard),
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
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.session_minimize))
            }
        },
        title = {
            Column {
                if (dayName != null) {
                    Text(dayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    formatClock(elapsed),
                    style = SpotterTheme.type.numberMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(Res.string.session_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.session_workout_note)) },
                        onClick = { onMenuOpenChange(false); onNote() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.session_stopwatch)) },
                        onClick = { onMenuOpenChange(false); onStopwatch() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.session_discard), color = MaterialTheme.colorScheme.error) },
                        onClick = { onMenuOpenChange(false); onDiscard() },
                    )
                }
            }
            FilledTonalButton(onClick = onFinish, modifier = Modifier.padding(end = Spacing.sm)) {
                Text(stringResource(Res.string.session_finish))
            }
        },
    )
}

/** Wall-clock "now", updated every second; durations derive from stored timestamps. */
@Composable
internal fun rememberTicker() = produceState(Clock.System.now()) {
    while (true) {
        value = Clock.System.now()
        delay(1000L - Clock.System.now().toEpochMilliseconds() % 1000L)
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
        oneRepMax?.let { stringResource(Res.string.pr_1rm, formatOneRepMax(it.value, unit)) },
        stringResource(Res.string.pr_reps).takeIf { event.records.any { it.type == PersonalRecordType.REPS_AT_WEIGHT } },
    )
    val body = stringResource(
        Res.string.pr_banner_body,
        event.exerciseName,
        formatSet(event.weightKg, event.reps, unit, event.timed),
        parts.joinToString(" · "),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.BannerElevation),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(Dimens.IconBanner))
            Column(Modifier.padding(start = Spacing.md)) {
                Text(stringResource(Res.string.pr_banner_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

private const val CELEBRATION_MS = 3_500L
