package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.viktorolsson.spotter.core.data.db.dao.UserProfileDao
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val profileDao: UserProfileDao,
) {
    private val defaults = UserPreferences()

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE].toEnumOr(defaults.themeMode),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            weightUnit = prefs[Keys.WEIGHT_UNIT].toEnumOr(defaults.weightUnit),
            defaultRestSeconds = prefs[Keys.DEFAULT_REST_SECONDS] ?: defaults.defaultRestSeconds,
            // Before the effort scale existed, a "Log RIR" switch meant the RIR scale.
            effortScale = prefs[Keys.EFFORT_SCALE].toEnumOr(
                if (prefs[Keys.LOG_RIR] == true) EffortScale.RIR else defaults.effortScale,
            ),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            deloadUntil = prefs[Keys.DELOAD_UNTIL]?.let(LocalDate::ofEpochDay),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /**
     * The unit preference and the synced profile's `units` are one setting: changing it here
     * also updates the profile (the sync trigger stamps it, so the website and other phones
     * follow), and a profile pulled by sync is mirrored back here by [SyncRepository].
     */
    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
        profileDao.get()?.takeIf { it.units != unit }?.let { profileDao.upsert(it.copy(units = unit)) }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { it[Keys.DEFAULT_REST_SECONDS] = seconds }
    }

    suspend fun setEffortScale(scale: EffortScale) {
        dataStore.edit { it[Keys.EFFORT_SCALE] = scale.name }
    }

    suspend fun setDeloadUntil(date: LocalDate?) {
        dataStore.edit { if (date == null) it.remove(Keys.DELOAD_UNTIL) else it[Keys.DELOAD_UNTIL] = date.toEpochDay() }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = done }
    }

    /** The current unit preference, for callers that only need it once. */
    suspend fun weightUnit(): WeightUnit = preferences.first().weightUnit

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val LOG_RIR = booleanPreferencesKey("log_rir")
        val EFFORT_SCALE = stringPreferencesKey("effort_scale")
        val DELOAD_UNTIL = longPreferencesKey("deload_until_epoch_day")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
