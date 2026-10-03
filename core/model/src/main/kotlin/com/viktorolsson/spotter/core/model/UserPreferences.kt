package com.viktorolsson.spotter.core.model

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 120,
    val onboardingCompleted: Boolean = false,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
