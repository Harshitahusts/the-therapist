package app.haven.companion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Small encrypted key-value store for auth tokens and local, pre-account
 * onboarding choices. Encrypted with a key held in the Android Keystore.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        try {
            create(context, key)
        } catch (e: Exception) {
            // Keystore/prefs can become unreadable after a backup restore or OS
            // bug. Start fresh rather than crash; the user just signs in again.
            context.deleteSharedPreferences(FILE)
            create(context, key)
        }
    }

    private fun create(context: Context, keyAlias: String) = EncryptedSharedPreferences.create(
        FILE,
        keyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var accessToken: String?
        get() = prefs.getString(K_ACCESS, null)
        set(v) = prefs.edit { putString(K_ACCESS, v) }

    var refreshToken: String?
        get() = prefs.getString(K_REFRESH, null)
        set(v) = prefs.edit { putString(K_REFRESH, v) }

    /** Epoch seconds. */
    var expiresAt: Long
        get() = prefs.getLong(K_EXPIRES, 0L)
        set(v) = prefs.edit { putLong(K_EXPIRES, v) }

    var email: String?
        get() = prefs.getString(K_EMAIL, null)
        set(v) = prefs.edit { putString(K_EMAIL, v) }

    var introCompleted: Boolean
        get() = prefs.getBoolean(K_INTRO, false)
        set(v) = prefs.edit { putBoolean(K_INTRO, v) }

    var draftName: String?
        get() = prefs.getString(K_NAME, null)
        set(v) = prefs.edit { putString(K_NAME, v) }

    var draftMemoryEnabled: Boolean
        get() = prefs.getBoolean(K_MEMORY, true)
        set(v) = prefs.edit { putBoolean(K_MEMORY, v) }

    var autoStart: Boolean
        get() = prefs.getBoolean(K_AUTOSTART, true)
        set(v) = prefs.edit { putBoolean(K_AUTOSTART, v) }

    fun clearAuth() = prefs.edit {
        remove(K_ACCESS); remove(K_REFRESH); remove(K_EXPIRES); remove(K_EMAIL)
    }

    fun clearAll() = prefs.edit { clear() }

    private companion object {
        const val FILE = "haven_secure"
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_EXPIRES = "expires_at"
        const val K_EMAIL = "email"
        const val K_INTRO = "intro_completed"
        const val K_NAME = "draft_name"
        const val K_MEMORY = "draft_memory"
        const val K_AUTOSTART = "auto_start"
    }
}
