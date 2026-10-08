package app.haven.companion.core.audio

/**
 * Six procedurally generated soundscapes. They are synthesised on the phone, so
 * they never loop audibly, need no downloads and carry no licensing questions.
 */
enum class SoundId(val label: String, val emoji: String, /** Balances loudness between sounds. */ val level: Float) {
    AIR("Air", "🌬️", 2.0f),
    RAIN("Rain", "🌧️", 1.9f),
    BIRDS("Birds", "🐦", 5.0f),
    RIVER("River", "🏞️", 2.3f),
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
            SoundId.RAIN -> Rain(sampleRate, seed)
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

/**
 * Rain: a steady, bright hiss of rainfall made of hundreds of tiny droplet ticks,
 * with the odd heavier drop nearby and an occasional drip into a puddle. Unlike
 * the river there is no bubbling and no surging: rain is even and broadband.
 */
class Rain(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val hiss = WhiteNoise(rng)
    private val hissHp = Biquad(sr).highpass(1200f)
    private val hissLp = Biquad(sr).lowpass(9000f)
    private val patter = PinkNoise(rng)
    private val patterLp = Biquad(sr).lowpass(600f)
    private val drops = ArrayList<Crackle>()
    private val ticks = Every(0.002f, 0.008f) { drops += Crackle(rng, sampleRate, rng.range(0.05f, 0.2f)) }
    private val heavy = Every(0.05f, 0.25f) { drops += Crackle(rng, sampleRate, rng.range(0.3f, 0.5f)) }
    private val puddle = Every(0.6f, 2.0f) {
        val f = rng.range(1800f, 3200f)
        blips += Blip(sampleRate, f, f * 0.8f, 0.06f, 0.04f)
    }
    override fun next(): Float {
        ticks.tick(); heavy.tick(); puddle.tick()
        var s = hissLp.process(hissHp.process(hiss.next())) * 0.35f + patterLp.process(patter.next()) * 0.15f
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            s += d.next()
            if (d.done) it.remove()
        }
        return s + renderBlips()
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

/**
 * River: a small, gentle stream tumbling over stones, like a tiny waterfall.
 * A soft, bright rush of water that swells a little, a light gurgle, and lots of
 * tiny bubbles. Each bubble is a short note whose pitch rises as it pops
 * (the way real air bubbles in water ring), which is what makes water sound wet.
 */
class River(sr: Float, seed: Int) : Soundscape(sr, seed) {
    private val rush = PinkNoise(rng)
    private val rushHp = Biquad(sr).highpass(250f)
    private val rushLp = Biquad(sr).lowpass(5000f)
    private val surge = Drift(rng, 0.7f, 1.0f, 0.6f, sr)
    private val gurgleNoise = WhiteNoise(rng)
    private val gurgle = Biquad(sr)
    private val gurgleCenter = Drift(rng, 900f, 2600f, 6f, sr)
    private val body = BrownNoise(rng)
    private val bodyLp = Biquad(sr).lowpass(400f)
    private val bubbles = Every(0.008f, 0.045f) {
        val f = rng.range(600f, 2200f)
        blips += Blip(sampleRate, f, f * rng.range(1.6f, 2.4f), rng.range(0.015f, 0.04f), rng.range(0.03f, 0.08f))
    }
    private var n = 0
    override fun next(): Float {
        bubbles.tick()
        val c = gurgleCenter.next()
        if (n++ % 32 == 0) gurgle.bandpass(c, 1.2f)
        val water = rushLp.process(rushHp.process(rush.next())) * 0.5f * surge.next()
        return water + gurgle.process(gurgleNoise.next()) * 0.25f + bodyLp.process(body.next()) * 0.25f + renderBlips()
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
