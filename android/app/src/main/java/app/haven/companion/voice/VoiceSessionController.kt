package app.haven.companion.voice

import android.content.Context
import android.util.Log
import app.haven.companion.data.ApiClient
import app.haven.companion.data.ApiException
import app.haven.companion.data.Config
import app.haven.companion.data.CrisisResources
import app.haven.companion.data.NetworkException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val micLevel: Float = 0f,
    val error: String? = null,
    val crisis: CrisisResources? = null,
    val muted: Boolean = false,
)

/**
 * Runs one voice conversation at a time: mints a session via the backend,
 * connects WebRTC, drives [RealtimeCoordinator], and hands the transcript to
 * the backend for summarising when the conversation ends.
 */
class VoiceSessionController(
    private val context: Context,
    private val http: OkHttpClient,
    private val api: ApiClient,
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state

    // All coordinator access happens on this one thread; WebRTC calls back on its own threads.
    private val serial = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + serial)

    private var coordinator: RealtimeCoordinator? = null
    private var link: WebRtcVoiceLink? = null
    private var conversationId: String? = null
    private var audio: AudioRouting? = null
    private var maxLengthTimer: Job? = null

    val isActive: Boolean
        get() = _state.value.phase !in setOf(VoicePhase.IDLE, VoicePhase.ERROR)

    fun start() {
        if (isActive) return
        _state.value = VoiceUiState(phase = VoicePhase.CONNECTING)
        scope.launch {
            try {
                val session = api.startRealtimeSession()
                conversationId = session.conversation_id
                val c = RealtimeCoordinator()
                coordinator = c
                audio = AudioRouting(context).also { it.start() }
                val l = WebRtcVoiceLink(context, http, linkListener)
                link = l
                l.connect(session.calls_url, session.client_secret)
                maxLengthTimer = scope.launch {
                    delay(MAX_CONVERSATION_MS)
                    stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not start conversation", e)
                fail(userMessageFor(e))
            }
        }
    }

    /** Ends the conversation and sends the transcript for summarising. Safe to call repeatedly. */
    fun stop() {
        scope.launch { teardown(sendTranscript = true) }
    }

    fun setMuted(muted: Boolean) {
        link?.setMicEnabled(!muted)
        _state.update { it.copy(muted = muted) }
    }

    fun dismissCrisisCard() = _state.update { it.copy(crisis = null) }

    fun showCrisisCard(resources: CrisisResources) = _state.update { it.copy(crisis = resources) }

    fun clearError() = _state.update { if (it.phase == VoicePhase.ERROR) VoiceUiState() else it }

    private suspend fun teardown(sendTranscript: Boolean, keepError: String? = null) {
        maxLengthTimer?.cancel()
        maxLengthTimer = null
        val transcript = coordinator?.transcript().orEmpty()
        val id = conversationId
        link?.close()
        link = null
        audio?.stop()
        audio = null
        coordinator = null
        conversationId = null
        val crisis = _state.value.crisis
        _state.value = VoiceUiState(
            phase = if (keepError != null) VoicePhase.ERROR else VoicePhase.IDLE,
            error = keepError,
            crisis = crisis, // never hide crisis resources just because the call ended
        )
        if (sendTranscript && id != null) {
            withContext(NonCancellable) {
                runCatching { api.endConversation(id, transcript) }
                    .onFailure { Log.w(TAG, "could not end conversation cleanly", it) }
            }
        }
    }

    private suspend fun fail(message: String) = teardown(sendTranscript = true, keepError = message)

    private fun handle(effects: List<Effect>) {
        for (effect in effects) when (effect) {
            is Effect.Send -> link?.send(effect.json)
            is Effect.Phase -> _state.update { it.copy(phase = effect.phase) }
            is Effect.Log -> Log.i(TAG, effect.message)
            is Effect.RunTool -> runTool(effect)
            is Effect.CheckSafety -> checkSafety(effect.text)
        }
    }

    private fun runTool(effect: Effect.RunTool) {
        val id = conversationId ?: return
        scope.launch {
            val output = try {
                api.callTool(id, effect.name, effect.arguments)
            } catch (e: Exception) {
                Log.w(TAG, "tool ${effect.name} failed", e)
                buildJsonObject { put("error", "This is unavailable right now. Carry on without it.") }.toString()
            }
            coordinator?.let { handle(it.onToolResult(effect.callId, output)) }
        }
    }

    private fun checkSafety(text: String) {
        val id = conversationId ?: return
        scope.launch {
            val check = try {
                api.checkTurn(id, text)
            } catch (e: Exception) {
                Log.w(TAG, "safety check failed", e)
                return@launch // the voice model's own safety instructions still apply
            }
            check.resources?.let { showCrisisCard(it) }
            coordinator?.let { handle(it.onSafetyResult(check)) }
        }
    }

    private val linkListener = object : WebRtcVoiceLink.Listener {
        override fun onDataChannelOpen() {
            scope.launch { coordinator?.let { handle(it.onConnected()) } }
        }

        override fun onServerEvent(json: String) {
            scope.launch { coordinator?.let { handle(it.onEvent(json)) } }
        }

        override fun onConnectionLost() {
            scope.launch { if (coordinator != null) fail(Config.NETWORK_ERROR_MESSAGE) }
        }

        override fun onMicLevel(level: Float) {
            // ~100 callbacks/s: smooth, and only publish visible changes.
            val current = _state.value.micLevel
            val smoothed = current * 0.7f + level * 0.3f
            if (kotlin.math.abs(smoothed - current) > 0.02f) _state.update { it.copy(micLevel = smoothed) }
        }
    }

    private fun userMessageFor(e: Exception): String = when {
        e is NetworkException -> Config.NETWORK_ERROR_MESSAGE
        e is ApiException && e.code == 429 -> e.message ?: "Please take a short break and try again soon."
        e is ApiException && e.code == 401 -> "Please sign in again."
        e is ApiException && e.code >= 500 -> "The voice service is unavailable right now. Please try again in a moment."
        else -> Config.NETWORK_ERROR_MESSAGE
    }

    private companion object {
        const val TAG = "VoiceSession"
        // Realtime sessions are capped server-side at about an hour; end gracefully before that.
        const val MAX_CONVERSATION_MS = 50L * 60 * 1000
    }
}
