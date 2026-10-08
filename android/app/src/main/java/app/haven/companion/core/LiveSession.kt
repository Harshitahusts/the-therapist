package app.haven.companion.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZonedDateTime
import java.util.Base64

enum class VoicePhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR }

/** Something the live session wants the outside world to do. */
sealed interface LiveEffect {
    /** Send a client message over the WebSocket. */
    data class Send(val json: String) : LiveEffect
    /** Queue 24 kHz 16-bit mono PCM for playback. */
    class PlayAudio(val pcm: ByteArray) : LiveEffect
    /** Stop speaking immediately and discard queued audio. */
    data object StopPlayback : LiveEffect
    data class Phase(val phase: VoicePhase) : LiveEffect
    data class RunTool(val id: String, val name: String, val args: JsonObject) : LiveEffect
    /** A user turn finished: run the safety check on it. */
    data class CheckSafety(val text: String) : LiveEffect
    /** The server is about to close this connection: reconnect (resuming the session). */
    data object Reconnect : LiveEffect
    data class Log(val message: String) : LiveEffect
}

/**
 * Pure state machine over the Gemini Live API (BidiGenerateContent) stream.
 * No Android or network code, so it is fully unit-testable.
 */
class LiveCoordinator(
    private val model: String,
    private val instructions: String,
    private val voice: String = DEFAULT_VOICE,
) {
    var phase: VoicePhase = VoicePhase.CONNECTING
        private set

    /** Handle for resuming this session on a fresh connection (connections are rotated by the server). */
    var resumeHandle: String? = null
        private set

    var setupDone = false
        private set

    private var greeted = false
    private var modelTurnActive = false
    private var dropAudio = false
    private var turnDone = true
    private val pendingTools = mutableSetOf<String>()

    private val turns = mutableListOf<TranscriptTurn>()
    private val userBuf = StringBuilder()
    private val assistantBuf = StringBuilder()

    fun transcript(): List<TranscriptTurn> {
        val out = turns.toMutableList()
        if (userBuf.isNotBlank()) out += TranscriptTurn("user", userBuf.toString().trim())
        if (assistantBuf.isNotBlank()) out += TranscriptTurn("assistant", assistantBuf.toString().trim())
        return out
    }

    /** First message on every connection. */
    fun setupMessage(): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", if (model.startsWith("models/")) model else "models/$model")
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } }
                }
            }
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", instructions) } } }
            putJsonArray("tools") { addJsonObject { put("functionDeclarations", Tools.DECLARATIONS) } }
            putJsonObject("realtimeInputConfig") {
                putJsonObject("automaticActivityDetection") {
                    // Give people room to pause and think before the companion replies.
                    put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                    put("silenceDurationMs", 900)
                }
            }
            putJsonObject("inputAudioTranscription") {}
            putJsonObject("outputAudioTranscription") {}
            putJsonObject("sessionResumption") { resumeHandle?.let { put("handle", it) } }
            // Without this, audio sessions are capped at about 15 minutes.
            putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
        }
    }.toString()

    /** Wraps one chunk of 16 kHz 16-bit mono microphone PCM. */
    fun audioMessage(pcm: ByteArray): String = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") {
                put("data", Base64.getEncoder().encodeToString(pcm))
                put("mimeType", "audio/pcm;rate=16000")
            }
        }
    }.toString()

    fun onServerMessage(raw: String): List<LiveEffect> {
        val out = mutableListOf<LiveEffect>()
        val msg = runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return out

        if ("setupComplete" in msg) {
            setupDone = true
            if (!greeted) {
                greeted = true
                out += LiveEffect.Send(textMessage(Prompts.OPENING_CUE))
                setPhase(VoicePhase.THINKING, out)
            } else {
                setPhase(VoicePhase.LISTENING, out)
            }
        }

        (msg["sessionResumptionUpdate"] as? JsonObject)?.let { u ->
            val handle = (u["newHandle"] as? JsonPrimitive)?.contentOrNull
            if ((u["resumable"] as? JsonPrimitive)?.booleanOrNull == true && !handle.isNullOrBlank()) resumeHandle = handle
        }

        (msg["serverContent"] as? JsonObject)?.let { content -> handleContent(content, out) }

        (msg["toolCall"] as? JsonObject)?.let { call ->
            finishUserTurn(out)
            for (fc in (call["functionCalls"] as? JsonArray).orEmpty()) {
                val f = fc as? JsonObject ?: continue
                val id = (f["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val name = (f["name"] as? JsonPrimitive)?.contentOrNull ?: continue
                pendingTools += id
                out += LiveEffect.RunTool(id, name, (f["args"] as? JsonObject) ?: JsonObject(emptyMap()))
            }
            setPhase(VoicePhase.THINKING, out)
        }

        (msg["toolCallCancellation"] as? JsonObject)?.let { c ->
            (c["ids"] as? JsonArray).orEmpty().forEach { id -> (id as? JsonPrimitive)?.contentOrNull?.let { pendingTools -= it } }
        }

        if ("goAway" in msg) out += LiveEffect.Reconnect
        return out
    }

    private fun handleContent(c: JsonObject, out: MutableList<LiveEffect>) {
        (c["inputTranscription"] as? JsonObject)?.let { t ->
            (t["text"] as? JsonPrimitive)?.contentOrNull?.let { text ->
                if (assistantBuf.isNotBlank()) finishAssistantTurn()
                userBuf.append(text)
                if (!modelTurnActive) setPhase(VoicePhase.LISTENING, out)
            }
        }

        val parts = ((c["modelTurn"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        for (p in parts) {
            val inline = (p as? JsonObject)?.get("inlineData") as? JsonObject ?: continue
            val mime = (inline["mimeType"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val data = (inline["data"] as? JsonPrimitive)?.contentOrNull ?: continue
            if (!mime.startsWith("audio/pcm")) continue
            finishUserTurn(out)
            modelTurnActive = true
            turnDone = false
            if (!dropAudio) {
                out += LiveEffect.PlayAudio(Base64.getDecoder().decode(data))
                setPhase(VoicePhase.SPEAKING, out)
            }
        }

        (c["outputTranscription"] as? JsonObject)?.let { t ->
            (t["text"] as? JsonPrimitive)?.contentOrNull?.let { text ->
                finishUserTurn(out)
                if (!dropAudio) assistantBuf.append(text)
            }
        }

        if ((c["interrupted"] as? JsonPrimitive)?.booleanOrNull == true) {
            out += LiveEffect.StopPlayback
            finishAssistantTurn()
            modelTurnActive = false
            dropAudio = false
            turnDone = true
            setPhase(VoicePhase.LISTENING, out)
        }

        if ((c["turnComplete"] as? JsonPrimitive)?.booleanOrNull == true) {
            finishUserTurn(out)
            finishAssistantTurn()
            modelTurnActive = false
            dropAudio = false
            turnDone = true
            if (phase != VoicePhase.SPEAKING && pendingTools.isEmpty()) setPhase(VoicePhase.LISTENING, out)
        }
    }

    /** The speaker has played everything queued. */
    fun onPlaybackIdle(): List<LiveEffect> {
        val out = mutableListOf<LiveEffect>()
        if (turnDone && phase == VoicePhase.SPEAKING) setPhase(VoicePhase.LISTENING, out)
        return out
    }

    fun onToolResult(id: String, name: String, response: JsonObject): List<LiveEffect> {
        if (!pendingTools.remove(id)) return emptyList() // cancelled meanwhile
        val json = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject { put("id", id); put("name", name); put("response", response) }
                }
            }
        }.toString()
        return listOf(LiveEffect.Send(json))
    }

    /**
     * Result of the on-device safety check for the last user turn. For anything above LOW the current
     * reply is cut off and the companion is re-steered with the guidance.
     */
    fun onSafety(assessment: SafetyAssessment, guidance: String?): List<LiveEffect> {
        val out = mutableListOf<LiveEffect>()
        if (guidance == null || !assessment.interrupt) return out
        if (modelTurnActive) {
            out += LiveEffect.StopPlayback
            dropAudio = true // discard the rest of the reply that was generated without the guidance
            assistantBuf.clear()
        }
        out += LiveEffect.Send(textMessage(guidance))
        setPhase(VoicePhase.THINKING, out)
        return out
    }

    /** Called when a connection is replaced; the session itself continues. */
    fun onReconnecting() {
        setupDone = false
        pendingTools.clear()
    }

    private fun finishUserTurn(out: MutableList<LiveEffect>) {
        val text = userBuf.toString().trim()
        userBuf.clear()
        if (text.isNotEmpty()) {
            turns += TranscriptTurn("user", text)
            out += LiveEffect.CheckSafety(text)
        }
    }

    private fun finishAssistantTurn() {
        val text = assistantBuf.toString().trim()
        assistantBuf.clear()
        if (text.isNotEmpty()) turns += TranscriptTurn("assistant", text)
    }

    private fun setPhase(p: VoicePhase, out: MutableList<LiveEffect>) {
        if (phase != p) {
            phase = p
            out += LiveEffect.Phase(p)
        }
    }

    companion object {
        const val DEFAULT_VOICE = "Aoede"

        /** Text input during a live session (used for the opening cue and safety guidance). */
        fun textMessage(text: String): String = buildJsonObject {
            putJsonObject("realtimeInput") { put("text", text) }
        }.toString()

        /** System instructions with the user's context appended. */
        fun buildInstructions(data: CompanionData, now: ZonedDateTime, maxMemories: Int = 12, maxSummaries: Int = 3): String {
            val p = data.profile
            val name = (p.preferredName ?: p.name)?.trim().orEmpty()
            val partOfDay = when (now.hour) {
                in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "night"
            }
            val lines = mutableListOf(
                "",
                "# Context for this conversation (background only; never read it out)",
                "- User's preferred name: ${name.ifEmpty { "unknown (you can ask what they like to be called)" }}",
                "- User's local time: ${now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                    "%02d:%02d ($partOfDay)".format(now.hour, now.minute),
                "- Preferred language: ${p.language}",
            )
            if (p.preferences.isNotEmpty()) lines += "- Communication preferences: " + p.preferences.entries.joinToString { "${it.key}: ${it.value}" }
            if (data.settings.memoryEnabled) {
                lines += "- Memory is ON."
                val mems = data.memories.sortedWith(compareByDescending<Memory> { it.confidence }.thenByDescending { it.updatedAt })
                if (mems.isNotEmpty()) {
                    lines += "- Things you know about the user:"
                    mems.take(maxMemories).forEach { lines += "  - (${it.category}) ${it.content}" }
                }
                val sums = data.summaries.sortedByDescending { it.createdAt }.take(maxSummaries)
                if (sums.isNotEmpty()) {
                    lines += "- Recent conversations (newest first):"
                    sums.forEach { s ->
                        val feel = s.emotionalContext?.let { " Feeling: $it." }.orEmpty()
                        val follow = s.followUp?.let { " Possible follow-up: $it" }.orEmpty()
                        lines += "  - ${s.summary}$feel$follow"
                    }
                }
            } else {
                lines += "- Memory is OFF: you will not remember this conversation later. Do not call save_memory or update_memory."
            }
            data.checks.lastOrNull()?.let { c ->
                val ageDays = (now.toInstant().toEpochMilli() - c.createdAt) / 86_400_000
                if (ageDays <= 7) {
                    val band = Checkin.band(c.score)
                    lines += "- The user's latest wellbeing check-in (${if (ageDays == 0L) "today" else "$ageDays day(s) ago"}): " +
                        "Well-Being Count ${c.score}/100 (${band.name}). It is a self-reflection, not a diagnosis; never call it one. " +
                        "If it fits, you may gently draw on it, e.g. ask about an area that seemed hard. Their answers:"
                    c.answers.forEach { a -> lines += "  - ${a.question} ${a.answer}" }
                }
            }
            return Prompts.VOICE_AGENT + "\n" + lines.joinToString("\n") + "\n"
        }
    }
}
