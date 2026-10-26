package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import kotlinx.coroutines.delay
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.seconds_short
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.stopwatch_countdown
import com.viktorolsson.spotter.feature.session.resources.stopwatch_pause
import com.viktorolsson.spotter.feature.session.resources.stopwatch_reset
import com.viktorolsson.spotter.feature.session.resources.stopwatch_start
import com.viktorolsson.spotter.core.ui.localFormat

/**
 * A standalone stopwatch for timed sets (planks, carries), plus quick countdowns
 * that reuse the rest timer so they alert with the screen off too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StopwatchSheet(
    onCountdown: (seconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Elapsed = banked time + (now - runningSince) while running; survives recomposition and rotation.
    var bankedMs by rememberSaveable { mutableLongStateOf(0L) }
    var runningSince by rememberSaveable { mutableStateOf<Long?>(null) }
    var displayMs by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(runningSince) {
        while (true) {
            displayMs = bankedMs + (runningSince?.let { elapsedRealtimeMs() - it } ?: 0L)
            if (runningSince == null) break
            delay(50)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        StopwatchSheetContent(
            displayMs = displayMs,
            running = runningSince != null,
            onCountdown = onCountdown,
            onReset = { bankedMs = 0; runningSince = null; displayMs = 0 },
            onStartPause = {
                val since = runningSince
                if (since == null) {
                    runningSince = elapsedRealtimeMs()
                } else {
                    bankedMs += elapsedRealtimeMs() - since
                    runningSince = null
                }
            },
        )
    }
}

/** The sheet's body, usable on its own (screenshot tests). */
@Composable
internal fun StopwatchSheetContent(
    displayMs: Long,
    running: Boolean,
    onCountdown: (seconds: Int) -> Unit,
    onReset: () -> Unit,
    onStartPause: () -> Unit,
) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.SheetPadding).padding(bottom = Dimens.SheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                formatStopwatch(displayMs),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(onClick = onReset, enabled = !running && displayMs > 0) { Text(stringResource(Res.string.stopwatch_reset)) }
                Button(onClick = onStartPause) {
                    Text(stringResource(if (running) Res.string.stopwatch_pause else Res.string.stopwatch_start))
                }
            }
            Text(
                stringResource(Res.string.stopwatch_countdown),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(30, 45, 60, 90).forEach { seconds ->
                    AssistChip(
                        onClick = { onCountdown(seconds) },
                        label = { Text(stringResource(CoreRes.string.seconds_short, seconds)) },
                    )
                }
            }
        }
}

private fun formatStopwatch(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d.%d".localFormat(totalSeconds / 60, totalSeconds % 60, (ms % 1000) / 100)
}
