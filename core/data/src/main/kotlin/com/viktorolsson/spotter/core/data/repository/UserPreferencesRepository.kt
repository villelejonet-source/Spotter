package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val defaults = UserPreferences()

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE].toEnumOr(defaults.themeMode),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            weightUnit = prefs[Keys.WEIGHT_UNIT].toEnumOr(defaults.weightUnit),
            defaultRestSeconds = prefs[Keys.DEFAULT_REST_SECONDS] ?: defaults.defaultRestSeconds,
            logRir = prefs[Keys.LOG_RIR] ?: defaults.logRir,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { it[Keys.DEFAULT_REST_SECONDS] = seconds }
    }

    suspend fun setLogRir(enabled: Boolean) {
        dataStore.edit { it[Keys.LOG_RIR] = enabled }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = done }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val LOG_RIR = booleanPreferencesKey("log_rir")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
