package com.viktorolsson.spotter.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class AccountRoute(
    /** Worded for creating an account (from the welcome screen) instead of signing in. */
    val newAccount: Boolean = false,
)

fun NavGraphBuilder.accountScreen(onDone: () -> Unit) {
    composable<AccountRoute> { entry -> AccountRoute(newAccount = entry.toRoute<AccountRoute>().newAccount, onDone = onDone) }
}

data class AccountUiState(
    val email: String = "",
    val code: String = "",
    /** True once a code has been sent to [email]. */
    val codeSent: Boolean = false,
    val busy: Boolean = false,
    val error: Int? = null,
    val signedIn: Boolean = false,
    /** The code field is hidden until asked for: the email's link is the main way in. */
    val showCode: Boolean = false,
) {
    val emailValid: Boolean get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
}

@HiltViewModel
class AccountViewModel @Inject constructor(private val sync: SyncRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        // Tapping the link in the email signs in from outside this screen.
        viewModelScope.launch {
            sync.status.first { it.account != null }
            _state.update { it.copy(busy = false, signedIn = true) }
        }
    }

    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), error = null) }

    fun setCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun sendCode() = viewModelScope.launch {
        if (!_state.value.emailValid) return@launch
        _state.update { it.copy(busy = true, error = null) }
        runCatching { sync.sendCode(_state.value.email) }
            .onSuccess { _state.update { it.copy(busy = false, codeSent = true, code = "") } }
            .onFailure { _state.update { it.copy(busy = false, error = R.string.account_send_failed) } }
    }

    fun verify() = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        runCatching { sync.verifyCode(_state.value.email, _state.value.code) }
            .onSuccess { _state.update { it.copy(busy = false, signedIn = true) } }
            .onFailure { _state.update { it.copy(busy = false, error = R.string.account_verify_failed) } }
    }

    fun showCode() = _state.update { it.copy(showCode = true) }

    fun changeEmail() = _state.update { it.copy(codeSent = false, showCode = false, code = "", error = null) }
}


@Composable
internal fun AccountRoute(newAccount: Boolean, onDone: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onDone() }
    AccountScreen(
        state = state,
        newAccount = newAccount,
        onBack = onDone,
        actions = AccountActions(
            setEmail = viewModel::setEmail,
            sendCode = viewModel::sendCode,
            setCode = viewModel::setCode,
            verify = viewModel::verify,
            showCode = viewModel::showCode,
            changeEmail = viewModel::changeEmail,
        ),
    )
}

internal class AccountActions(
    val setEmail: (String) -> Unit,
    val sendCode: () -> Unit,
    val setCode: (String) -> Unit,
    val verify: () -> Unit,
    val showCode: () -> Unit,
    val changeEmail: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(state: AccountUiState, newAccount: Boolean, onBack: () -> Unit, actions: AccountActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (newAccount) R.string.account_title_new else R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.codeSent) {
                Text(stringResource(if (newAccount) R.string.account_email_intro_new else R.string.account_email_intro), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = state.email,
                    onValueChange = actions.setEmail,
                    label = { Text(stringResource(R.string.account_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionButton(R.string.account_send_code, enabled = state.emailValid, busy = state.busy, onClick = actions.sendCode)
            } else {
                // Supabase's email always has the sign-in link; the code only when the project's
                // template includes it, so the code field stays secondary.
                Icon(
                    Icons.Rounded.MarkEmailRead,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    stringResource(R.string.account_check_email),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.account_code_intro, state.email), style = MaterialTheme.typography.bodyLarge)
                val context = LocalContext.current
                Button(onClick = { openEmailApp(context) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.account_open_email))
                }
                if (state.showCode) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = actions.setCode,
                        label = { Text(stringResource(R.string.account_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(R.string.account_verify, enabled = state.code.length == 6, busy = state.busy, onClick = actions.verify)
                } else {
                    TextButton(onClick = actions.showCode) { Text(stringResource(R.string.account_enter_code)) }
                }
                TextButton(onClick = actions.sendCode, enabled = !state.busy) { Text(stringResource(R.string.account_resend)) }
                TextButton(onClick = actions.changeEmail, enabled = !state.busy) { Text(stringResource(R.string.account_change_email)) }
            }
            state.error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Text(
                stringResource(R.string.account_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Opens the phone's email app on its inbox; does nothing if there isn't one. */
private fun openEmailApp(context: Context) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun ActionButton(label: Int, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp)) else Text(stringResource(label))
    }
}
