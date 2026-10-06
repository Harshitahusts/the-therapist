package app.haven.companion.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Email + password auth against Supabase Auth's REST API. */
class AuthRepository(private val http: OkHttpClient, private val store: SessionStore) {

    sealed interface SignUpResult {
        data object SignedIn : SignUpResult
        data object ConfirmEmail : SignUpResult
    }

    private val _signedIn = MutableStateFlow(store.refreshToken != null)
    val signedIn: StateFlow<Boolean> = _signedIn

    private val refreshLock = Mutex()
    private val jsonType = "application/json".toMediaType()

    @Serializable
    private data class Credentials(val email: String, val password: String)

    @Serializable
    private data class RefreshBody(val refresh_token: String)

    @Serializable
    private data class RecoverBody(val email: String)

    @Serializable
    private data class SessionResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val expires_at: Long? = null,
    )

    private fun authUrl(path: String) = "${Config.supabaseUrl}/auth/v1/$path"

    private suspend fun post(path: String, body: String, bearer: String? = null): String {
        val req = Request.Builder()
            .url(authUrl(path))
            .header("apikey", Config.supabaseAnonKey)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .post(body.toRequestBody(jsonType))
            .build()
        http.newCall(req).await().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, errorMessage(text, "Sign-in failed (${resp.code})"))
            }
            return text.orEmpty()
        }
    }

    private fun saveSession(raw: String, email: String?): Boolean {
        val s = AppJson.decodeFromString<SessionResponse>(raw)
        val access = s.access_token ?: return false
        store.accessToken = access
        store.refreshToken = s.refresh_token
        store.expiresAt = s.expires_at ?: (System.currentTimeMillis() / 1000 + (s.expires_in ?: 3600))
        if (email != null) store.email = email
        _signedIn.value = true
        return true
    }

    suspend fun signIn(email: String, password: String) {
        val raw = post("token?grant_type=password", AppJson.encodeToString(Credentials(email.trim(), password)))
        check(saveSession(raw, email.trim())) { "No session returned" }
    }

    suspend fun signUp(email: String, password: String): SignUpResult {
        val raw = post("signup", AppJson.encodeToString(Credentials(email.trim(), password)))
        return if (saveSession(raw, email.trim())) SignUpResult.SignedIn else SignUpResult.ConfirmEmail
    }

    suspend fun sendPasswordReset(email: String) {
        post("recover", AppJson.encodeToString(RecoverBody(email.trim())))
    }

    /** Returns a non-expired access token, refreshing if needed, or null if signed out. */
    suspend fun accessToken(forceRefresh: Boolean = false): String? {
        return refreshLock.withLock {
            val now = System.currentTimeMillis() / 1000
            val current = store.accessToken
            if (!forceRefresh && current != null && store.expiresAt - now > 60) return current
            val refresh = store.refreshToken ?: return null
            try {
                val raw = post("token?grant_type=refresh_token", AppJson.encodeToString(RefreshBody(refresh)))
                if (!saveSession(raw, null)) signOutLocally()
            } catch (e: ApiException) {
                // Refresh token revoked or expired: the user must sign in again.
                signOutLocally()
            }
            store.accessToken
        }
    }

    suspend fun signOut() {
        val token = store.accessToken
        if (token != null) runCatching { post("logout", "{}", bearer = token) }
        signOutLocally()
    }

    fun signOutLocally() {
        store.clearAuth()
        _signedIn.value = false
    }
}
