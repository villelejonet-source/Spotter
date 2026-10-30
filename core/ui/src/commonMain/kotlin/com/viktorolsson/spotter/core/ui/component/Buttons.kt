package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*

/**
 * The one main action on a screen: full width, [Dimens.ButtonHeight] tall, the accent as a
 * gradient with its glow (the website's `.btn-primary`). While [busy] it shows a spinner
 * (announced as "Loading") and can't be pressed again.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    val active = enabled && !busy
    val shape = MaterialTheme.shapes.medium
    val fill = if (active) {
        Modifier
            .shadow(Dimens.GlowElevation, shape, ambientColor = SpotterTheme.colors.glow, spotColor = SpotterTheme.colors.glow)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, SpotterTheme.colors.primaryStrong)), shape)
    } else {
        Modifier
    }
    Button(
        onClick = onClick,
        enabled = active,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier.fillMaxWidth().height(Dimens.ButtonHeight).then(fill),
    ) {
        ButtonContent(text, busy, icon)
    }
}

/** A secondary full-width action next to a [PrimaryButton]; same height and shape, outlined. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth().height(Dimens.ButtonHeight),
    ) {
        ButtonContent(text, busy, icon)
    }
}

@Composable
private fun ButtonContent(text: String, busy: Boolean, icon: ImageVector?) {
    if (busy) {
        val loading = stringResource(Res.string.state_loading)
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp).semantics { contentDescription = loading },
        )
        return
    }
    if (icon != null) {
        Icon(icon, contentDescription = null)
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    } else {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
