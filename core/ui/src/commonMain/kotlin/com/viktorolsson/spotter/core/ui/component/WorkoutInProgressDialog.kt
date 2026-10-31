package com.viktorolsson.spotter.core.ui.component

import androidx.compose.runtime.Composable
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.workout_in_progress_open
import com.viktorolsson.spotter.core.ui.resources.workout_in_progress_text
import com.viktorolsson.spotter.core.ui.resources.workout_in_progress_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shown when starting a workout while another one is running: only one can be active, so
 * the choice is to go to the running one or stay here.
 */
@Composable
fun WorkoutInProgressDialog(onOpen: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    title = stringResource(Res.string.workout_in_progress_title),
    text = stringResource(Res.string.workout_in_progress_text),
    confirmText = stringResource(Res.string.workout_in_progress_open),
    onConfirm = onOpen,
    onDismiss = onDismiss,
)
