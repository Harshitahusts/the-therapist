package app.haven.companion.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Client for the Haven backend. Every call carries the user's Supabase access token. */
class ApiClient(private val http: OkHttpClient, private val auth: AuthRepository) {

    private val jsonType = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: RequestBody? = null): String {
        var token = auth.accessToken() ?: throw ApiException(401, "Signed out")
        for (attempt in 0..1) {
            val req = Request.Builder()
                .url("${Config.backendUrl}$path")
                .header("Authorization", "Bearer $token")
                .method(method, body)
                .build()
            val (code, text) = http.newCall(req).await().use { it.code to it.body?.string() }
            if (code == 401) {
                if (attempt == 0) {
                    token = auth.accessToken(forceRefresh = true) ?: throw ApiException(401, "Signed out")
                    continue
                }
                auth.signOutLocally()
                throw ApiException(401, "Signed out")
            }
            if (code !in 200..299) throw ApiException(code, errorMessage(text, "Request failed ($code)"))
            return text.orEmpty()
        }
        error("unreachable")
    }

    private inline fun <reified T> json(value: T): RequestBody = AppJson.encodeToString(value).toRequestBody(jsonType)
    private val empty: RequestBody get() = "{}".toRequestBody(jsonType)

    suspend fun me(): Me = AppJson.decodeFromString(call("GET", "/v1/me"))

    suspend fun completeOnboarding(req: OnboardingRequest): Me =
        AppJson.decodeFromString(call("POST", "/v1/me/onboarding", json(req)))

    suspend fun updateSettings(update: SettingsUpdate): Me =
        AppJson.decodeFromString(call("PUT", "/v1/me/settings", json(update)))

    suspend fun crisisResources(): CrisisResources =
        AppJson.decodeFromString(call("GET", "/v1/me/crisis-resources"))

    suspend fun memories(): List<MemoryItem> = AppJson.decodeFromString(call("GET", "/v1/memories"))

    suspend fun deleteMemory(id: String) {
        call("DELETE", "/v1/memories/$id")
    }

    suspend fun deleteAllMemories() {
        call("DELETE", "/v1/memories")
    }

    /** Raw JSON so it can be written straight to a file the user picks. */
    suspend fun exportData(): String = call("GET", "/v1/me/export")

    suspend fun deleteAllData() {
        call("DELETE", "/v1/me/data")
    }

    suspend fun deleteAccount() {
        call("DELETE", "/v1/me")
    }

    suspend fun startRealtimeSession(): RealtimeSession =
        AppJson.decodeFromString(call("POST", "/v1/realtime/session", empty))

    suspend fun checkTurn(conversationId: String, text: String): TurnCheck =
        AppJson.decodeFromString(call("POST", "/v1/conversations/$conversationId/turns", json(TurnRequest(text.take(MAX_TEXT)))))

    suspend fun callTool(conversationId: String, name: String, arguments: JsonObject): String =
        call("POST", "/v1/conversations/$conversationId/tools/$name", json(ToolCallRequest(arguments)))

    suspend fun endConversation(conversationId: String, transcript: List<TranscriptTurn>) {
        // Mirror the server's limits so one very long turn can't make the whole request fail.
        val bounded = transcript.takeLast(MAX_TURNS).map { it.copy(text = it.text.take(MAX_TEXT)) }
        call("POST", "/v1/conversations/$conversationId/end", json(EndRequest(bounded)))
    }

    private companion object {
        const val MAX_TEXT = 4000
        const val MAX_TURNS = 500
    }
}
