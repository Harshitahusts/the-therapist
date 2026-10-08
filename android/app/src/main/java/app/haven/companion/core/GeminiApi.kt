package app.haven.companion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GeminiException(val code: Int, message: String) : IOException(message)

@Serializable
data class GeminiModel(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
) {
    val id: String get() = name.removePrefix("models/")
}

/** Blocking client for the Gemini REST API. Call from a background thread. */
class GeminiApi(
    private val http: OkHttpClient,
    private val apiKey: () -> String?,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    @Serializable
    private data class ModelList(val models: List<GeminiModel> = emptyList(), val nextPageToken: String? = null)

    private fun key(): String = apiKey()?.takeIf { it.isNotBlank() } ?: throw GeminiException(401, "No Gemini API key set")

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw GeminiException(resp.code, describeError(resp.code, body))
            return body
        }
    }

    fun listModels(key: String = key()): List<GeminiModel> {
        val out = mutableListOf<GeminiModel>()
        var page: String? = null
        do {
            val url = "$baseUrl/models?pageSize=1000" + (page?.let { "&pageToken=$it" } ?: "")
            val body = execute(Request.Builder().url(url).header("x-goog-api-key", key).get().build())
            val list = AppJson.decodeFromString<ModelList>(body)
            out += list.models
            page = list.nextPageToken
        } while (!page.isNullOrBlank())
        return out
    }

    /** Asks a text model for a JSON object. */
    fun generateJson(model: String, system: String, user: String): JsonObject {
        val payload = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }
        val body = execute(
            Request.Builder()
                .url("$baseUrl/models/$model:generateContent")
                .header("x-goog-api-key", key())
                .post(AppJson.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                .build()
        )
        return try {
            val text = AppJson.parseToJsonElement(body).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
                .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
            AppJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw GeminiException(502, "The model did not return valid JSON")
        }
    }

    /**
     * Speaks [text] with a prebuilt [voice] using a Gemini text-to-speech model (for voice previews).
     * Returns 24 kHz, 16-bit little-endian mono PCM.
     */
    fun speak(model: String, voice: String, text: String): ByteArray {
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) }) })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", voice) })
                    })
                })
            })
        }
        val body = execute(
            Request.Builder()
                .url("$baseUrl/models/$model:generateContent")
                .header("x-goog-api-key", key())
                .post(AppJson.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                .build()
        )
        return try {
            val data = AppJson.parseToJsonElement(body).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
                .jsonObject["parts"]!!.jsonArray[0].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content
            java.util.Base64.getDecoder().decode(data)
        } catch (e: Exception) {
            throw GeminiException(502, "The voice preview didn't come back.")
        }
    }

    companion object {
        /** A text-to-speech model, for voice previews (optional). */
        fun chooseTtsModel(models: List<GeminiModel>): String? =
            models.filter { "tts" in it.id && "generateContent" in it.supportedGenerationMethods }
                .sortedWith(compareByDescending<GeminiModel> { "flash" in it.id }.thenByDescending { it.id })
                .firstOrNull()?.id

        fun describeError(code: Int, body: String): String = when {
            code == 400 && body.contains("API_KEY_INVALID") -> "That Gemini API key isn't valid."
            code == 403 -> "This Gemini API key isn't allowed to use the Gemini API."
            code == 429 -> "Gemini's free limit was reached. Please try again in a little while."
            code >= 500 -> "Gemini is having trouble right now. Please try again shortly."
            else -> "Gemini returned an error ($code)."
        }

        private fun versionOf(id: String): Double =
            Regex("""gemini-(\d+(?:\.\d+)?)""").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        /**
         * Live (speech-to-speech) models suitable for conversation, best first: newest version,
         * skipping slower "thinking" variants and special-purpose ones (translation, transcription, robotics).
         */
        fun rankLiveModels(models: List<GeminiModel>): List<String> =
            models.filter { "bidiGenerateContent" in it.supportedGenerationMethods }
                .filterNot { m -> listOf("thinking", "translate", "transcribe", "robotics", "embedding").any { it in m.id } }
                .sortedWith(
                    compareByDescending<GeminiModel> { versionOf(it.id) }
                        .thenByDescending { "native-audio" in it.id || "live" in it.id }
                        .thenByDescending { !it.id.contains("preview") }
                        .thenByDescending { it.id }
                )
                .map { it.id }

        fun chooseLiveModel(models: List<GeminiModel>): String? = rankLiveModels(models).firstOrNull()

        /** Picks a fast, generally available text model for end-of-conversation summaries. */
        fun chooseTextModel(models: List<GeminiModel>): String? {
            val text = models.filter { "generateContent" in it.supportedGenerationMethods && it.id.startsWith("gemini-") }
                .filterNot { m -> listOf("image", "tts", "audio", "live", "embedding", "thinking", "exp").any { it in m.id } }
            val stableFlash = Regex("""^gemini-\d+(\.\d+)?-flash(-lite)?$""")
            return text.sortedWith(
                compareByDescending<GeminiModel> { stableFlash.matches(it.id) }
                    .thenByDescending { "flash" in it.id }
                    .thenByDescending { !it.id.contains("preview") }
                    .thenByDescending { versionOf(it.id) }
                    .thenBy { it.id.contains("lite") }
            ).firstOrNull()?.id
        }
    }
}
