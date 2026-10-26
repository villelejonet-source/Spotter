package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import kotlinx.coroutines.flow.StateFlow

/** A password-reset link opened the app, wherever the user was: ask for the new password. */
class PasswordRecoveryViewModel(sync: SyncRepository) : ViewModel() {
    val recovering: StateFlow<Boolean> = sync.recoveringPassword
}
