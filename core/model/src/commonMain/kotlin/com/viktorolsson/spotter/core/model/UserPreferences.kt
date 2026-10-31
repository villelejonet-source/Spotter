package com.viktorolsson.spotter.core.model

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 120,
    /** Which effort column (if any) to show when logging sets. */
    val effortScale: EffortScale = EffortScale.OFF,
    val onboardingCompleted: Boolean = false,
    /** Last day of an active deload week, if any. */
    val deloadUntil: kotlinx.datetime.LocalDate? = null,
    /** When the plan was last reviewed (applied or kept); the next review counts from here. */
    val planReviewedOn: kotlinx.datetime.LocalDate? = null,
    /** "Later" on the plan review: hidden through this day. */
    val planReviewSnoozedUntil: kotlinx.datetime.LocalDate? = null,
) {
    fun isDeload(today: kotlinx.datetime.LocalDate): Boolean = deloadUntil != null && today <= deloadUntil
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How set effort is entered and shown. Sets always store RIR; RPE is the same
 * rating on a 1–10 scale (RPE = 10 − RIR), converted at the edges.
 */
enum class EffortScale {
    OFF, RIR, RPE;

    /** Stored RIR as shown on this scale. RIR is shown as-is when the scale is [OFF]. */
    fun display(rir: Int): Int = if (this == RPE) (10 - rir).coerceIn(1, 10) else rir

    /** A value typed on this scale as RIR, or null when it's out of range. */
    fun toRir(value: Int): Int? = when (this) {
        RPE -> value.takeIf { it in 1..10 }?.let { 10 - it }
        else -> value.takeIf { it in 0..10 }
    }
}
