package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import kotlinx.coroutines.CancellationException

/** Android's [SessionStore]: the encrypted store, with the pre-0.11.0 plain session moved into it. */
class AndroidSessionStore(private val context: Context) : SessionStore {
    override fun open(): Settings? =
        AuthStorage.open(context)?.also { AuthStorage.migratePlainSession(context, it) }
}

/** Where the auth session and PKCE verifier live: EncryptedSharedPreferences (AES-256, Keystore master key). */
internal object AuthStorage {
    private const val FILE = "spotter_auth"
    /** Jetpack Security keeps the wrapped keysets for every encrypted prefs file here. */
    private const val KEYSET_FILE = "__androidx_security_crypto_encrypted_prefs__"
    private const val TAG = "SpotterAuth"

    /**
     * Opens the store, recovering from a broken one: if the encrypted file can't be opened
     * (a corrupt or restored file, a Keystore key that no longer matches), the file and its
     * keysets are deleted and it is created fresh once (the user signs in again). If even
     * that fails it returns null and the caller keeps the session in memory for this
     * process, so the app keeps working and sign-in is asked for again on the next launch.
     * [create] is the seam for tests.
     */
    fun open(context: Context, create: (Context) -> SharedPreferences = ::encryptedPreferences): Settings? {
        repeat(2) { attempt ->
            try {
                return SharedPreferencesSettings(create(context))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Could not open the session store (attempt ${attempt + 1}): ${e.javaClass.simpleName}")
                context.deleteSharedPreferences(FILE)
                context.deleteSharedPreferences(KEYSET_FILE)
            }
        }
        Log.w(TAG, "Session store unavailable; keeping the session in memory for this process")
        return null
    }

    @Suppress("DEPRECATION") // Jetpack Security is in maintenance but still the supported Keystore-backed store.
    fun encryptedPreferences(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Releases before 0.11.0 kept the session in the default (plain) SharedPreferences via
     * supabase-kt's default session manager. Move it over once so nobody is signed out by
     * the update, and wipe the plain copy.
     */
    fun migratePlainSession(context: Context, secure: Settings) {
        val plain = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val keys = listOf(SettingsSessionManager.SETTINGS_KEY, SettingsCodeVerifierCache.SETTINGS_KEY)
        if (keys.none { plain.contains(it) }) return
        try {
            keys.forEach { key ->
                plain.getString(key, null)?.let { if (!secure.hasKey(key)) secure.putString(key, it) }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not move the old session: ${e.javaClass.simpleName}")
        }
        plain.edit().apply { keys.forEach(::remove) }.apply()
    }
}
