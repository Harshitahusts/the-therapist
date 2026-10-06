package app.haven.companion.voice

import android.content.Context
import app.haven.companion.data.ApiException
import app.haven.companion.data.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

/**
 * Audio + event connection to the OpenAI Realtime API over WebRTC.
 *
 * The phone sends microphone audio and receives the model's voice directly;
 * WebRTC handles echo cancellation, jitter and interruption. Authentication
 * uses a short-lived client secret minted by our backend, so the real API
 * key never touches the device.
 */
class WebRtcVoiceLink(
    private val context: Context,
    private val http: OkHttpClient,
    private val listener: Listener,
) {
    interface Listener {
        fun onDataChannelOpen()
        fun onServerEvent(json: String)
        fun onConnectionLost()
        fun onMicLevel(level: Float)
    }

    private var factory: PeerConnectionFactory? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var peer: PeerConnection? = null
    private var events: DataChannel? = null
    private var micTrack: AudioTrack? = null
    @Volatile private var closed = false

    suspend fun connect(callsUrl: String, clientSecret: String) {
        ensureInitialized(context)
        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setSamplesReadyCallback { samples -> listener.onMicLevel(micLevel(samples.data)) }
            .createAudioDeviceModule()
        audioModule = adm
        val f = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
        factory = f

        val gatheringDone = CompletableDeferred<Unit>()
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringDone.complete(Unit)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (!closed && (state == PeerConnection.PeerConnectionState.FAILED ||
                        state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                        state == PeerConnection.PeerConnectionState.CLOSED)
                ) listener.onConnectionLost()
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                // Remote audio tracks are played by the audio device module automatically.
            }
        }) ?: throw IllegalStateException("Could not create peer connection")
        peer = pc

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val track = f.createAudioTrack("mic", f.createAudioSource(audioConstraints))
        micTrack = track
        pc.addTransceiver(track, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))

        val dc = pc.createDataChannel("oai-events", DataChannel.Init())
        events = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (dc.state() == DataChannel.State.OPEN) listener.onDataChannelOpen()
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                listener.onServerEvent(String(bytes, Charsets.UTF_8))
            }
        })

        val offer = pc.awaitSdp { obs -> pc.createOffer(obs, MediaConstraints()) }
        pc.awaitSet { obs -> pc.setLocalDescription(obs, offer) }
        // Include our ICE candidates in the offer (no trickle ICE with this endpoint).
        withTimeoutOrNull(3_000) { gatheringDone.await() }
        val localSdp = pc.localDescription?.description ?: offer.description

        val request = Request.Builder()
            .url(callsUrl)
            .header("Authorization", "Bearer $clientSecret")
            .post(localSdp.toRequestBody("application/sdp".toMediaType()))
            .build()
        val answer = http.newCall(request).await().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, "Voice service returned ${resp.code}")
            body
        }
        pc.awaitSet { obs -> pc.setRemoteDescription(obs, SessionDescription(SessionDescription.Type.ANSWER, answer)) }
    }

    fun send(json: String): Boolean {
        val dc = events ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        return dc.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false))
    }

    fun setMicEnabled(enabled: Boolean) {
        micTrack?.setEnabled(enabled)
    }

    fun close() {
        closed = true
        runCatching { events?.unregisterObserver() }
        runCatching { events?.close() }
        runCatching { peer?.close() }
        runCatching { events?.dispose() }
        runCatching { peer?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { audioModule?.release() }
        events = null; peer = null; factory = null; audioModule = null; micTrack = null
    }

    private suspend fun PeerConnection.awaitSdp(block: (SdpObserver) -> Unit): SessionDescription {
        val result = CompletableDeferred<SessionDescription>()
        block(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { result.complete(sdp) }
            override fun onCreateFailure(error: String?) { result.completeExceptionally(IllegalStateException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        })
        return result.await()
    }

    private suspend fun PeerConnection.awaitSet(block: (SdpObserver) -> Unit) {
        val result = CompletableDeferred<Unit>()
        block(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onSetFailure(error: String?) { result.completeExceptionally(IllegalStateException(error)) }
        })
        result.await()
    }

    companion object {
        @Volatile private var initialized = false

        private fun ensureInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .createInitializationOptions()
                )
                initialized = true
            }
        }
    }
}
