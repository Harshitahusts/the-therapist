package app.haven.companion.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Network unreachable, timed out, etc. Shown to the user as a friendly message. */
class NetworkException(cause: Throwable) : IOException(cause)

/** The server answered with an error status. */
class ApiException(val code: Int, message: String) : IOException(message)

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(NetworkException(e))
        }

        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
}

/** Pulls FastAPI's {"detail": "..."} or Supabase's error message out of an error body. */
internal fun errorMessage(body: String?, fallback: String): String {
    if (body.isNullOrBlank()) return fallback
    return runCatching {
        val obj = AppJson.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        listOf("detail", "msg", "error_description", "message", "error")
            .firstNotNullOfOrNull { k -> (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull() ?: fallback
}
