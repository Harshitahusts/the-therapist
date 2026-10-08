package app.haven.companion.core.audio

/**
 * Six procedurally generated soundscapes. They are synthesised on the phone, so
 * they never loop audibly, need no downloads and carry no licensing questions.
 */
enum class SoundId(val label: String, val emoji: String, /** Balances loudness between sounds. */ val level: Float) {
    AIR("Air", "🌬️", 2.0f),
    WATER("Water", "💧", 3.4f),
    BIRDS("Birds", "🐦", 5.0f),
    RIVER("River", "🏞️", 1.45f),
    BONFIRE("Bonfire", "🔥", 1.2f),
    FRESH("Fresh", "🌿", 2.3f),
}

/** One soundscape: renders mono float samples, roughly in [-1, 1]. */
abstract class Soundscape(protected val sampleRate: Float, seed: Int) {
    protected val rng = Rng(seed)
    abstract fun next(): Float

    /** Fires a callback at random intervals between [minSec] and [maxSec]. */
    protected inner class Every(private val minSec: Float, private val maxSec: Float, private val action: () -> Unit) {
        private var countdown = (rng.range(0f, maxSec) * sampleRate).toInt()
        fun tick() {
            if (--countdown <= 0) {
                action()
                countdown = (rng.range(minSec, maxSec) * sampleRate).toInt()
            }
        }
    }

    protected val blips = ArrayList<Blip>()
    protected fun renderBlips(): Float {
        var s = 0f
        val it = blips.iterator()
        while (it.hasNext()) {
            val b = it.next()
            s += b.next()
            if (b.done) it.remove()
        }
        return s
    }

    companion object {
        fun create(id: SoundId, sampleRate: Float, seed: Int = id.ordinal * 7919 + 17): Soundscape = when (id) {
            SoundId.AIR -> Air(sampleRate, seed)
            SoundId.WATER -> Water(sampleRate, seed)
            SoundId.BIRDS -> Birds(sampleRate, seed)
            SoundId.RIVER -> River(sampleRate, seed)
            SoundId.BONFIRE -> Bonfire(sampleRate, seed)
            SoundId.FRESH -> Fresh(sampleRate, seed)
        }
    }
}

/** Wind: pink noise through a slowly wandering band-pass, with gusts. */
class Air(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val noise = PinkNoise(rng)
    private val bp = Biquad(sr)
    private val center = Drift(rng, 280f, 820f, 0.12f, sr)
    private val gust = Drift(rng, 0.3f, 1.0f, 0.18f, sr)
    private var n = 0
    override fun next(): Float {
        val c = center.next()
        if (n++ % 64 == 0) bp.bandpass(c, 0.7f)
        return bp.process(noise.next()) * gust.next() * 1.6f
    }
}

/** Soft rain: a bright noise bed plus scattered droplets. */
class Water(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val noise = PinkNoise(rng)
    private val hp = Biquad(sr).highpass(700f)
    private val lp = Biquad(sr).lowpass(6500f)
    private val drops = Every(0.03f, 0.16f) {
        val f = rng.range(2200f, 4800f)
        blips += Blip(sampleRate, f, f * rng.range(0.55f, 0.75f), 0.05f, rng.range(0.05f, 0.11f))
    }
    override fun next(): Float {
        drops.tick()
        return lp.process(hp.process(noise.next())) * 0.45f + renderBlips()
    }
}

/** Birdsong: short phrases from two kinds of bird, with natural pauses, over a faint breeze. */
class Birds(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val bed = PinkNoise(rng)
    private val lp = Biquad(sr).lowpass(900f)
    private val phrases = Every(1.6f, 5.2f) {
        val warbler = rng.chance(0.5f)
        val notes = 2 + (rng.next() * 5).toInt()
        val base = if (warbler) 3200f else 2300f
        for (k in 0 until notes) {
            val f = base + rng.range(0f, 900f)
            if (warbler) blips += Blip(sampleRate, f, f * 1.45f, 0.07f, 0.09f, k * 0.11f)
            else blips += Blip(sampleRate, f * 1.3f, f * 0.85f, 0.13f, 0.08f, k * 0.19f)
        }
    }
    override fun next(): Float {
        phrases.tick()
        return lp.process(bed.next()) * 0.07f + renderBlips()
    }
}

/** River: deep flowing brown noise plus a bubbling mid band. */
class River(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val low = BrownNoise(rng)
    private val lp = Biquad(sr).lowpass(650f)
    private val white = WhiteNoise(rng)
    private val bp = Biquad(sr)
    private val bubble = Drift(rng, 500f, 1400f, 11f, sr)
    private var n = 0
    override fun next(): Float {
        val f = bubble.next()
        if (n++ % 32 == 0) bp.bandpass(f, 2.2f)
        return lp.process(low.next()) * 0.6f + bp.process(white.next()) * 0.3f
    }
}

/** Bonfire: a low rumble with clustered crackles and the odd pop. */
class Bonfire(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val rumble = BrownNoise(rng)
    private val lp = Biquad(sr).lowpass(260f)
    private val crackles = ArrayList<Crackle>()
    private var pending = 0
    private var gap = 0
    private val spark = Every(0.06f, 0.42f) { pending += if (rng.chance(0.25f)) 3 + (rng.next() * 4).toInt() else 1 }
    override fun next(): Float {
        spark.tick()
        if (pending > 0 && --gap <= 0) {
            crackles += Crackle(rng, sampleRate, 0.35f * rng.range(0.3f, 1.3f))
            pending--
            gap = (rng.range(0.008f, 0.038f) * sampleRate).toInt()
        }
        var s = lp.process(rumble.next()) * 0.8f
        val it = crackles.iterator()
        while (it.hasNext()) {
            val c = it.next()
            s += c.next()
            if (c.done) it.remove()
        }
        return s
    }
}

/** Fresh morning forest: leaves rustling, a light breeze, and a distant chime now and then. */
class Fresh(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val leaves = WhiteNoise(rng)
    private val hp = Biquad(sr).highpass(3200f)
    private val rustle = Lfo(0.17f, sr)
    private val rustleDrift = Drift(rng, 0.3f, 1.0f, 0.5f, sr)
    private val breeze = PinkNoise(rng)
    private val lp = Biquad(sr).lowpass(500f)
    private val chime = Every(6f, 13f) {
        listOf(1f, 1.5f, 2.01f).forEachIndexed { i, m ->
            blips += Blip(sampleRate, 784f * m, 784f * m, 2.8f - i * 0.6f, 0.03f, i * 0.04f)
        }
    }
    override fun next(): Float {
        chime.tick()
        val leafGain = (0.06f + 0.05f * rustle.next()) * rustleDrift.next()
        return hp.process(leaves.next()) * leafGain * 2f + lp.process(breeze.next()) * 0.35f + renderBlips()
    }
}
