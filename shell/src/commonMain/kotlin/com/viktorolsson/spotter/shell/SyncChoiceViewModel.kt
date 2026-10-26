package com.viktorolsson.spotter.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.sync.FirstSyncChoice
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the first-sync dialog needs: whether to show, and whose account it's about. */
data class SyncChoiceUiState(
    val needsChoice: Boolean = false,
    val email: String? = null,
    /** Signed in from an emailed link into an account with no data: ask before uploading. */
    val fromLink: Boolean = false,
)

/** The first sync on a phone with its own data asks, wherever the user is, what to keep. */
class SyncChoiceViewModel(private val sync: SyncRepository) : ViewModel() {
    val uiState: StateFlow<SyncChoiceUiState> = sync.status
        .map { SyncChoiceUiState(needsChoice = it.needsChoice, email = it.account?.email, fromLink = it.fromLink) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncChoiceUiState())

    fun choose(choice: FirstSyncChoice) = viewModelScope.launch { sync.syncNow(choice) }

    /** Declines uploading this phone's data to a link-signed-in account. */
    fun signOut() = viewModelScope.launch { sync.signOut() }
}
