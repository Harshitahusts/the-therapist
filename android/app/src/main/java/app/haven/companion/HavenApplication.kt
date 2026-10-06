package app.haven.companion

import android.app.Application
import app.haven.companion.data.ApiClient
import app.haven.companion.data.AuthRepository
import app.haven.companion.data.SessionStore
import app.haven.companion.voice.VoiceSessionController
import okhttp3.OkHttpClient
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val store = SessionStore(app)
    val auth = AuthRepository(http, store)
    val api = ApiClient(http, auth)
    val voice = VoiceSessionController(app, http, api)
}
