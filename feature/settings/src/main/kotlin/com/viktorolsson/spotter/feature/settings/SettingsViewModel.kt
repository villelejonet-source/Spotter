package com.viktorolsson.spotter.feature.settings

import android.util.Log
import androidx.annotation.StringRes
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    planRepository: PlanRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = syncRepository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    /** A one-off message for the snackbar (an account action that failed); cleared with [messageShown]. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    fun syncNow() = viewModelScope.launch { syncRepository.syncNow() }

    fun signOut() = attempt(R.string.backup_sign_out_failed) { syncRepository.signOut() }

    fun deleteAccount() = attempt(R.string.backup_delete_failed) { syncRepository.deleteAccount() }

    private fun attempt(@StringRes failure: Int, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Settings", "Account action failed: ${e.javaClass.simpleName}")
            _message.value = failure
        }
    }

    /** Name of the active plan, or null when there is none. */
    val planName: StateFlow<String?> = planRepository.observeActivePlan()
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { preferencesRepository.setWeightUnit(unit) }

    fun setDefaultRest(seconds: Int) = viewModelScope.launch { preferencesRepository.setDefaultRestSeconds(seconds) }

    fun setEffortScale(scale: EffortScale) = viewModelScope.launch { preferencesRepository.setEffortScale(scale) }
}
