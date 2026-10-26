package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: UserPreferences) : MainUiState
}

class MainViewModel(
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = preferencesRepository.preferences
        .map<UserPreferences, MainUiState>(MainUiState::Ready)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState.Loading)
}
