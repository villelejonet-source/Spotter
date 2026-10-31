package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.uppercaseLocal
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.core.ui.formatClock
import kotlin.time.Instant
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.rest_minus
import com.viktorolsson.spotter.feature.session.resources.rest_plus
import com.viktorolsson.spotter.feature.session.resources.rest_skip
import com.viktorolsson.spotter.feature.session.resources.rest_title

/**
 * The rest countdown, pinned at the bottom: a glowing progress line, the time in large
 * monospace digits, and −15 s / +15 s / Skip as big buttons for a sweaty thumb.
 */
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
    val progress = if (rest.totalSeconds > 0) (remaining.toFloat() / rest.totalSeconds).coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    val glow = SpotterTheme.colors.glow
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(if (compact) Modifier else Modifier.navigationBarsPadding()) {
            Canvas(Modifier.fillMaxWidth().height(PROGRESS_HEIGHT)) {
                drawRect(track)
                val end = size.width * progress
                drawRect(glow, size = Size(end, size.height * 2))
                drawRect(accent, size = Size(end, size.height))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = if (compact) Spacing.xs else Spacing.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.rest_title).uppercaseLocal(),
                        style = SpotterTheme.type.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatClock(remaining),
                        style = if (compact) SpotterTheme.type.numberMedium else SpotterTheme.type.numberLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val shape = MaterialTheme.shapes.medium
                val outline = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outline)
                val height = if (compact) Dimens.TouchTarget else Dimens.TouchTarget + Spacing.sm
                OutlinedButton(onClick = { onAdjust(-15) }, shape = shape, border = outline, contentPadding = ADJUST_PADDING, modifier = Modifier.height(height)) {
                    Text(stringResource(Res.string.rest_minus), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = { onAdjust(15) }, shape = shape, border = outline, contentPadding = ADJUST_PADDING, modifier = Modifier.height(height)) {
                    Text(stringResource(Res.string.rest_plus), style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = onSkip, shape = shape, modifier = Modifier.height(height)) {
                    Text(stringResource(Res.string.rest_skip), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val PROGRESS_HEIGHT = 3.dp
private val ADJUST_PADDING = PaddingValues(horizontal = Spacing.md)
