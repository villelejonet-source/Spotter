package com.viktorolsson.spotter.core.data.sync

import android.content.Intent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Project URL and publishable key; blank when cloud sync isn't configured for this build. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()
}

/** The Supabase client, created on first use and only if the build is configured. */
@Singleton
class SupabaseProvider @Inject constructor(private val config: SupabaseConfig) {
    val client: SupabaseClient? by lazy {
        if (!config.isConfigured) return@lazy null
        createSupabaseClient(config.url, config.publishableKey) {
            install(Auth) {
                // Sign-in links in emails open the app at spotter://login (must be listed under
                // Authentication → URL Configuration → Redirect URLs in Supabase).
                scheme = DEEP_LINK_SCHEME
                host = DEEP_LINK_HOST
            }
            install(Postgrest)
        }
    }
}

const val DEEP_LINK_SCHEME = "spotter"
const val DEEP_LINK_HOST = "login"
private const val REDIRECT_URL = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST"

data class Account(val userId: String, val email: String?)

/** Why a sign-in, sign-up or password request failed, in terms the screens can explain. */
enum class AuthProblem {
    WRONG_CREDENTIALS, EMAIL_NOT_CONFIRMED, ALREADY_REGISTERED, CODE_INVALID, WEAK_PASSWORD, SAME_PASSWORD,
    REAUTHENTICATION_NEEDED, RATE_LIMITED, INVALID_EMAIL, OFFLINE, OTHER,
}

class AuthException(val problem: AuthProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** What the email code is for; each matches a Supabase email template. */
enum class CodePurpose { SIGN_IN, SIGN_UP, RECOVERY }

enum class SignUpResult {
    /** The confirmation email is on its way; the code (or its link) finishes sign-up. */
    CONFIRM_EMAIL,
    /** The project doesn't require confirmation: already signed in. */
    SIGNED_IN,
}

/**
 * Accounts are email + password, shared with the website. Every email (confirm sign-up,
 * sign-in code, password reset) has a link that opens the app (see [handleDeeplink]) and
 * a code that can be typed in instead (docs/platform/auth-emails.md). Accounts made before
 * passwords existed sign in with a code, and can set a password with "forgot password".
 */
@Singleton
class AccountService @Inject constructor(private val provider: SupabaseProvider) {
    val isAvailable: Boolean get() = provider.client != null

    /** The signed-in account, or null; also null while the session is restored on start. */
    val account: Flow<Account?> = provider.client?.auth?.sessionStatus?.map { status ->
        (status as? SessionStatus.Authenticated)?.session?.user?.let { Account(it.id, it.email) }
    } ?: flowOf(null)

    fun currentAccount(): Account? = provider.client?.auth?.currentUserOrNull()?.let { Account(it.id, it.email) }

    /** Set when a password-reset link opened the app: the user is signed in to choose a new one. */
    private val _recovering = MutableStateFlow(false)
    val recovering: StateFlow<Boolean> = _recovering.asStateFlow()

    fun finishRecovery() = _recovering.update { false }

    suspend fun signUp(email: String, password: String): SignUpResult = authCall {
        val user = requireClient().auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
        // With confirmation on, Supabase hides whether the address is taken: an existing
        // account comes back without identities, and no email is sent.
        if (user != null && user.identities?.isEmpty() == true) throw AuthException(AuthProblem.ALREADY_REGISTERED)
        if (currentAccount() != null) SignUpResult.SIGNED_IN else SignUpResult.CONFIRM_EMAIL
    }

    suspend fun signIn(email: String, password: String) = authCall {
        requireClient().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Emails a sign-in code (and link) to an existing account. New accounts are made with
     * [signUp]. An unknown address is treated as sent, so this doesn't reveal who has one.
     */
    suspend fun sendCode(email: String) = authCall {
        try {
            requireClient().auth.signInWith(OTP, redirectUrl = REDIRECT_URL) {
                this.email = email.trim()
                createUser = false
            }
        } catch (e: AuthRestException) {
            if (e.errorCode != AuthErrorCode.OtpDisabled) throw e
        }
    }

    suspend fun resendConfirmation(email: String) = authCall {
        requireClient().auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    suspend fun sendPasswordReset(email: String) = authCall {
        requireClient().auth.resetPasswordForEmail(email.trim(), redirectUrl = REDIRECT_URL)
    }

    suspend fun verifyCode(email: String, code: String, purpose: CodePurpose = CodePurpose.SIGN_IN): Unit = authCall {
        val type = when (purpose) {
            CodePurpose.SIGN_IN -> OtpType.Email.EMAIL
            CodePurpose.SIGN_UP -> OtpType.Email.SIGNUP
            CodePurpose.RECOVERY -> OtpType.Email.RECOVERY
        }
        requireClient().auth.verifyEmailOtp(type = type, email = email.trim(), token = code.trim())
        Unit
    }

    /**
     * Sets the signed-in user's password. When the project asks for a recent sign-in
     * ("secure password change"), this fails with REAUTHENTICATION_NEEDED after emailing a
     * code; call again with that code as [nonce].
     */
    suspend fun updatePassword(password: String, nonce: String? = null) = authCall {
        try {
            requireClient().auth.updateUser {
                this.password = password
                if (nonce != null) this.nonce = nonce.trim()
            }
            _recovering.update { false }
        } catch (e: AuthRestException) {
            if (e.errorCode == AuthErrorCode.ReauthenticationNeeded && nonce == null) {
                requireClient().auth.reauthenticate()
                throw AuthException(AuthProblem.REAUTHENTICATION_NEEDED, e)
            }
            throw e
        }
    }

    /** Runs a Supabase auth call, turning its errors into [AuthException]s. */
    private suspend fun <T> authCall(block: suspend () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: AuthRestException) {
        throw AuthException(e.errorCode.toProblem(), e)
    } catch (e: HttpRequestException) {
        throw AuthException(AuthProblem.OFFLINE, e)
    } catch (e: IOException) {
        throw AuthException(AuthProblem.OFFLINE, e)
    }

    private fun AuthErrorCode?.toProblem() = when (this) {
        AuthErrorCode.InvalidCredentials -> AuthProblem.WRONG_CREDENTIALS
        AuthErrorCode.EmailNotConfirmed -> AuthProblem.EMAIL_NOT_CONFIRMED
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> AuthProblem.ALREADY_REGISTERED
        AuthErrorCode.OtpExpired -> AuthProblem.CODE_INVALID
        AuthErrorCode.WeakPassword -> AuthProblem.WEAK_PASSWORD
        AuthErrorCode.SamePassword -> AuthProblem.SAME_PASSWORD
        AuthErrorCode.ReauthenticationNeeded -> AuthProblem.REAUTHENTICATION_NEEDED
        AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> AuthProblem.RATE_LIMITED
        AuthErrorCode.EmailAddressInvalid -> AuthProblem.INVALID_EMAIL
        else -> AuthProblem.OTHER
    }

    /**
     * Completes sign-in from a tapped email link (spotter://login#...); ignores other intents.
     * A password-reset link also flags [recovering], so the app asks for a new password.
     */
    fun handleDeeplink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != DEEP_LINK_SCHEME || data.host != DEEP_LINK_HOST) return
        val recovery = "type=recovery" in (data.fragment.orEmpty() + "&" + data.query.orEmpty())
        provider.client?.handleDeeplinks(intent, onSessionSuccess = { if (recovery) _recovering.update { true } })
    }

    suspend fun signOut() {
        provider.client?.auth?.signOut()
    }

    /** Deletes the account and, server-side, every backed-up row. Local data stays. */
    suspend fun deleteAccount() {
        requireClient().postgrest.rpc("delete_my_account")
        signOut()
    }

    private fun requireClient() = provider.client ?: error("Cloud sync isn't configured in this build")
}

/** [SyncRemote] over the `sync_rows` table and `push_rows` function (see supabase/migrations/20261004000000_sync.sql). */
@Singleton
class SupabaseSyncRemote @Inject constructor(private val provider: SupabaseProvider) : SyncRemote {
    private val client get() = provider.client ?: error("Cloud sync isn't configured in this build")

    @Serializable
    private data class IdOnly(@SerialName("sync_id") val syncId: String)

    override suspend fun hasRows(): Boolean =
        client.from(TABLE).select(Columns.list("sync_id")) { limit(1) }.decodeList<IdOnly>().isNotEmpty()

    override suspend fun pull(since: String?, offset: Int, limit: Int): List<RemoteRow> =
        client.from(TABLE).select {
            if (since != null) filter { gt("server_updated_at", since) }
            order("server_updated_at", Order.ASCENDING)
            order("sync_id", Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<RemoteRow>()

    override suspend fun push(rows: List<SyncRow>) {
        client.postgrest.rpc("push_rows", buildJsonObject { put("rows", json.encodeToJsonElement(rows)) })
    }

    private val json = Json { encodeDefaults = true }

    private companion object {
        const val TABLE = "sync_rows"
    }
}
