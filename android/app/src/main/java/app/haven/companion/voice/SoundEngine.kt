package app.haven.companion.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.haven.companion.core.audio.Mixer
import app.haven.companion.core.audio.SoundId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/**
 * The app's single audio output: the companion's voice (with its soft echo) and
 * the soundscapes, mixed into one stream. Using one stream matters: during a
 * conversation it is the voice-call stream, so the phone's echo canceller
 * removes both the voice and the soundscapes from the microphone.
 */
class SoundEngine {
    private val mixer = Mixer(SAMPLE_RATE)

    private val _sounds = MutableStateFlow<Set<SoundId>>(emptySet())
    val sounds: StateFlow<Set<SoundId>> = _sounds

    private val _volume = MutableStateFlow(mixer.volume)
    val volume: StateFlow<Float> = _volume

    /** Called (on the audio thread) once the speaker has finished playing the companion's last words. */
    @Volatile var onVoiceIdle: (() -> Unit)? = null

    @Volatile private var conversation = false
    @Volatile private var voiceIdleNotified = true
    private var worker: Thread? = null
    private val lock = Any()

    fun setSounds(ids: Set<SoundId>) {
        _sounds.value = ids
        mixer.setSounds(ids)
        ensureRunning()
    }

    fun toggle(id: SoundId) = setSounds(if (id in _sounds.value) _sounds.value - id else _sounds.value + id)

    fun setVolume(v: Float) {
        mixer.volume = v.coerceIn(0f, 1f)
        _volume.value = mixer.volume
    }

    /** Switch to (or from) the voice-call stream used during conversations. */
    fun setConversationMode(on: Boolean) {
        conversation = on
        if (!on) mixer.flushVoice()
        ensureRunning()
    }

    /** 24 kHz, 16-bit little-endian mono PCM from Gemini. */
    fun enqueueVoice(pcm: ByteArray) {
        val shorts = ShortArray(pcm.size / 2) { i -> ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xff)).toShort() }
        mixer.enqueueVoice(shorts)
        voiceIdleNotified = false
        ensureRunning()
    }

    fun flushVoice() {
        mixer.flushVoice()
        voiceIdleNotified = false // report idle once the speaker has caught up
    }

    private fun ensureRunning() {
        synchronized(lock) {
            if (worker?.isAlive != true) worker = thread(name = "haven-audio") { audioLoop() }
        }
    }

    private fun newTrack(voiceCall: Boolean): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(if (voiceCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(if (voiceCall) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE / 5 * 2)) // ~200 ms
            .build()
    }

    private fun audioLoop() {
        val block = ShortArray(BLOCK)
        var mode = conversation
        var track = newTrack(mode)
        var trackStart = mixer.framesRendered
        var quietBlocks = 0
        track.play()
        try {
            while (true) {
                if (mode != conversation) {
                    runCatching { track.stop() }; track.release()
                    mode = conversation
                    track = newTrack(mode)
                    trackStart = mixer.framesRendered
                    track.play()
                }
                if (!conversation && !mixer.isActive()) {
                    if (++quietBlocks > IDLE_BLOCKS_BEFORE_STOP) break
                } else quietBlocks = 0

                mixer.render(block)
                track.write(block, 0, block.size)

                if (!voiceIdleNotified && !mixer.hasPendingVoice()) {
                    val played = trackStart + track.playbackHeadPosition.toLong()
                    if (played >= mixer.lastVoiceFrame) {
                        voiceIdleNotified = true
                        onVoiceIdle?.invoke()
                    }
                }
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(lock) { worker = null }
            // A request may have arrived while we were shutting down.
            if (conversation || mixer.isActive()) ensureRunning()
        }
    }

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val BLOCK = 480 // 20 ms
        private const val IDLE_BLOCKS_BEFORE_STOP = 50 // 1 s of silence
    }
}
