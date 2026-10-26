package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii, Material 3's scale made explicit so components reach for
 * `MaterialTheme.shapes.*` instead of their own `RoundedCornerShape`.
 *
 * - extraSmall: badges and chips inside text
 * - small: set fields, calendar cells, menu items
 * - medium: cards
 * - large: option cards and sheets
 */
val SpotterShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
