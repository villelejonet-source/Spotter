package com.viktorolsson.spotter.feature.session

import android.os.SystemClock
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
            displayMs = bankedMs + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
            if (runningSince == null) break
            delay(50)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                formatStopwatch(displayMs),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { bankedMs = 0; runningSince = null; displayMs = 0 },
                    enabled = runningSince == null && displayMs > 0,
                ) { Text(stringResource(R.string.stopwatch_reset)) }
                Button(
                    onClick = {
                        val since = runningSince
                        if (since == null) {
                            runningSince = SystemClock.elapsedRealtime()
                        } else {
                            bankedMs += SystemClock.elapsedRealtime() - since
                            runningSince = null
                        }
                    },
                ) {
                    Text(stringResource(if (runningSince == null) R.string.stopwatch_start else R.string.stopwatch_pause))
                }
            }
            Text(
                stringResource(R.string.stopwatch_countdown),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60, 90).forEach { seconds ->
                    AssistChip(onClick = { onCountdown(seconds) }, label = { Text("${seconds}s") })
                }
            }
        }
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d.%d".format(totalSeconds / 60, totalSeconds % 60, (ms % 1000) / 100)
}
