package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.core.ui.formatClock
import java.time.Instant

@Composable
internal fun RestTimerBar(
    rest: RestTimer,
    now: Instant,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val remaining = rest.remainingSeconds(now)
    val progress = if (rest.totalSeconds > 0) remaining.toFloat() / rest.totalSeconds else 0f
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rest_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        formatClock(remaining),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                OutlinedButton(onClick = { onAdjust(-15) }) { Text(stringResource(R.string.rest_minus)) }
                OutlinedButton(onClick = { onAdjust(15) }) { Text(stringResource(R.string.rest_plus)) }
                Button(onClick = onSkip) { Text(stringResource(R.string.rest_skip)) }
            }
        }
    }
}
