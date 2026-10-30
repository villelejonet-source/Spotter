package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.viktorolsson.spotter.core.ui.theme.Dimens

/**
 * The bottom tab bar: the card surface under a hairline, the active tab in the accent on
 * its tint. Items use [spotterNavigationItemColors].
 */
@Composable
fun SpotterNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Dimens.None,
        modifier = modifier.drawBehind {
            drawLine(hairline, Offset.Zero, Offset(size.width, 0f), Dimens.Hairline.toPx())
        },
        content = content,
    )
}

@Composable
fun spotterNavigationItemColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
