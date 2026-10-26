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
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.core.ui.formatClock
import kotlin.time.Instant

@Composable
internal fun RestTimerBar(
    rest: RestTimer,
    now: Instant,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    /** Slimmer, and without nav-bar padding, when the keypad sits below it. */
    compact: Boolean = false,
) {
    val remaining = rest.remainingSeconds(now)
    val progress = if (rest.totalSeconds > 0) remaining.toFloat() / rest.totalSeconds else 0f
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(if (compact) Modifier else Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = if (compact) Spacing.xs else Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rest_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        formatClock(remaining),
                        style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
                            .copy(fontFeatureSettings = "tnum"),
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
