package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.viktorolsson.spotter.core.model.LOW_CHECK_IN
import com.viktorolsson.spotter.core.model.Readiness
import com.viktorolsson.spotter.core.model.SessionAdvice
import com.viktorolsson.spotter.core.ui.component.FeelPicker
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.*
import com.viktorolsson.spotter.feature.today.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * "How do you feel today?" before a planned workout starts. A low score asks whether to
 * make today lighter, unless a deload week ([inDeloadWeek]) already does. [onStart] gets the
 * score (null when skipped) and the lighter choice (null to leave it to the deload week).
 *
 * With a readiness from the health store ([measured]) the sheet opens on what was measured:
 * the matching face is picked and the suggestion is one tap away. The faces stay, so the
 * person can answer for themselves instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckInSheet(
    inDeloadWeek: Boolean,
    measured: Readiness?,
    onStart: (readiness: Int?, lighter: Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    var readiness by rememberSaveable { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CheckInContent(readiness, inDeloadWeek, measured, onPick = { readiness = it }, onStart = onStart, onRest = onDismiss)
    }
}

/** The sheet's content: the faces (with what was measured, if anything), or the lighter question once a low score is picked. */
@Composable
internal fun CheckInContent(
    readiness: Int?,
    inDeloadWeek: Boolean,
    measured: Readiness? = null,
    onPick: (Int) -> Unit,
    onStart: (readiness: Int?, lighter: Boolean?) -> Unit,
    onRest: () -> Unit = {},
) {
    if (readiness != null && readiness <= LOW_CHECK_IN && !inDeloadWeek) {
        LighterQuestion(onLighter = { onStart(readiness, true) }, onKeep = { onStart(readiness, false) })
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.SheetPadding).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(Res.string.checkin_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (measured != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(readinessTitle(measured.level), style = MaterialTheme.typography.titleMedium)
                Text(readinessSummary(measured), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FeelPicker(selected = readiness ?: measured?.level?.checkIn, onSelect = { picked ->
            onPick(picked)
            if (picked > LOW_CHECK_IN || inDeloadWeek) onStart(picked, null)
        })
        if (measured != null) {
            // Its buttons start the workout too, so "Skip" has nothing left to mean.
            Suggestion(measured, inDeloadWeek, onStart, onRest)
        } else {
            TextButton(onClick = { onStart(null, null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.checkin_skip))
            }
        }
    }
}

/** What the measured readiness suggests, each choice one tap: it starts the workout with the matching check-in. */
@Composable
private fun Suggestion(measured: Readiness, inDeloadWeek: Boolean, onStart: (readiness: Int?, lighter: Boolean?) -> Unit, onRest: () -> Unit) {
    val checkIn = measured.level.checkIn
    // A deload week is already lighter, so there's nothing to choose.
    val advice = if (inDeloadWeek) SessionAdvice.AS_PLANNED else measured.advice
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (advice == SessionAdvice.AS_PLANNED) {
            Button(onClick = { onStart(checkIn, null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.checkin_start)) }
            return@Column
        }
        Text(adviceText(advice), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { onStart(checkIn, true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.checkin_lighter)) }
        OutlinedButton(onClick = { onStart(checkIn, false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.checkin_keep)) }
        if (advice == SessionAdvice.REST_OR_LIGHT) {
            TextButton(onClick = onRest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.checkin_rest)) }
        }
    }
}

@Composable
private fun LighterQuestion(onLighter: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.SheetPadding).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(Res.string.checkin_lighter_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(Res.string.checkin_lighter_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onKeep) { Text(stringResource(Res.string.checkin_keep)) }
            Button(onClick = onLighter, modifier = Modifier.padding(start = Spacing.sm)) {
                Text(stringResource(Res.string.checkin_lighter))
            }
        }
    }
}
