package app.haven.companion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.haven.companion.core.Persistence

private fun encryptedPrefs(context: Context, file: String): SharedPreferences {
    val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    fun create() = EncryptedSharedPreferences.create(
        file,
        key,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    return try {
        create()
    } catch (e: Exception) {
        // Keystore/prefs can become unreadable after an OS bug or restore. Start fresh rather than crash.
        context.deleteSharedPreferences(file)
        create()
    }
}

/**
 * Small encrypted settings store (keys held in the Android Keystore): the
 * Gemini API key, chosen models and app preferences.
 */
class SecureStore(context: Context) {
    private val prefs = encryptedPrefs(context, "haven_secure")

    var apiKey: String?
        get() = prefs.getString("gemini_api_key", null)
        set(v) = prefs.edit { putString("gemini_api_key", v) }

    var liveModel: String?
        get() = prefs.getString("live_model", null)
        set(v) = prefs.edit { putString("live_model", v) }

    var textModel: String?
        get() = prefs.getString("text_model", null)
        set(v) = prefs.edit { putString("text_model", v) }

    var introCompleted: Boolean
        get() = prefs.getBoolean("intro_completed", false)
        set(v) = prefs.edit { putBoolean("intro_completed", v) }

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(v) = prefs.edit { putBoolean("auto_start", v) }

    val isReady: Boolean get() = introCompleted && !apiKey.isNullOrBlank() && !liveModel.isNullOrBlank()

    fun clearAll() = prefs.edit { clear() }
}

/** Holds Haven's data document (memories, summaries, profile…) encrypted on the device. */
class EncryptedPersistence(context: Context) : Persistence {
    private val prefs = encryptedPrefs(context, "haven_data")
    override fun load(): String? = prefs.getString("doc", null)
    override fun save(json: String) {
        prefs.edit(commit = true) { putString("doc", json) }
    }
    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }
}
