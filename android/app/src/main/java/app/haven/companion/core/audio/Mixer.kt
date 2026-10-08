package app.haven.companion.core.audio

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp

/**
 * A soft, short echo for the companion's voice: one damped feedback delay,
 * like talking in a small clearing. Subtle on purpose.
 */
class Echo(sampleRate: Float, delaySec: Float = 0.13f, private val feedback: Float = 0.24f, private val wet: Float = 0.17f) {
    private val buf = FloatArray((delaySec * sampleRate).toInt().coerceAtLeast(1))
    private var pos = 0
    private val damp = Biquad(sampleRate).lowpass(3200f)

    /** Returns only the echo (wet) part for this input sample. */
    fun process(x: Float): Float {
        val delayed = buf[pos]
        buf[pos] = x + damp.process(delayed) * feedback
        pos = (pos + 1) % buf.size
        return delayed * wet
    }

    fun clear() {
        buf.fill(0f)
        pos = 0
    }
}

/**
 * Mixes the companion's voice (16-bit PCM chunks, with echo) and any number of
 * soundscapes into one output stream. Soundscapes fade in and out, and dip
 * while the companion is speaking. Not thread-safe: the audio thread calls
 * [render]; other threads post changes through the synchronized setters.
 */
class Mixer(val sampleRate: Int = 24_000) {
    private val sr = sampleRate.toFloat()
    private val voice = ArrayDeque<ShortArray>()
    private var voicePos = 0
    private val echo = Echo(sr)
    private var echoEnergy = 0f

    private class Layer(val sound: Soundscape, val level: Float) { var gain = 0f; var on = true }
    private val layers = LinkedHashMap<SoundId, Layer>()

    @Volatile var volume = 0.55f
    private var duck = 1f
    private val fadeCoef = 1f - exp(-1.0 / (0.35 * sampleRate)).toFloat()
    private val duckCoef = 1f - exp(-1.0 / (0.25 * sampleRate)).toFloat()

    /** Total frames rendered so far, and the frame at which the last voice sample was rendered. */
    var framesRendered = 0L; private set
    var lastVoiceFrame = -1L; private set

    @Synchronized fun setSounds(ids: Set<SoundId>) {
        for ((id, layer) in layers) layer.on = id in ids
        for (id in ids) if (id !in layers) layers[id] = Layer(Soundscape.create(id, sr), id.level)
    }

    @Synchronized fun activeSounds(): Set<SoundId> = layers.filterValues { it.on }.keys.toSet()

    @Synchronized fun enqueueVoice(pcm: ShortArray) { if (pcm.isNotEmpty()) voice.addLast(pcm) }

    /** Cut the voice off immediately (interruption). */
    @Synchronized fun flushVoice() {
        voice.clear()
        voicePos = 0
        echo.clear()
        echoEnergy = 0f
    }

    @Synchronized fun hasPendingVoice(): Boolean = voice.isNotEmpty()

    /** True while anything is still audible or about to be. */
    @Synchronized fun isActive(): Boolean = voice.isNotEmpty() || echoEnergy > 1e-4f || layers.isNotEmpty()

    @Synchronized fun render(out: ShortArray, frames: Int = out.size) {
        for (i in 0 until frames) {
            // Voice
            var v = 0f
            val chunk = voice.peekFirst()
            if (chunk != null) {
                v = chunk[voicePos] / 32768f
                if (++voicePos >= chunk.size) { voice.removeFirst(); voicePos = 0 }
                lastVoiceFrame = framesRendered
            }
            val e = echo.process(v)
            echoEnergy = echoEnergy * 0.9995f + abs(e) * 0.0005f

            // Soundscapes, dipped while the companion speaks
            val speaking = chunk != null
            duck += ((if (speaking) 0.45f else 1f) - duck) * duckCoef
            var amb = 0f
            if (layers.isNotEmpty()) {
                val it = layers.values.iterator()
                while (it.hasNext()) {
                    val l = it.next()
                    l.gain += ((if (l.on) 1f else 0f) - l.gain) * fadeCoef
                    if (!l.on && l.gain < 1e-3f) { it.remove(); continue }
                    amb += l.sound.next() * l.gain * l.level
                }
            }
            val mixed = v + e + amb * AMBIENT_LEVEL * volume * duck
            out[i] = (softClip(mixed) * 32767f).toInt().toShort()
            framesRendered++
        }
    }

    companion object {
        const val AMBIENT_LEVEL = 0.32f

        /** Gentle saturation above 0.8 instead of hard clipping when everything plays at once. */
        fun softClip(x: Float): Float {
            val a = abs(x)
            if (a <= 0.8f) return x
            val y = 0.8f + 0.2f * (1f - exp(-(a - 0.8f) / 0.2f))
            return if (x > 0) minOf(y, 0.999f) else -minOf(y, 0.999f)
        }
    }
}
