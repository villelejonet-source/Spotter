package com.viktorolsson.spotter.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.geist_bold
import com.viktorolsson.spotter.core.ui.resources.geist_medium
import com.viktorolsson.spotter.core.ui.resources.geist_mono_medium
import com.viktorolsson.spotter.core.ui.resources.geist_mono_regular
import com.viktorolsson.spotter.core.ui.resources.geist_mono_semibold
import com.viktorolsson.spotter.core.ui.resources.geist_regular
import com.viktorolsson.spotter.core.ui.resources.geist_semibold
import org.jetbrains.compose.resources.Font

// Geist and Geist Mono, the website's fonts (SIL Open Font License, docs/licenses/Geist-OFL.txt).
// Text is Geist; every number someone reads or compares (weights, reps, times, stats) is Geist
// Mono, so digits line up in columns and don't shift while a timer runs.

@Composable
private fun geist() = FontFamily(
    Font(Res.font.geist_regular, FontWeight.Normal),
    Font(Res.font.geist_medium, FontWeight.Medium),
    Font(Res.font.geist_semibold, FontWeight.SemiBold),
    Font(Res.font.geist_bold, FontWeight.Bold),
)

@Composable
private fun geistMono() = FontFamily(
    Font(Res.font.geist_mono_regular, FontWeight.Normal),
    Font(Res.font.geist_mono_medium, FontWeight.Medium),
    Font(Res.font.geist_mono_semibold, FontWeight.SemiBold),
)

/** Material's scale in Geist, with tighter, heavier headings. */
@Composable
internal fun spotterTypography(): Typography {
    val sans = geist()
    val base = Typography()
    fun TextStyle.geist(weight: FontWeight? = null, tracking: Double? = null) = copy(
        fontFamily = sans,
        fontWeight = weight ?: fontWeight,
        letterSpacing = tracking?.em ?: letterSpacing,
    )
    return Typography(
        displayLarge = base.displayLarge.geist(FontWeight.SemiBold, -0.03),
        displayMedium = base.displayMedium.geist(FontWeight.SemiBold, -0.03),
        displaySmall = base.displaySmall.geist(FontWeight.SemiBold, -0.025),
        headlineLarge = base.headlineLarge.geist(FontWeight.SemiBold, -0.025),
        headlineMedium = base.headlineMedium.geist(FontWeight.SemiBold, -0.02),
        headlineSmall = base.headlineSmall.geist(FontWeight.SemiBold, -0.02),
        titleLarge = base.titleLarge.geist(FontWeight.SemiBold, -0.015),
        titleMedium = base.titleMedium.geist(FontWeight.SemiBold, -0.01),
        titleSmall = base.titleSmall.geist(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.geist(),
        bodyMedium = base.bodyMedium.geist(),
        bodySmall = base.bodySmall.geist(),
        labelLarge = base.labelLarge.geist(FontWeight.SemiBold),
        labelMedium = base.labelMedium.geist(),
        labelSmall = base.labelSmall.geist(),
    )
}

/** Monospace styles for numbers, and the small uppercase label the website uses for card titles. */
@Immutable
data class SpotterTypography(
    /** The headline number of a stat tile or the rest timer. */
    val numberLarge: TextStyle,
    /** A stat in a row of three, a set's weight and reps. */
    val numberMedium: TextStyle,
    /** Numbers inside body text and lists. */
    val numberSmall: TextStyle,
    /** Card titles, stat labels, eyebrows: small, uppercase, tracked out. Uppercase the text yourself. */
    val label: TextStyle,
)

@Composable
internal fun spotterNumberTypography(): SpotterTypography {
    val mono = geistMono()
    return SpotterTypography(
        numberLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
        numberMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
        numberSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        label = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.12.em),
    )
}

internal val LocalSpotterTypography = staticCompositionLocalOf {
    SpotterTypography(TextStyle.Default, TextStyle.Default, TextStyle.Default, TextStyle.Default)
}
