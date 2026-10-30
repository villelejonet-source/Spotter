package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.uppercaseLocal
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*

/** A heading between groups of content on a screen: a small uppercase label, read as a heading. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercaseLocal(),
        style = SpotterTheme.type.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** A small all-caps label above a card's headline ("NEXT WORKOUT"), in the accent. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercaseLocal(),
        style = SpotterTheme.type.label,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** A card's own title: the small uppercase label, muted, read as a heading. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercaseLocal(),
        style = SpotterTheme.type.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * A content card, the website's panel: `surfaceContainerLow` with a hairline border and a
 * faint top-lit gradient, [Dimens.CardPadding] inside, [Spacing.sm] between its children.
 * Stat tiles use `surfaceContainerHigh` instead (see [StatTile]).
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            Modifier.topLit().padding(Dimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            content()
        }
    }
}

/** The panel's light from above: a faint gradient over the top of a card. */
@Composable
fun Modifier.topLit(): Modifier {
    val light = MaterialTheme.colorScheme.onSurface.copy(alpha = TOP_LIGHT_ALPHA)
    return background(Brush.verticalGradient(0f to light, TOP_LIGHT_END to Color.Transparent))
}

/** A single number with its label, in a `surfaceContainerHigh` tile; several sit in a row. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(Dimens.TilePadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                label.uppercaseLocal(),
                style = SpotterTheme.type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = SpotterTheme.type.numberMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private const val TOP_LIGHT_ALPHA = 0.035f
private const val TOP_LIGHT_END = 0.7f

/** The "Superset" tag next to an exercise that's paired with the next one. */
@Composable
fun SupersetBadge(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            stringResource(Res.string.superset_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Dimens.BadgePaddingHorizontal, vertical = Spacing.xxs),
        )
    }
}
