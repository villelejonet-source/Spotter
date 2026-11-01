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
import com.viktorolsson.spotter.core.ui.component.FeelPicker
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.*
import com.viktorolsson.spotter.feature.today.resources.Res
import org.jetbrains.compose.resources.stringResource

/** A check-in of 1 or 2 is a low day: the sheet then offers a lighter session. */
internal const val LOW_READINESS = 2

/**
 * "How do you feel today?" before a planned workout starts. A low score asks whether to
 * make today lighter, unless a deload week ([inDeloadWeek]) already does. [onStart] gets the
 * score (null when skipped) and the lighter choice (null to leave it to the deload week).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckInSheet(
    inDeloadWeek: Boolean,
    onStart: (readiness: Int?, lighter: Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    var readiness by rememberSaveable { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CheckInContent(readiness, inDeloadWeek, onPick = { readiness = it }, onStart = onStart)
    }
}

/** The sheet's content: the faces, or the lighter question once a low score is picked. */
@Composable
internal fun CheckInContent(
    readiness: Int?,
    inDeloadWeek: Boolean,
    onPick: (Int) -> Unit,
    onStart: (readiness: Int?, lighter: Boolean?) -> Unit,
) {
    if (readiness != null && readiness <= LOW_READINESS && !inDeloadWeek) {
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
        FeelPicker(selected = readiness, onSelect = { picked ->
            onPick(picked)
            if (picked > LOW_READINESS || inDeloadWeek) onStart(picked, null)
        })
        TextButton(onClick = { onStart(null, null) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.checkin_skip))
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
