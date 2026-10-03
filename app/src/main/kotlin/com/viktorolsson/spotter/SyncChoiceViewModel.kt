package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The first sync on a phone with its own data asks, wherever the user is, what to keep. */
@HiltViewModel
class SyncChoiceViewModel @Inject constructor(private val sync: SyncRepository) : ViewModel() {
    val needsChoice: StateFlow<Boolean> = sync.status.map { it.needsChoice }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun choose(choice: FirstSyncChoice) = viewModelScope.launch { sync.syncNow(choice) }
}
