package com.viktorolsson.spotter.feature.settings

import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.logWarn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.viktorolsson.spotter.feature.settings.resources.Res
import com.viktorolsson.spotter.feature.settings.resources.backup_delete_failed
import com.viktorolsson.spotter.feature.settings.resources.backup_sign_out_failed
import org.jetbrains.compose.resources.StringResource

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    planRepository: PlanRepository,
    private val syncRepository: SyncRepository,
    private val errors: AppErrors,
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = syncRepository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    /** A one-off message for the snackbar (an account action that failed); cleared with [messageShown]. */
    private val _message = MutableStateFlow<StringResource?>(null)
    val message: StateFlow<StringResource?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    fun syncNow() = viewModelScope.launchReporting(errors, Failure.ACTION) { syncRepository.syncNow() }

    fun signOut() = attempt(Res.string.backup_sign_out_failed) { syncRepository.signOut() }

    fun deleteAccount() = attempt(Res.string.backup_delete_failed) { syncRepository.deleteAccount() }

    private fun attempt(failure: StringResource, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarn("Settings", "Account action failed: ${e::class.simpleName}")
            _message.value = failure
        }
    }

    /** Name of the active plan, or null when there is none. */
    val planName: StateFlow<String?> = planRepository.observeActivePlan()
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launchReporting(errors) { preferencesRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launchReporting(errors) { preferencesRepository.setDynamicColor(enabled) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launchReporting(errors) { preferencesRepository.setWeightUnit(unit) }

    fun setDefaultRest(seconds: Int) = viewModelScope.launchReporting(errors) { preferencesRepository.setDefaultRestSeconds(seconds) }

    fun setEffortScale(scale: EffortScale) = viewModelScope.launchReporting(errors) { preferencesRepository.setEffortScale(scale) }
}
