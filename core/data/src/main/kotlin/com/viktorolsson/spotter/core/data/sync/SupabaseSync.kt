package com.viktorolsson.spotter.core.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
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
            install(Auth)
            install(Postgrest)
        }
    }
}

data class Account(val userId: String, val email: String?)

/** Sign-in with a 6-digit code sent by email: no passwords, no deep links. */
@Singleton
class AccountService @Inject constructor(private val provider: SupabaseProvider) {
    val isAvailable: Boolean get() = provider.client != null

    /** The signed-in account, or null; also null while the session is restored on start. */
    val account: Flow<Account?> = provider.client?.auth?.sessionStatus?.map { status ->
        (status as? SessionStatus.Authenticated)?.session?.user?.let { Account(it.id, it.email) }
    } ?: flowOf(null)

    fun currentAccount(): Account? = provider.client?.auth?.currentUserOrNull()?.let { Account(it.id, it.email) }

    suspend fun sendCode(email: String) {
        requireClient().auth.signInWith(OTP) {
            this.email = email.trim()
            createUser = true
        }
    }

    suspend fun verifyCode(email: String, code: String) {
        requireClient().auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim(), token = code.trim())
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

/** [SyncRemote] over the `sync_rows` table and `push_rows` function (see supabase/001_sync.sql). */
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
