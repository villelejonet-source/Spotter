package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.SettingsSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

/** A broken encrypted store must never take the app down: it is recreated, or kept in memory. */
@RunWith(AndroidJUnit4::class)
class AuthStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun plainPrefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun aHealthyStoreIsUsedAsIs() {
        val settings = AuthStorage.open(context) { plainPrefs("spotter_auth") }
        assertTrue(settings is SharedPreferencesSettings)
        settings?.putString(SettingsSessionManager.SETTINGS_KEY, "s")
        assertEquals("s", plainPrefs("spotter_auth").getString(SettingsSessionManager.SETTINGS_KEY, null))
    }

    @Test
    fun aCorruptStoreIsWipedAndRecreatedOnce() {
        plainPrefs("spotter_auth").edit().putString("stale", "x").apply()
        plainPrefs("__androidx_security_crypto_encrypted_prefs__").edit().putString("keyset", "k").apply()
        var attempts = 0
        val settings = AuthStorage.open(context) {
            attempts++
            if (attempts == 1) throw GeneralSecurityException("keyset is corrupt") else plainPrefs("spotter_auth")
        }
        assertEquals(2, attempts)
        assertTrue(settings is SharedPreferencesSettings)
        assertNull(plainPrefs("spotter_auth").getString("stale", null)) // the broken file is gone
        assertNull(plainPrefs("__androidx_security_crypto_encrypted_prefs__").getString("keyset", null))
    }

    @Test
    fun aStoreThatKeepsFailingFallsBackToMemory() {
        var attempts = 0
        // Null tells SupabaseProvider to use MemorySessionManager/MemoryCodeVerifierCache.
        val settings = AuthStorage.open(context) { attempts++; throw java.io.IOException("no keystore") }
        assertEquals(2, attempts)
        assertNull(settings)
    }

    @Test
    fun theOldPlainSessionIsMovedOverOnce() {
        val plain = plainPrefs("${context.packageName}_preferences")
        plain.edit().putString(SettingsSessionManager.SETTINGS_KEY, "{\"old\":true}").apply()
        val secure = SharedPreferencesSettings(plainPrefs("migration-target"))
        AuthStorage.migratePlainSession(context, secure)
        assertEquals("{\"old\":true}", secure.getStringOrNull(SettingsSessionManager.SETTINGS_KEY))
        assertNull(plain.getString(SettingsSessionManager.SETTINGS_KEY, null))
    }
}
