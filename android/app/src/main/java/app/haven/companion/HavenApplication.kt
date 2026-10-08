package app.haven.companion

import android.app.Application
import app.haven.companion.core.DataRepository
import app.haven.companion.core.GeminiApi
import app.haven.companion.core.GeminiException
import app.haven.companion.core.KnowledgeBase
import app.haven.companion.data.EncryptedPersistence
import app.haven.companion.data.SecureStore
import app.haven.companion.voice.SoundEngine
import app.haven.companion.voice.VoiceSessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

class HavenApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val store = SecureStore(app)
    val repo = DataRepository(EncryptedPersistence(app))
    val gemini = GeminiApi(http, apiKey = { store.apiKey })

    /** The bundled psychoeducation library (from /knowledge/sources, packaged as assets). */
    val knowledge: KnowledgeBase by lazy {
        val dir = "sources"
        KnowledgeBase(
            app.assets.list(dir).orEmpty().filter { it.endsWith(".md") }.sorted().map { name ->
                name to app.assets.open("$dir/$name").bufferedReader().use { it.readText() }
            }
        )
    }

    val sound = SoundEngine()
    val voice = VoiceSessionController(app, http, repo, store, { knowledge }, gemini, sound)

    /**
     * Checks a Gemini API key, picks the voice and text models it can use, and saves it.
     * Returns null on success, or a message for the user.
     */
    suspend fun connectGeminiKey(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val models = gemini.listModels(key.trim())
            val live = GeminiApi.chooseLiveModel(models)
                ?: return@withContext "This key works, but it can't use Gemini's live voice models yet. Try again later or create a new key in Google AI Studio."
            store.apiKey = key.trim()
            store.liveModel = live
            store.textModel = GeminiApi.chooseTextModel(models)
            store.ttsModel = GeminiApi.chooseTtsModel(models)
            null
        } catch (e: GeminiException) {
            e.message
        } catch (e: IOException) {
            VoiceSessionController.NETWORK_ERROR
        }
    }

    /** Live (voice) models available to the saved key. */
    suspend fun liveModels(): List<String> = withContext(Dispatchers.IO) {
        gemini.listModels().filter { "bidiGenerateContent" in it.supportedGenerationMethods }.map { it.id }.sorted()
    }
}
