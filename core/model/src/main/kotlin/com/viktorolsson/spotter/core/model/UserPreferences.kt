package com.viktorolsson.spotter.core.model

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 120,
    /** Show the optional RIR (reps in reserve) column when logging sets. */
    val logRir: Boolean = false,
    val onboardingCompleted: Boolean = false,
    /** Last day of an active deload week, if any. */
    val deloadUntil: java.time.LocalDate? = null,
) {
    fun isDeload(today: java.time.LocalDate): Boolean = deloadUntil != null && !today.isAfter(deloadUntil)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
