package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** A password-reset link opened the app, wherever the user was: ask for the new password. */
@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(sync: SyncRepository) : ViewModel() {
    val recovering: StateFlow<Boolean> = sync.recoveringPassword
}
