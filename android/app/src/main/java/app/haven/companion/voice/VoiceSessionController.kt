package app.haven.companion.voice

import android.content.Context
import android.util.Log
import app.haven.companion.core.CrisisResources
import app.haven.companion.core.DataRepository
import app.haven.companion.core.GeminiApi
import app.haven.companion.core.KnowledgeBase
import app.haven.companion.core.LiveCoordinator
import app.haven.companion.core.LiveEffect
import app.haven.companion.core.RiskLevel
import app.haven.companion.core.Safety
import app.haven.companion.core.Summarizer
import app.haven.companion.core.Tools
import app.haven.companion.core.Voices
import app.haven.companion.core.VoicePhase
import app.haven.companion.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val micLevel: Float = 0f,
    val error: String? = null,
    val crisis: CrisisResources? = null,
    val muted: Boolean = false,
    /** When the current conversation started (for the session timer), or null. */
    val startedAt: Long? = null,
)

/**
 * Runs one voice conversation at a time, directly between the phone and the
 * Gemini Live API. Tools, memory and safety checks all run on the device.
 */
class VoiceSessionController(
    private val context: Context,
    http: OkHttpClient,
    private val repo: DataRepository,
    private val store: SecureStore,
    private val knowledge: () -> KnowledgeBase,
    private val gemini: GeminiApi,
    private val sound: SoundEngine,
    /** Picks the Gemini models on first use; returns null when ready or a message for the user. */
    private val ensureModels: suspend () -> String?,
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state

    private val wsClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // All session state is touched only on this thread.
    private val serial = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + serial)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var coordinator: LiveCoordinator? = null
    private var socket: WebSocket? = null
    private var mic: MicRecorder? = null
    private var audio: AudioRouting? = null
    private var conversationId: String? = null
    private var reconnects = 0
    private var liveModel = ""
    private var instructions = ""
    private var voiceInUse = Voices.DEFAULT
    private var triedVoiceFallback = false
    private var maxLengthTimer: Job? = null

    val isActive: Boolean get() = _state.value.phase !in setOf(VoicePhase.IDLE, VoicePhase.ERROR)

    fun start() {
        if (isActive) return
        _state.value = VoiceUiState(phase = VoicePhase.CONNECTING, startedAt = System.currentTimeMillis())
        scope.launch {
            val key = store.apiKey
            if (key.isNullOrBlank()) {
                fail("Haven's voice isn't set up in this build.")
                return@launch
            }
            ensureModels()?.let { problem ->
                fail(problem)
                return@launch
            }
            val model = store.liveModel!!
            try {
                val zone = runCatching { ZoneId.of(repo.data.profile.timezone) }.getOrDefault(ZoneId.systemDefault())
                liveModel = model
                instructions = LiveCoordinator.buildInstructions(repo.data, ZonedDateTime.now(zone))
                voiceInUse = Voices.find(store.voice).name
                triedVoiceFallback = false
                coordinator = LiveCoordinator(model, instructions, voiceInUse)
                conversationId = repo.startConversation().id
                reconnects = 0
                audio = AudioRouting(context).also { it.start() }
                sound.onVoiceIdle = { scope.launch { coordinator?.let { handle(it.onPlaybackIdle()) } } }
                sound.setConversationMode(true)
                open(key)
                maxLengthTimer = scope.launch {
                    delay(MAX_CONVERSATION_MS)
                    teardown(summarize = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not start conversation", e)
                fail(NETWORK_ERROR)
            }
        }
    }

    /** Ends the conversation and writes its summary (if memory is on). Safe to call repeatedly. */
    fun stop() {
        scope.launch { teardown(summarize = true) }
    }

    fun setMuted(muted: Boolean) {
        mic?.muted = muted
        _state.update { it.copy(muted = muted) }
    }

    fun dismissCrisisCard() = _state.update { it.copy(crisis = null) }

    fun clearError() = _state.update { if (it.phase == VoicePhase.ERROR) VoiceUiState(crisis = it.crisis) else it }

    private fun open(key: String) {
        val c = coordinator ?: return
        val request = Request.Builder()
            .url(LIVE_URL)
            .header("x-goog-api-key", key)
            .build()
        lateinit var ws: WebSocket
        ws = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(c.setupMessage())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = deliver(webSocket, text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = deliver(webSocket, bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                scope.launch { if (webSocket == socket) connectionLost(code, reason) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "live connection failed", t)
                scope.launch { if (webSocket == socket) connectionLost(-1, "") }
            }
        })
        socket = ws
    }

    private fun deliver(webSocket: WebSocket, text: String) {
        scope.launch {
            val c = coordinator ?: return@launch
            if (webSocket != socket) return@launch
            handle(c.onServerMessage(text))
            if (c.setupDone && mic == null) startMic()
        }
    }

    private fun startMic() {
        val m = MicRecorder(
            onChunk = { pcm ->
                val c = coordinator
                if (c != null && c.setupDone) socket?.send(c.audioMessage(pcm))
            },
            onLevel = { level ->
                val current = _state.value.micLevel
                val smoothed = current * 0.7f + level * 0.3f
                if (kotlin.math.abs(smoothed - current) > 0.02f) _state.update { it.copy(micLevel = smoothed) }
            },
        )
        m.muted = _state.value.muted
        try {
            m.start()
            mic = m
        } catch (e: Exception) {
            Log.w(TAG, "microphone failed", e)
            scope.launch { fail("Haven couldn't use the microphone. Check the microphone permission and try again.") }
        }
    }

    private suspend fun connectionLost(code: Int, reason: String) {
        val c = coordinator ?: return
        // Older Live models only know the classic voices: retry once with one of those.
        if (!c.setupDone && voiceInUse !in Voices.CLASSIC && !triedVoiceFallback) {
            triedVoiceFallback = true
            voiceInUse = Voices.CLASSIC_FALLBACK
            Log.i(TAG, "voice not accepted (code $code); retrying with a classic voice")
            coordinator = LiveCoordinator(liveModel, instructions, voiceInUse)
            socket = null
            store.apiKey?.let { open(it) } ?: fail(NETWORK_ERROR)
            return
        }
        // The model itself wouldn't start: move on to the next best live model and remember it.
        val nextModel = store.liveFallbacks.firstOrNull()
        if (!c.setupDone && nextModel != null) {
            Log.i(TAG, "live model $liveModel did not start (code $code); trying $nextModel")
            store.liveFallbacks = store.liveFallbacks.drop(1)
            store.liveModel = nextModel
            liveModel = nextModel
            triedVoiceFallback = voiceInUse in Voices.CLASSIC
            coordinator = LiveCoordinator(liveModel, instructions, voiceInUse)
            socket = null
            store.apiKey?.let { open(it) } ?: fail(NETWORK_ERROR)
            return
        }
        // Gemini rotates connections; resume the same session when we can.
        if (c.resumeHandle != null && reconnects < MAX_RECONNECTS && code != 1007 && code != 1008) {
            reconnects++
            reconnect()
            return
        }
        Log.w(TAG, "connection closed: $code")
        fail(messageForClose(code, reason))
    }

    private fun reconnect() {
        val c = coordinator ?: return
        val key = store.apiKey ?: return
        c.onReconnecting()
        val old = socket
        socket = null
        old?.close(1000, null)
        open(key)
    }

    private fun handle(effects: List<LiveEffect>) {
        val c = coordinator ?: return
        for (e in effects) when (e) {
            is LiveEffect.Send -> socket?.send(e.json)
            is LiveEffect.PlayAudio -> sound.enqueueVoice(e.pcm)
            LiveEffect.StopPlayback -> sound.flushVoice()
            is LiveEffect.Phase -> _state.update { it.copy(phase = e.phase) }
            is LiveEffect.Log -> Log.i(TAG, e.message)
            LiveEffect.Reconnect -> {
                reconnects = 0
                reconnect()
            }
            is LiveEffect.RunTool -> background.launch {
                val result = try {
                    Tools(repo, knowledge()).execute(e.name, e.args)
                } catch (ex: Exception) {
                    Log.w(TAG, "tool ${e.name} failed", ex)
                    buildJsonObject { put("error", "This is unavailable right now. Carry on without it.") }
                }
                scope.launch { if (coordinator === c) handle(c.onToolResult(e.id, e.name, result)) }
            }
            is LiveEffect.CheckSafety -> {
                val assessment = Safety.classify(e.text)
                if (assessment.level >= RiskLevel.MODERATE) background.launch { repo.recordSafetyEvent(assessment) }
                val region = repo.data.settings.crisisRegion
                if (assessment.showResources) _state.update { it.copy(crisis = Safety.resourcesFor(region)) }
                handle(c.onSafety(assessment, Safety.guidanceFor(assessment, region)))
            }
        }
    }

    private suspend fun fail(message: String) = teardown(summarize = true, error = message)

    private suspend fun teardown(summarize: Boolean, error: String? = null) {
        maxLengthTimer?.cancel()
        maxLengthTimer = null
        val transcript = coordinator?.transcript().orEmpty()
        val id = conversationId
        coordinator = null
        conversationId = null
        val ws = socket
        socket = null
        ws?.close(1000, null)
        mic?.stop()
        mic = null
        sound.onVoiceIdle = null
        sound.setConversationMode(false)
        audio?.stop()
        audio = null
        val crisis = _state.value.crisis
        _state.value = VoiceUiState(
            phase = if (error != null) VoicePhase.ERROR else VoicePhase.IDLE,
            error = error,
            crisis = crisis, // never hide crisis resources just because the call ended
        )
        if (id == null) return
        repo.endConversation(id)
        val textModel = store.textModel
        if (summarize && repo.memoryEnabled && textModel != null && transcript.any { it.role == "user" }) {
            background.launch {
                runCatching {
                    Summarizer(repo) { system, user -> gemini.generateJson(textModel, system, user) }.summarize(id, transcript)
                }.onFailure { Log.w(TAG, "summary failed", it) }
            }
        }
    }

    private fun messageForClose(code: Int, reason: String): String {
        val r = reason.lowercase()
        return when {
            "api key" in r || "api_key" in r -> "Your Gemini API key was rejected. Check it in Settings."
            "quota" in r || "resource_exhausted" in r || "rate" in r -> "Gemini's free limit was reached. Please try again in a little while."
            "model" in r && ("not found" in r || "not supported" in r) -> "The selected voice model isn't available. Choose another in Settings."
            code == 1011 -> "Gemini had a problem on its side. Please try again."
            else -> NETWORK_ERROR
        }
    }

    companion object {
        private const val TAG = "VoiceSession"
        const val NETWORK_ERROR = "I'm having trouble connecting right now. Please check your internet connection and try again."
        private const val LIVE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val MAX_RECONNECTS = 3
        private const val MAX_CONVERSATION_MS = 50L * 60 * 1000
    }
}
