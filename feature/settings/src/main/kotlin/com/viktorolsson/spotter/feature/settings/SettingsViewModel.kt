package com.viktorolsson.spotter.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun syncNow() = viewModelScope.launch { syncRepository.syncNow() }

    fun signOut() = viewModelScope.launch { syncRepository.signOut() }

    fun deleteAccount() = viewModelScope.launch { runCatching { syncRepository.deleteAccount() } }

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

    fun setLogRir(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setLogRir(enabled) }
}
