package app.haven.companion.live

import app.haven.companion.core.AppJson
import app.haven.companion.core.LiveCoordinator
import app.haven.companion.core.LiveEffect
import app.haven.companion.core.Safety
import app.haven.companion.core.SafetyAssessment
import app.haven.companion.core.Tools
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Drives the app's real [LiveCoordinator], [Tools] and [Safety] code against the
 * Gemini Live API, the same way VoiceSessionController does on the phone, minus
 * Android audio. Used by the opt-in live tests (HAVEN_LIVE_TEST=1).
 */
class LiveHarness(
    private val key: String,
    model: String,
    instructions: String,
    voice: String,
    private val tools: Tools,
    private val region: String = "IN",
) : AutoCloseable {
    val coordinator = LiveCoordinator(model, instructions, voice)
    val audio = ByteArrayOutputStream()          // everything Haven said, 24 kHz PCM
    val toolCalls = mutableListOf<String>()
    val safety = mutableListOf<SafetyAssessment>()
    var closeReason: String? = null
    /** Compact log of what the server sent, for debugging. */
    val trace = mutableListOf<String>()
    private var turnCompletes = 0
    private val inbox = LinkedBlockingQueue<String>()
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val ws: WebSocket

    init {
        val req = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent")
            .header("x-goog-api-key", key)
            .build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(coordinator.setupMessage()) }
            override fun onMessage(webSocket: WebSocket, text: String) { inbox.put(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { inbox.put(bytes.utf8()) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { closeReason = "$code $reason"; inbox.put(CLOSED) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closeReason = "failure: ${t.message}"; inbox.put(CLOSED) }
        })
    }

    private fun handle(effects: List<LiveEffect>) {
        for (e in effects) when (e) {
            is LiveEffect.Send -> ws.send(e.json)
            is LiveEffect.PlayAudio -> audio.write(e.pcm)
            LiveEffect.StopPlayback -> {}
            is LiveEffect.Phase, is LiveEffect.Log, LiveEffect.Reconnect -> {}
            is LiveEffect.RunTool -> {
                toolCalls += e.name
                handle(coordinator.onToolResult(e.id, e.name, tools.execute(e.name, e.args)))
            }
            is LiveEffect.CheckSafety -> {
                val a = Safety.classify(e.text)
                safety += a
                handle(coordinator.onSafety(a, Safety.guidanceFor(a, region)))
            }
        }
    }

    /** Processes server messages until [turns] more model turns have completed. Returns false on close/timeout. */
    fun awaitTurns(turns: Int = 1, timeoutSec: Long = 60): Boolean {
        val target = turnCompletes + turns
        val deadline = System.currentTimeMillis() + timeoutSec * 1000
        while (turnCompletes < target) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return false
            val msg = inbox.poll(left, TimeUnit.MILLISECONDS) ?: return false
            if (msg == CLOSED) return false
            val obj = runCatching { AppJson.parseToJsonElement(msg).jsonObject }.getOrNull()
            obj?.let { o ->
                val sc = o["serverContent"] as? JsonObject
                trace += if (sc != null) "serverContent:" + sc.keys.joinToString(",") + (sc["outputTranscription"]?.let { " out=" + it.toString().take(60) } ?: "")
                else o.keys.joinToString(",")
            }
            handle(coordinator.onServerMessage(msg))
            val content = obj?.get("serverContent") as? JsonObject
            if (content?.get("turnComplete")?.toString() == "true") turnCompletes++
        }
        return true
    }

    /** Streams 16 kHz mono PCM as the microphone would (40 ms chunks), then silence so the turn ends. */
    fun speak(pcm16k: ByteArray) {
        val chunk = 1280
        var i = 0
        while (i < pcm16k.size) {
            val end = minOf(i + chunk, pcm16k.size)
            ws.send(coordinator.audioMessage(pcm16k.copyOfRange(i, end)))
            i = end
            Thread.sleep(20)
        }
        val silence = ByteArray(chunk)
        repeat(50) { ws.send(coordinator.audioMessage(silence)); Thread.sleep(40) } // 2 s of quiet
    }

    /** Sends typed text as the user's turn (no speech synthesis needed). */
    fun say(text: String) { ws.send(LiveCoordinator.textMessage(text)) }

    /** The speaker finished playing what Haven said. */
    fun playbackIdle() = handle(coordinator.onPlaybackIdle())

    /** One tick of the quiet-user timer; true if it nudged Haven. */
    fun tick(): Boolean {
        val effects = coordinator.onTick()
        handle(effects)
        return effects.any { it is LiveEffect.Send }
    }

    override fun close() {
        ws.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val CLOSED = "__closed__"

        /** Linear resample of 16-bit mono PCM (e.g. 24 kHz TTS output to 16 kHz microphone input). */
        fun resample(pcm: ByteArray, from: Int, to: Int): ByteArray {
            val n = pcm.size / 2
            val outN = (n.toLong() * to / from).toInt()
            val out = ByteArray(outN * 2)
            for (k in 0 until outN) {
                val pos = k.toDouble() * from / to
                val i = pos.toInt().coerceAtMost(n - 2)
                val f = pos - i
                val a = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xff)).toShort()
                val b = ((pcm[2 * i + 3].toInt() shl 8) or (pcm[2 * i + 2].toInt() and 0xff)).toShort()
                val v = (a * (1 - f) + b * f).toInt()
                out[2 * k] = v.toByte(); out[2 * k + 1] = (v shr 8).toByte()
            }
            return out
        }
    }
}
