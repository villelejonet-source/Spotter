package com.viktorolsson.spotter.core.data.sync

import com.russhwolf.settings.Settings
import com.viktorolsson.spotter.core.data.logWarn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/** Project URL and publishable key; blank when cloud sync isn't configured for this build. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()
}

/**
 * Where the auth session and PKCE verifier are kept: Android's EncryptedSharedPreferences
 * (`AuthStorage` in :core:data-android), the Keychain on iOS. Null when the store can't be
 * opened; the session is then kept in memory for this process.
 */
fun interface SessionStore {
    fun open(): Settings?
}

/**
 * The Supabase client, built once, off the main thread, on first use, and only if the
 * build is configured. Building it opens the session store (a Keystore or Keychain
 * operation), so nothing at start-up should touch it from the UI thread.
 */
class SupabaseProvider(
    private val config: SupabaseConfig,
    private val sessionStore: SessionStore,
    scope: CoroutineScope,
) {
    val isConfigured: Boolean get() = config.isConfigured

    private val deferred: Deferred<SupabaseClient?> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        if (!config.isConfigured) return@async null
        // Null when the encrypted store can't be opened even after a reset: keep the
        // session in memory for this process instead of failing every account flow.
        val secure = sessionStore.open()
        createSupabaseClient(config.url, config.publishableKey) {
            install(Auth) {
                // Sign-in links in emails open the app at spotter://login (must be listed under
                // Authentication → URL Configuration → Redirect URLs in Supabase).
                scheme = DEEP_LINK_SCHEME
                host = DEEP_LINK_HOST
                // PKCE: the link carries a one-time ?code= that only this phone can exchange
                // (the verifier never leaves the device), and no tokens travel in the URL.
                flowType = FlowType.PKCE
                sessionManager = secure?.let(::SettingsSessionManager) ?: MemorySessionManager()
                codeVerifierCache = secure?.let(::SettingsCodeVerifierCache) ?: MemoryCodeVerifierCache()
            }
            install(Postgrest)
        }
    }

    /** The client, or null when sync isn't configured. Suspends while it's first built. */
    suspend fun client(): SupabaseClient? = deferred.await()

    /** The client if it has already been built; never triggers the build. */
    fun clientOrNull(): SupabaseClient? = if (deferred.isCompleted && !deferred.isCancelled) deferred.getCompleted() else null
}

const val DEEP_LINK_SCHEME = "spotter"
const val DEEP_LINK_HOST = "login"
private const val REDIRECT_URL = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST"

data class Account(val userId: String, val email: String?)

/** A sign-in link the app will act on: `spotter://login?code=…`, with the PKCE code to exchange. */
data class SignInLink(val code: String, val recovery: Boolean)

/**
 * Parses the URIs the app is opened with. Only our scheme and host with a `code` query
 * parameter count; a URI carrying tokens in its fragment (`#access_token=…`, the implicit
 * flow) is ignored, so a crafted link can never sign the phone into an account.
 */
object SignInLinks {
    /** scheme://authority path ?query #fragment. An opaque URI (`spotter:login`) has no `//`. */
    private val HIERARCHICAL = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)[^?#]*(?:\?([^#]*))?(?:#.*)?$""")

    fun parse(url: String?): SignInLink? {
        val match = url?.let(HIERARCHICAL::matchEntire) ?: return null
        val (scheme, authority, query) = match.destructured
        val host = authority.substringAfterLast('@').substringBefore(':')
        if (scheme != DEEP_LINK_SCHEME || host != DEEP_LINK_HOST) return null
        val params = query.split('&').filter { it.isNotEmpty() }
            .map { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }
        val code = params.firstOrNull { it.first == "code" }?.second?.takeIf { it.isNotBlank() } ?: return null
        return SignInLink(code, recovery = params.firstOrNull { it.first == "type" }?.second == "recovery")
    }

    /** Percent-decoding with `+` as a space, as Android's `Uri.getQueryParameter` does. */
    private fun decode(value: String): String {
        val out = ByteArray(value.length * 4)
        var size = 0
        var i = 0
        while (i < value.length) {
            val hex = if (value[i] == '%' && i + 3 <= value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            when {
                hex != null -> { out[size++] = hex.toByte(); i += 3 }
                value[i] == '+' -> { out[size++] = ' '.code.toByte(); i++ }
                else -> { value[i].toString().encodeToByteArray().forEach { out[size++] = it }; i++ }
            }
        }
        return out.copyOf(size).decodeToString()
    }
}

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
class AccountService(
    private val provider: SupabaseProvider,
    private val scope: CoroutineScope,
) {
    val isAvailable: Boolean get() = provider.isConfigured

    /** The signed-in account, or null; also null while the session is restored on start. */
    val account: Flow<Account?> = flow {
        val client = provider.client() ?: return@flow emit(null)
        emitAll(client.auth.sessionStatus.map { status -> (status as? SessionStatus.Authenticated)?.session?.toAccount() })
    }

    /** The account if the client is up and signed in; null while it's still being built. */
    fun currentAccount(): Account? = provider.clientOrNull()?.auth?.currentSessionOrNull()?.toAccount()

    /** Like [currentAccount], waiting for the client to be built first. */
    suspend fun awaitAccount(): Account? = provider.client()?.auth?.currentSessionOrNull()?.toAccount()

    private fun UserSession.toAccount() = user?.let { Account(it.id, it.email) }

    /** Set when a password-reset link opened the app: the user is signed in to choose a new one. */
    private val _recovering = MutableStateFlow(false)
    val recovering: StateFlow<Boolean> = _recovering.asStateFlow()

    fun finishRecovery() = _recovering.update { false }

    /**
     * True from the moment a sign-in link is being exchanged until the first sync after
     * it finishes (or the exchange fails). [SyncRepository] uses it to ask before
     * uploading this phone's data to an account that has none.
     */
    @Volatile
    var signedInFromLink: Boolean = false

    suspend fun signUp(email: String, password: String): SignUpResult = authCall {
        val user = requireClient().auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
        // With confirmation on, Supabase hides whether the address is taken: an existing
        // account comes back without identities, and no email is sent.
        if (user != null && user.identities?.isEmpty() == true) throw AuthException(AuthProblem.ALREADY_REGISTERED)
        if (awaitAccount() != null) SignUpResult.SIGNED_IN else SignUpResult.CONFIRM_EMAIL
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
     * Completes sign-in from a tapped email link (`spotter://login?code=…`, the URL the app
     * was opened with); anything else, including a link that only carries tokens in its
     * fragment, is ignored. The PKCE code is exchanged for a session off the main thread, as
     * supabase-kt's Android `handleDeeplinks` does. A password-reset link also flags
     * [recovering], so the app asks for a new password.
     */
    fun handleDeeplink(url: String?) {
        val link = SignInLinks.parse(url) ?: return
        signedInFromLink = true
        scope.launch {
            val client = provider.client() ?: return@launch
            try {
                val session = client.auth.exchangeCodeForSession(link.code)
                if (link.recovery || session.isRecovery()) _recovering.update { true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                signedInFromLink = false
                logWarn(TAG, "Sign-in link failed: ${e::class.simpleName}")
            }
        }
    }

    suspend fun signOut() {
        provider.client()?.auth?.signOut()
        signedInFromLink = false
    }

    /** Deletes the account and, server-side, every backed-up row. Local data stays. */
    suspend fun deleteAccount() {
        requireClient().postgrest.rpc("delete_my_account")
        signOut()
    }

    private suspend fun requireClient() = provider.client() ?: error("Cloud sync isn't configured in this build")

    private companion object {
        const val TAG = "SpotterAuth"
    }
}

/**
 * Whether the session was issued by a password-reset link: with PKCE the redirect carries
 * no `type`, but the access token's `amr` claim records the method ("recovery").
 */
internal fun UserSession.isRecovery(): Boolean = type == "recovery" || runCatching {
    val payload = accessToken.split('.').getOrNull(1) ?: return false
    val claims = Json.parseToJsonElement(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()).jsonObject
    claims["amr"]?.jsonArray?.any { (it as? JsonObject)?.get("method")?.jsonPrimitive?.content == "recovery" } == true
}.getOrDefault(false)

/**
 * [SyncRemote] over the `sync_rows` table and the `push_rows` and `check_in_device` functions
 * (see supabase/migrations/).
 * Reads filter on `user_id` explicitly (see [SyncRemote.pull]).
 */
class SupabaseSyncRemote internal constructor(private val clientOrNull: suspend () -> SupabaseClient?) : SyncRemote {
    constructor(provider: SupabaseProvider) : this(provider::client)

    private suspend fun client() = clientOrNull() ?: error("Cloud sync isn't configured in this build")

    @Serializable
    private data class IdOnly(@SerialName("sync_id") val syncId: String)

    override suspend fun hasRows(userId: String): Boolean =
        client().from(TABLE).select(Columns.list("sync_id")) {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<IdOnly>().isNotEmpty()

    override suspend fun pull(userId: String, since: String?, offset: Int, limit: Int): List<RemoteRow> =
        client().from(TABLE).select {
            filter {
                eq("user_id", userId)
                if (since != null) gt("server_updated_at", since)
            }
            order("server_updated_at", Order.ASCENDING)
            order("sync_id", Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<RemoteRow>()

    override suspend fun push(rows: List<SyncRow>) {
        client().postgrest.rpc("push_rows", buildJsonObject { put("rows", json.encodeToJsonElement(rows)) })
    }

    override suspend fun checkIn(device: DeviceCheckIn) {
        client().postgrest.rpc("check_in_device", json.encodeToJsonElement(device).jsonObject)
    }

    private val json = Json { encodeDefaults = true }

    private companion object {
        const val TABLE = "sync_rows"
    }
}
