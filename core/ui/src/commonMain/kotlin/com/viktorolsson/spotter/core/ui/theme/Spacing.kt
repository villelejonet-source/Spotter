package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.ui.unit.dp

/** The spacing scale. Every padding and gap in the app is one of these. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Named sizes built from [Spacing], so screens agree on the same measurements. */
object Dimens {
    /** Horizontal and vertical inset of a scrolling screen's content. */
    val ScreenPadding = Spacing.lg

    /**
     * Onboarding, the welcome screen and the account screens keep a wider inset on
     * purpose: one question or one form at a time reads better with more air around it.
     */
    val FocusedScreenPadding = Spacing.xl

    /** Inside a content card. */
    val CardPadding = Spacing.lg

    /** Inside a small stat tile, where three sit side by side. */
    val TilePadding = Spacing.md

    /** Inside a bottom sheet. */
    val SheetPadding = Spacing.xl

    /** Gap between cards and rows in a list. */
    val ListGap = Spacing.md

    /** Gap between sections of a screen. */
    val SectionGap = Spacing.lg

    /** Full-width primary and secondary action buttons. */
    val ButtonHeight = 56.dp

    /** Keys on the in-app keypad. */
    val KeypadKeyHeight = 52.dp

    /** A set row and the tappable fields inside it. */
    val SetRowHeight = 52.dp
    val FieldHeight = 44.dp

    /** Inside a small tag such as the superset badge. */
    val BadgePaddingHorizontal = 6.dp

    /** Bottom clearance for a list scrolling under an extended floating action button. */
    val FabClearance = 96.dp

    /** Distance from the top of the screen to an overlay banner (the PR celebration). */
    val OverlayTopOffset = 96.dp

    /** Elevation of a banner floating over content. */
    val BannerElevation = 6.dp

    /** The primary button's glow. */
    val GlowElevation = 14.dp

    /** Flat surfaces: no tonal elevation. */
    val None = 0.dp

    /** Card borders and dividers. */
    val Hairline = 1.dp

    /** Minimum touch target. */
    val TouchTarget = 48.dp

    val IconSmall = 16.dp
    val IconMedium = 24.dp
    val IconBanner = 36.dp
    val IconLarge = 56.dp
}
