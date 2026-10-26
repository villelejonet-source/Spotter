package com.viktorolsson.spotter.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.sync.AuthException
import com.viktorolsson.spotter.core.data.sync.AuthProblem
import com.viktorolsson.spotter.core.data.sync.CodePurpose
import com.viktorolsson.spotter.core.data.sync.SignUpResult
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Accounts are shared with the website: email + password, confirmed by an emailed code or
 * link. See docs/platform/auth-emails.md for the emails.
 */
@Serializable
data class AccountRoute(
    /** Opens on "create account" (from the welcome screen and the backup section). */
    val newAccount: Boolean = false,
    /** Opens on "choose a new password": from Profile, or after a password-reset link. */
    val newPassword: Boolean = false,
)

fun NavGraphBuilder.accountScreen(onDone: () -> Unit) {
    composable<AccountRoute> { AccountRoute(onDone = onDone) }
}

enum class AccountStep { SIGN_IN, SIGN_UP, FORGOT, CODE, NEW_PASSWORD, PASSWORD_SAVED }

const val MIN_PASSWORD_LENGTH = 8

data class AccountUiState(
    val step: AccountStep = AccountStep.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val repeat: String = "",
    val code: String = "",
    /** What the CODE step's code is for. */
    val codePurpose: CodePurpose = CodePurpose.SIGN_IN,
    /** The CODE step for a sign-in attempt on an unconfirmed account (worded differently). */
    val unconfirmed: Boolean = false,
    /** NEW_PASSWORD needs the emailed reauthentication code too. */
    val needsNonce: Boolean = false,
    val busy: Boolean = false,
    @param:StringRes val error: Int? = null,
    @param:StringRes val notice: Int? = null,
    /** Signed in: the screen closes. */
    val done: Boolean = false,
) {
    val emailValid: Boolean get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
}

class AccountViewModel(
    private val sync: SyncRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AccountRoute>()
    private val _state = MutableStateFlow(
        AccountUiState(
            step = when {
                route.newPassword -> AccountStep.NEW_PASSWORD
                route.newAccount -> AccountStep.SIGN_UP
                else -> AccountStep.SIGN_IN
            },
        ),
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        // Tapping a link in the email signs in from outside this screen. A sign-in or sign-up
        // is then finished; a password reset goes on to choosing the new password.
        viewModelScope.launch {
            sync.status.collect { status ->
                if (status.account == null) return@collect
                val s = _state.value
                when {
                    s.step == AccountStep.FORGOT || (s.step == AccountStep.CODE && s.codePurpose == CodePurpose.RECOVERY) ->
                        _state.update { it.copy(step = AccountStep.NEW_PASSWORD, busy = false, error = null, notice = null) }
                    s.step in setOf(AccountStep.SIGN_IN, AccountStep.SIGN_UP, AccountStep.CODE) -> _state.update { it.copy(busy = false, done = true) }
                    else -> Unit
                }
            }
        }
    }

    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setRepeat(value: String) = _state.update { it.copy(repeat = value, error = null) }
    fun setCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(10), error = null) }

    fun goTo(step: AccountStep) = _state.update { it.copy(step = step, error = null, notice = null, code = "", password = "", repeat = "") }

    fun signIn() = perform { s ->
        try {
            sync.signIn(s.email, s.password)
        } catch (e: AuthException) {
            if (e.problem != AuthProblem.EMAIL_NOT_CONFIRMED) throw e
            sync.resendConfirmation(s.email)
            toCode(CodePurpose.SIGN_UP, unconfirmed = true)
        }
    }

    fun signUp() = perform { s ->
        passwordProblem(s)?.let { problem -> return@perform _state.update { it.copy(error = problem) } }
        if (sync.signUp(s.email, s.password) == SignUpResult.CONFIRM_EMAIL) toCode(CodePurpose.SIGN_UP)
    }

    fun sendSignInCode() = perform { s ->
        sync.sendCode(s.email)
        toCode(CodePurpose.SIGN_IN)
    }

    fun sendReset() = perform { s ->
        sync.sendPasswordReset(s.email)
        toCode(CodePurpose.RECOVERY)
    }

    fun verify() = perform { s ->
        sync.verifyCode(s.email, s.code, s.codePurpose)
        if (s.codePurpose == CodePurpose.RECOVERY) {
            _state.update { it.copy(step = AccountStep.NEW_PASSWORD, code = "") }
        }
        // Otherwise the sign-in closes the screen (see init).
    }

    fun resend() = perform { s ->
        when (s.codePurpose) {
            CodePurpose.SIGN_UP -> sync.resendConfirmation(s.email)
            CodePurpose.SIGN_IN -> sync.sendCode(s.email)
            CodePurpose.RECOVERY -> sync.sendPasswordReset(s.email)
        }
        _state.update { it.copy(notice = R.string.account_resent) }
    }

    fun savePassword() = perform { s ->
        passwordProblem(s)?.let { problem -> return@perform _state.update { it.copy(error = problem) } }
        try {
            sync.updatePassword(s.password, s.code.takeIf { s.needsNonce })
            _state.update { it.copy(step = AccountStep.PASSWORD_SAVED, password = "", repeat = "", code = "", needsNonce = false) }
        } catch (e: AuthException) {
            if (e.problem != AuthProblem.REAUTHENTICATION_NEEDED) throw e
            _state.update { it.copy(needsNonce = true, code = "") }
        }
    }

    /** The screen was left: a reset link's "choose a password" prompt is answered or dropped. */
    fun leave() = sync.finishPasswordRecovery()

    private fun toCode(purpose: CodePurpose, unconfirmed: Boolean = false) =
        _state.update { it.copy(step = AccountStep.CODE, codePurpose = purpose, unconfirmed = unconfirmed, code = "", password = "", repeat = "") }

    private fun passwordProblem(s: AccountUiState): Int? = when {
        s.password.length < MIN_PASSWORD_LENGTH -> R.string.account_error_short_password
        s.password != s.repeat -> R.string.account_error_mismatch
        else -> null
    }

    /** Runs one account action with the busy flag on, turning failures into an error line. */
    private fun perform(block: suspend (AccountUiState) -> Unit) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null, notice = null) }
        try {
            block(_state.value)
            _state.update { it.copy(busy = false) }
        } catch (e: AuthException) {
            _state.update { it.copy(busy = false, error = e.problem.message()) }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = R.string.account_error_other) }
        }
    }
}

@StringRes
private fun AuthProblem.message(): Int = when (this) {
    AuthProblem.WRONG_CREDENTIALS -> R.string.account_error_wrong_credentials
    AuthProblem.ALREADY_REGISTERED -> R.string.account_error_already_registered
    AuthProblem.CODE_INVALID -> R.string.account_error_code
    AuthProblem.WEAK_PASSWORD -> R.string.account_error_weak_password
    AuthProblem.SAME_PASSWORD -> R.string.account_error_same_password
    AuthProblem.RATE_LIMITED -> R.string.account_error_rate_limited
    AuthProblem.INVALID_EMAIL -> R.string.account_error_invalid_email
    AuthProblem.OFFLINE -> R.string.account_error_offline
    AuthProblem.EMAIL_NOT_CONFIRMED, AuthProblem.REAUTHENTICATION_NEEDED, AuthProblem.OTHER -> R.string.account_error_other
}

@Composable
internal fun AccountRoute(onDone: () -> Unit, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leave = {
        viewModel.leave()
        onDone()
    }
    LaunchedEffect(state.done) { if (state.done) leave() }
    AccountScreen(
        state = state,
        onBack = leave,
        actions = AccountActions(
            setEmail = viewModel::setEmail,
            setPassword = viewModel::setPassword,
            setRepeat = viewModel::setRepeat,
            setCode = viewModel::setCode,
            goTo = viewModel::goTo,
            signIn = viewModel::signIn,
            signUp = viewModel::signUp,
            sendSignInCode = viewModel::sendSignInCode,
            sendReset = viewModel::sendReset,
            verify = viewModel::verify,
            resend = viewModel::resend,
            savePassword = viewModel::savePassword,
        ),
    )
}

internal class AccountActions(
    val setEmail: (String) -> Unit = {},
    val setPassword: (String) -> Unit = {},
    val setRepeat: (String) -> Unit = {},
    val setCode: (String) -> Unit = {},
    val goTo: (AccountStep) -> Unit = {},
    val signIn: () -> Unit = {},
    val signUp: () -> Unit = {},
    val sendSignInCode: () -> Unit = {},
    val sendReset: () -> Unit = {},
    val verify: () -> Unit = {},
    val resend: () -> Unit = {},
    val savePassword: () -> Unit = {},
)

@Composable
internal fun AccountScreen(state: AccountUiState, onBack: () -> Unit, actions: AccountActions) {
    val title = when (state.step) {
        AccountStep.SIGN_IN -> R.string.account_title
        AccountStep.SIGN_UP -> R.string.account_title_new
        AccountStep.FORGOT -> R.string.account_title_forgot
        AccountStep.CODE -> when (state.codePurpose) {
            CodePurpose.SIGN_UP -> R.string.account_title_new
            CodePurpose.SIGN_IN -> R.string.account_title
            CodePurpose.RECOVERY -> R.string.account_title_forgot
        }
        AccountStep.NEW_PASSWORD, AccountStep.PASSWORD_SAVED -> R.string.account_title_new_password
    }
    Scaffold(
        topBar = { SpotterTopBar(title = stringResource(title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Dimens.FocusedScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (state.step) {
                AccountStep.SIGN_IN -> SignInForm(state, actions)
                AccountStep.SIGN_UP -> SignUpForm(state, actions)
                AccountStep.FORGOT -> ForgotForm(state, actions)
                AccountStep.CODE -> CodeStep(state, actions)
                AccountStep.NEW_PASSWORD -> NewPasswordForm(state, actions)
                AccountStep.PASSWORD_SAVED -> PasswordSaved(onBack)
            }
            state.notice?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            state.error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.step in setOf(AccountStep.SIGN_IN, AccountStep.SIGN_UP)) {
                Text(
                    stringResource(R.string.account_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignInForm(state: AccountUiState, actions: AccountActions) {
    Text(stringResource(R.string.account_intro_sign_in), style = MaterialTheme.typography.bodyLarge)
    EmailField(state, actions)
    PasswordField(state.password, actions.setPassword, R.string.account_password, ImeAction.Done, onDone = actions.signIn)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { actions.goTo(AccountStep.FORGOT) }) { Text(stringResource(R.string.account_forgot)) }
    }
    ActionButton(R.string.account_sign_in, enabled = state.emailValid && state.password.isNotEmpty(), busy = state.busy, onClick = actions.signIn)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(stringResource(R.string.account_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
    SecondaryButton(stringResource(R.string.account_email_code), actions.sendSignInCode, enabled = state.emailValid && !state.busy)
    Text(stringResource(R.string.account_email_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { actions.goTo(AccountStep.SIGN_UP) }) { Text(stringResource(R.string.account_switch_to_new)) }
}

@Composable
private fun SignUpForm(state: AccountUiState, actions: AccountActions) {
    Text(stringResource(R.string.account_intro_new), style = MaterialTheme.typography.bodyLarge)
    EmailField(state, actions)
    PasswordField(state.password, actions.setPassword, R.string.account_password_new, ImeAction.Next, newPassword = true)
    PasswordField(state.repeat, actions.setRepeat, R.string.account_password_repeat, ImeAction.Done, newPassword = true, onDone = actions.signUp)
    ActionButton(
        R.string.account_create,
        enabled = state.emailValid && state.password.isNotEmpty() && state.repeat.isNotEmpty(),
        busy = state.busy,
        onClick = actions.signUp,
    )
    TextButton(onClick = { actions.goTo(AccountStep.SIGN_IN) }) { Text(stringResource(R.string.account_switch_to_sign_in)) }
}

@Composable
private fun ForgotForm(state: AccountUiState, actions: AccountActions) {
    Text(stringResource(R.string.account_intro_forgot), style = MaterialTheme.typography.bodyLarge)
    EmailField(state, actions, imeAction = ImeAction.Send, onDone = actions.sendReset)
    ActionButton(R.string.account_send_reset, enabled = state.emailValid, busy = state.busy, onClick = actions.sendReset)
    TextButton(onClick = { actions.goTo(AccountStep.SIGN_IN) }) { Text(stringResource(R.string.account_switch_to_sign_in)) }
}

@Composable
private fun CodeStep(state: AccountUiState, actions: AccountActions) {
    Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.IconLarge))
    Text(stringResource(R.string.account_check_email), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    val intro = when {
        state.unconfirmed -> R.string.account_code_intro_unconfirmed
        state.codePurpose == CodePurpose.SIGN_UP -> R.string.account_code_intro_sign_up
        state.codePurpose == CodePurpose.RECOVERY -> R.string.account_code_intro_recovery
        else -> R.string.account_code_intro_sign_in
    }
    Text(stringResource(intro, state.email), style = MaterialTheme.typography.bodyLarge)
    CodeField(state, actions, onDone = actions.verify)
    ActionButton(R.string.account_verify, enabled = state.code.length >= 6, busy = state.busy, onClick = actions.verify)
    val context = LocalContext.current
    SecondaryButton(stringResource(R.string.account_open_email), { openEmailApp(context) })
    TextButton(onClick = actions.resend, enabled = !state.busy) { Text(stringResource(R.string.account_resend)) }
    TextButton(
        onClick = { actions.goTo(if (state.codePurpose == CodePurpose.SIGN_UP && !state.unconfirmed) AccountStep.SIGN_UP else AccountStep.SIGN_IN) },
        enabled = !state.busy,
    ) { Text(stringResource(R.string.account_change_email)) }
}

@Composable
private fun NewPasswordForm(state: AccountUiState, actions: AccountActions) {
    Text(stringResource(R.string.account_new_password_intro), style = MaterialTheme.typography.bodyLarge)
    PasswordField(state.password, actions.setPassword, R.string.account_password_new, ImeAction.Next, newPassword = true)
    PasswordField(
        state.repeat,
        actions.setRepeat,
        R.string.account_password_repeat,
        if (state.needsNonce) ImeAction.Next else ImeAction.Done,
        newPassword = true,
        onDone = actions.savePassword,
    )
    if (state.needsNonce) {
        Text(stringResource(R.string.account_nonce_intro), style = MaterialTheme.typography.bodyMedium)
        CodeField(state, actions, onDone = actions.savePassword)
    }
    ActionButton(
        R.string.account_save_password,
        enabled = state.password.isNotEmpty() && state.repeat.isNotEmpty() && (!state.needsNonce || state.code.length >= 6),
        busy = state.busy,
        onClick = actions.savePassword,
    )
}

@Composable
private fun PasswordSaved(onDone: () -> Unit) {
    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.IconLarge))
    Text(stringResource(R.string.account_password_saved), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.account_password_saved_body), style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(stringResource(R.string.account_done), onDone)
}

@Composable
private fun EmailField(state: AccountUiState, actions: AccountActions, imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    OutlinedTextField(
        value = state.email,
        onValueChange = actions.setEmail,
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onSend = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    imeAction: ImeAction,
    newPassword: Boolean = false,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (visible) R.string.account_hide_password else R.string.account_show_password),
                )
            }
        },
        supportingText = if (newPassword && value.isNotEmpty() && value.length < MIN_PASSWORD_LENGTH) {
            { Text(stringResource(R.string.account_error_short_password)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeField(state: AccountUiState, actions: AccountActions, onDone: () -> Unit) {
    OutlinedTextField(
        value = state.code,
        onValueChange = actions.setCode,
        label = { Text(stringResource(R.string.account_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.code.length >= 6) onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
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
private fun ActionButton(@StringRes label: Int, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    PrimaryButton(text = stringResource(label), onClick = onClick, enabled = enabled, busy = busy)
}
