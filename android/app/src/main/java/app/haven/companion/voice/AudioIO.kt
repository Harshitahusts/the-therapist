package app.haven.companion.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Microphone capture: 16 kHz, 16-bit mono PCM (what Gemini Live expects), using the
 * voice-call input path with the platform echo canceller so the companion's own
 * voice from the speaker is not heard as the user talking.
 */
class MicRecorder(private val onChunk: (ByteArray) -> Unit, private val onLevel: (Float) -> Unit) {
    @Volatile private var running = false
    @Volatile var muted = false
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val effects = mutableListOf<AudioEffect>()

    @SuppressLint("MissingPermission") // checked by the UI before a conversation starts
    fun start() {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate * 2 / 2),
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        val session = rec.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.let { it.setEnabled(true); effects += it }
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.let { it.setEnabled(true); effects += it }
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.let { it.setEnabled(true); effects += it }
        record = rec
        running = true
        rec.startRecording()
        worker = thread(name = "haven-mic") {
            val buf = ByteArray(rate * 2 / 25) // 40 ms
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    val chunk = buf.copyOf(n)
                    if (muted) onLevel(0f) else {
                        onLevel(micLevel(chunk))
                        onChunk(chunk)
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        effects.forEach { runCatching { it.release() } }
        effects.clear()
        record?.let { r -> runCatching { r.stop() }; r.release() }
        record = null
    }
}

/**
 * Plays the companion's voice: 24 kHz, 16-bit mono PCM streamed from Gemini.
 * [flush] cuts it off instantly (user interrupted, or a safety re-steer).
 */
class SpeakerPlayer(private val onIdle: () -> Unit) {
    private val rate = 24_000
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = true
    @Volatile private var flushRequested = false
    private var framesWritten = 0L
    private var idleNotified = true

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 2)
        )
        .build()

    private val worker = thread(name = "haven-speaker") {
        track.play()
        while (running) {
            if (flushRequested) {
                flushRequested = false
                runCatching { track.flush(); track.play() }
                framesWritten = 0
            }
            val chunk = queue.poll(40, TimeUnit.MILLISECONDS)
            if (chunk != null) {
                if (flushRequested) continue
                track.write(chunk, 0, chunk.size)
                framesWritten += chunk.size / 2
                idleNotified = false
            } else if (!idleNotified && track.playbackHeadPosition.toLong() >= framesWritten) {
                idleNotified = true
                onIdle()
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        queue.offer(pcm)
    }

    fun flush() {
        queue.clear()
        flushRequested = true
        runCatching { track.pause() } // silence now; the worker flushes the buffer
        idleNotified = false
    }

    fun release() {
        running = false
        queue.clear()
        worker.join(500)
        runCatching { track.stop() }
        track.release()
    }
}
