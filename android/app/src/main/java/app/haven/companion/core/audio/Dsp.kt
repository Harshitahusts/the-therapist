package app.haven.companion.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Small, allocation-free DSP building blocks for the on-device soundscapes. */

/** Fast deterministic random numbers (xorshift32). */
class Rng(seed: Int = 0x2545F491) {
    private var s = if (seed == 0) 1 else seed
    fun nextInt(): Int { s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5); return s }
    /** Uniform in [0, 1). */
    fun next(): Float = (nextInt() ushr 8) / 16_777_216f
    /** Uniform in [-1, 1). */
    fun bipolar(): Float = next() * 2f - 1f
    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * next()
    fun chance(p: Float): Boolean = next() < p
}

class WhiteNoise(private val rng: Rng) {
    fun next(): Float = rng.bipolar() * 0.5f
}

/** Pink (1/f) noise, Paul Kellet's refined filter. */
class PinkNoise(private val rng: Rng) {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f
    private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99886f * b0 + w * 0.0555179f; b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f; b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f; b5 = -0.7616f * b5 - w * 0.0168980f
        val out = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
        b6 = w * 0.115926f
        return out
    }
}

/** Brown (red) noise: a leaky integrator of white noise. */
class BrownNoise(private val rng: Rng) {
    private var last = 0f
    fun next(): Float {
        last = (last + 0.02f * rng.bipolar()) / 1.02f
        return last * 3.5f
    }
}

/** RBJ-cookbook biquad. Call a setter, then [process] per sample. */
class Biquad(private val sampleRate: Float) {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / a0).toFloat(); b1 = (nb1 / a0).toFloat(); b2 = (nb2 / a0).toFloat()
        a1 = (na1 / a0).toFloat(); a2 = (na2 / a0).toFloat()
    }

    private fun w0(freq: Float) = 2 * PI * freq.coerceIn(10f, sampleRate * 0.45f) / sampleRate

    fun lowpass(freq: Float, q: Float = 0.707f) = apply {
        val w = w0(freq); val c = cos(w); val alpha = sin(w) / (2 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun highpass(freq: Float, q: Float = 0.707f) = apply {
        val w = w0(freq); val c = cos(w); val alpha = sin(w) / (2 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Band-pass with 0 dB peak gain. */
    fun bandpass(freq: Float, q: Float) = apply {
        val w = w0(freq); val c = cos(w); val alpha = sin(w) / (2 * q)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** A value that drifts smoothly toward random targets: slow, natural-feeling modulation. */
class Drift(private val rng: Rng, private val lo: Float, private val hi: Float, private val changesPerSecond: Float, private val sampleRate: Float) {
    var value = rng.range(lo, hi); private set
    private var target = rng.range(lo, hi)
    private val coef = 1f - exp(-2.0 * PI * changesPerSecond / sampleRate).toFloat()
    fun next(): Float {
        value += (target - value) * coef
        if (rng.next() < changesPerSecond / sampleRate) target = rng.range(lo, hi)
        return value
    }
}

/** Sine LFO in [-1, 1]. */
class Lfo(private val freq: Float, private val sampleRate: Float, phase: Float = 0f) {
    private var ph = phase.toDouble()
    fun next(): Float {
        ph += 2 * PI * freq / sampleRate
        if (ph > 2 * PI) ph -= 2 * PI
        return sin(ph).toFloat()
    }
}

/** A short sine "note" with an exponential pitch glide and a quick attack and decay (chirps, drops, chimes). */
class Blip(
    private val sampleRate: Float,
    freqFrom: Float,
    private val freqTo: Float,
    durationSec: Float,
    private val level: Float,
    startDelaySec: Float = 0f,
) {
    private val total = (durationSec * sampleRate).toInt().coerceAtLeast(1)
    private val attack = (minOf(0.02f, durationSec / 3) * sampleRate).toInt().coerceAtLeast(1)
    private var delay = (startDelaySec * sampleRate).toInt()
    private var i = 0
    private var phase = 0.0
    private val f0 = freqFrom
    val done: Boolean get() = i >= total

    fun next(): Float {
        if (delay > 0) { delay--; return 0f }
        if (done) return 0f
        val t = i.toFloat() / total
        val f = f0 * Math.pow((freqTo / f0).toDouble(), t.toDouble())
        phase += 2 * PI * f / sampleRate
        val env = if (i < attack) i.toFloat() / attack else exp(-5.0 * (i - attack) / (total - attack).coerceAtLeast(1)).toFloat()
        i++
        return sin(phase).toFloat() * env * level
    }
}

/** A tiny burst of filtered noise: a crackle in a fire, a twig snapping. */
class Crackle(private val rng: Rng, private val sampleRate: Float, private val level: Float) {
    private val total = (rng.range(0.002f, 0.008f) * sampleRate).toInt().coerceAtLeast(2)
    private val hp = Biquad(sampleRate).highpass(rng.range(900f, 3400f))
    private var i = 0
    val done: Boolean get() = i >= total
    fun next(): Float {
        if (done) return 0f
        val env = 1f - i.toFloat() / total
        i++
        return hp.process(rng.bipolar() * env * env) * level
    }
}
