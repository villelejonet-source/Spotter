package com.viktorolsson.spotter.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.ProgramBlocker
import com.viktorolsson.spotter.core.ui.forRes
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.pitchRes
import com.viktorolsson.spotter.core.ui.progressRes
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.weekRes
import org.jetbrains.compose.resources.stringResource

/**
 * A card for one way to train: its name and one line on what it is, a "Suits you" or "Your
 * plan" mark, and, when [expanded], who it's for, what a week looks like and how it progresses.
 * A [blocker] says why it can't be picked instead of hiding the card. [selected] draws it like
 * a chosen [OptionCard]; [actions] go under the details (the Programs screen's "Use this program").
 */
@Composable
fun ProgramCard(
    program: Program,
    /** Null before there's a profile to read the days from (5/3/1 is always four). */
    daysPerWeek: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suits: Boolean = false,
    current: Boolean = false,
    blocker: ProgramBlocker? = null,
    selected: Boolean = false,
    expanded: Boolean = false,
    /** Tapping expands and collapses it (the Programs screen), rather than choosing it. */
    expandable: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val muted = blocker != null
    val state = if (expandable) stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed) else null
    Surface(
        onClick = onClick,
        selected = selected,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else Dimens.Hairline, if (selected) colors.primary else colors.outlineVariant),
        modifier = modifier.fillMaxWidth().semantics { if (state != null) stateDescription = state },
    ) {
        val textColor = if (muted) colors.onSurfaceVariant else Color.Unspecified
        Column(
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(program.labelRes), style = MaterialTheme.typography.titleMedium, color = textColor, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary)
            }
            Text(stringResource(program.pitchRes), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (daysPerWeek != null) Tag(stringResource(Res.string.program_days, daysPerWeek), colors.surfaceContainerHighest, colors.onSurfaceVariant)
                if (current) Tag(stringResource(Res.string.program_current), colors.secondaryContainer, colors.onSecondaryContainer)
                if (suits && !muted) Tag(stringResource(Res.string.program_suits_you), colors.primaryContainer, colors.onPrimaryContainer)
            }
            if (blocker != null) {
                Text(stringResource(blocker.labelRes), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Detail(stringResource(Res.string.program_heading_for), stringResource(program.forRes))
                    Detail(stringResource(Res.string.program_heading_week), stringResource(program.weekRes))
                    Detail(stringResource(Res.string.program_heading_progress), stringResource(program.progressRes))
                    actions?.invoke()
                }
            }
        }
    }
}

@Composable
private fun Detail(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        CardTitle(heading)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = Dimens.BadgePaddingHorizontal, vertical = Spacing.xxs),
        )
    }
}
