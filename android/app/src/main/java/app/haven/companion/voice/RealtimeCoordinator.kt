package app.haven.companion.voice

import app.haven.companion.data.AppJson
import app.haven.companion.data.TranscriptTurn
import app.haven.companion.data.TurnCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class VoicePhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR }

/** Something the coordinator wants the outside world to do. */
sealed interface Effect {
    /** Send a client event over the realtime data channel. */
    data class Send(val json: String) : Effect

    /** Execute a function tool on the backend, then call [RealtimeCoordinator.onToolResult]. */
    data class RunTool(val callId: String, val name: String, val arguments: JsonObject) : Effect

    /** Run the safety check on what the user just said, then call [RealtimeCoordinator.onSafetyResult]. */
    data class CheckSafety(val text: String) : Effect

    data class Phase(val phase: VoicePhase) : Effect

    data class Log(val message: String) : Effect
}

/**
 * Pure state machine over the OpenAI Realtime event stream. No Android or
 * network code, so it is fully unit-testable.
 *
 * Responsibilities:
 *  - map server events to the orb's phase (listening / thinking / speaking)
 *  - relay tool calls to the backend and resume the response afterwards
 *  - route each user utterance through the safety check, injecting guidance
 *    and interrupting the current reply when the risk is high
 *  - keep an in-memory transcript in the right order (sent to the backend for
 *    summarisation at the end, never stored on the device)
 */
class RealtimeCoordinator {

    private var responseActive = false
    private var speaking = false
    private var pendingTools = 0
    private var toolOutputsPending = false
    private var safetyResponsePending = false

    var phase: VoicePhase = VoicePhase.CONNECTING
        private set

    // item_id -> (role, text). LinkedHashMap keeps conversation order.
    private val items = LinkedHashMap<String, Pair<String, String?>>()

    fun transcript(): List<TranscriptTurn> =
        items.values.mapNotNull { (role, text) -> text?.takeIf { it.isNotBlank() }?.let { TranscriptTurn(role, it) } }

    /** Called when the data channel opens: ask the model for its greeting. */
    fun onConnected(): List<Effect> {
        val out = mutableListOf<Effect>(Effect.Send(responseCreate()))
        setPhase(VoicePhase.THINKING, out)
        return out
    }

    fun onEvent(raw: String): List<Effect> {
        val out = mutableListOf<Effect>()
        val event = runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return out
        when (event.str("type")) {
            "input_audio_buffer.speech_started" -> setPhase(VoicePhase.LISTENING, out)
            "input_audio_buffer.speech_stopped" -> setPhase(VoicePhase.THINKING, out)
            "input_audio_buffer.committed" -> event.str("item_id")?.let { items.getOrPut(it) { "user" to null } }

            "response.created" -> {
                responseActive = true
                if (!speaking) setPhase(VoicePhase.THINKING, out)
            }
            "response.output_item.added", "conversation.item.added", "conversation.item.created" -> {
                val item = event["item"] as? JsonObject
                val id = item?.str("id")
                if (id != null && item.str("type") == "message" && item.str("role") == "assistant") {
                    items.getOrPut(id) { "assistant" to null }
                }
            }
            "output_audio_buffer.started" -> {
                speaking = true
                setPhase(VoicePhase.SPEAKING, out)
            }
            "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> {
                speaking = false
                settle(out)
            }
            "response.done" -> {
                responseActive = false
                maybeContinue(out)
                settle(out)
            }

            "conversation.item.input_audio_transcription.completed" -> {
                val text = event.str("transcript")?.trim().orEmpty()
                val id = event.str("item_id") ?: "user-${items.size}"
                items[id] = "user" to text
                if (text.isNotEmpty()) out += Effect.CheckSafety(text)
            }
            "response.output_audio_transcript.done", "response.audio_transcript.done" -> {
                val text = event.str("transcript")?.trim().orEmpty()
                val id = event.str("item_id") ?: "assistant-${items.size}"
                items[id] = "assistant" to text
            }

            "response.function_call_arguments.done" -> {
                val callId = event.str("call_id") ?: return out
                val name = event.str("name") ?: return out
                val args = runCatching { AppJson.parseToJsonElement(event.str("arguments") ?: "{}").jsonObject }
                    .getOrDefault(JsonObject(emptyMap()))
                pendingTools++
                out += Effect.RunTool(callId, name, args)
            }

            "error" -> {
                val err = event["error"] as? JsonObject
                out += Effect.Log("realtime error: ${err?.str("code") ?: err?.str("type")}")
            }
        }
        return out
    }

    fun onToolResult(callId: String, outputJson: String): List<Effect> {
        val out = mutableListOf<Effect>()
        pendingTools = (pendingTools - 1).coerceAtLeast(0)
        out += Effect.Send(functionOutput(callId, outputJson))
        toolOutputsPending = true
        maybeContinue(out)
        return out
    }

    fun onSafetyResult(check: TurnCheck): List<Effect> {
        val out = mutableListOf<Effect>()
        val guidance = check.guidance ?: return out
        if (check.interrupt) {
            // Stop whatever the model had started saying before it heard the guidance.
            if (responseActive) out += Effect.Send(simple("response.cancel"))
            if (speaking) out += Effect.Send(simple("output_audio_buffer.clear"))
        }
        out += Effect.Send(systemMessage(guidance))
        if (check.interrupt) {
            safetyResponsePending = true
            maybeContinue(out)
        }
        return out
    }

    /** After tool outputs or safety guidance, ask for a (new) response once nothing else is running. */
    private fun maybeContinue(out: MutableList<Effect>) {
        if (responseActive || pendingTools > 0) return
        if (toolOutputsPending || safetyResponsePending) {
            toolOutputsPending = false
            safetyResponsePending = false
            responseActive = true // the server will confirm with response.created
            out += Effect.Send(responseCreate())
            if (!speaking) setPhase(VoicePhase.THINKING, out)
        }
    }

    private fun settle(out: MutableList<Effect>) {
        when {
            speaking -> setPhase(VoicePhase.SPEAKING, out)
            responseActive || pendingTools > 0 -> setPhase(VoicePhase.THINKING, out)
            else -> setPhase(VoicePhase.LISTENING, out)
        }
    }

    private fun setPhase(p: VoicePhase, out: MutableList<Effect>) {
        if (phase != p) {
            phase = p
            out += Effect.Phase(p)
        }
    }

    companion object {
        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        fun simple(type: String) = buildJsonObject { put("type", type) }.toString()

        fun responseCreate() = simple("response.create")

        fun functionOutput(callId: String, outputJson: String) = buildJsonObject {
            put("type", "conversation.item.create")
            put("item", buildJsonObject {
                put("type", "function_call_output")
                put("call_id", callId)
                put("output", outputJson)
            })
        }.toString()

        fun systemMessage(text: String) = buildJsonObject {
            put("type", "conversation.item.create")
            put("item", buildJsonObject {
                put("type", "message")
                put("role", "system")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "input_text")
                        put("text", text)
                    })
                })
            })
        }.toString()
    }
}
