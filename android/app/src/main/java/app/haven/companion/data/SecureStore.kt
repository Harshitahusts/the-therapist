package app.haven.companion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.haven.companion.BuildConfig
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

    /** A key entered in Settings, otherwise the key built into the app. */
    var apiKey: String?
        get() = prefs.getString("gemini_api_key", null) ?: BUILT_IN_KEY
        set(v) = prefs.edit { putString("gemini_api_key", v) }

    /** True when the app ships with its own Gemini key, so users are never asked for one. */
    val hasBuiltInKey: Boolean get() = BUILT_IN_KEY != null

    var liveModel: String?
        get() = prefs.getString("live_model", null)
        set(v) = prefs.edit { putString("live_model", v) }

    /** Other live models to try, best first, if the chosen one won't start. */
    var liveFallbacks: List<String>
        get() = prefs.getString("live_fallbacks", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        set(v) = prefs.edit { putString("live_fallbacks", v.joinToString(",")) }

    var textModel: String?
        get() = prefs.getString("text_model", null)
        set(v) = prefs.edit { putString("text_model", v) }

    /** Gemini prebuilt voice for the companion. */
    var voice: String?
        get() = prefs.getString("voice", null)
        set(v) = prefs.edit { putString("voice", v) }

    /** Text-to-speech model for voice previews, if the key has one. */
    var ttsModel: String?
        get() = prefs.getString("tts_model", null)
        set(v) = prefs.edit { putString("tts_model", v) }

    var introCompleted: Boolean
        get() = prefs.getBoolean("intro_completed", false)
        set(v) = prefs.edit { putBoolean("intro_completed", v) }

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(v) = prefs.edit { putBoolean("auto_start", v) }

    val isReady: Boolean get() = introCompleted && !apiKey.isNullOrBlank()

    fun clearAll() = prefs.edit { clear() }

    private companion object {
        val BUILT_IN_KEY: String? = BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
    }
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
