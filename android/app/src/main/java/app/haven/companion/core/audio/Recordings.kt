package app.haven.companion.core.audio

/**
 * Real recordings decoded at start-up (24 kHz mono floats). When a recording is
 * available the matching soundscape uses it; otherwise the synthesised version plays.
 */
object SampleBank {
    @Volatile var birdsAmbience: FloatArray? = null
    @Volatile var birdsSong: FloatArray? = null
}

/**
 * Birds from real forest recordings (Mixkit, used under the Mixkit Free License):
 * a seamlessly looping dawn ambience, plus a nearby bird that sings now and then,
 * each time at a slightly different pitch, loudness and moment so it never feels repeated.
 */
class RecordedBirds(
    sr: Float,
    seed: Int,
    private val ambience: FloatArray,
    private val song: FloatArray?,
) : Soundscape(sr, seed) {
    private var pos = (rng.next() * ambience.size).toInt() // start somewhere different each time
    private var songPos = -1.0
    private var songRate = 1.0
    private var songGain = 0f
    private val nearby = Every(14f, 38f) {
        if (song != null && songPos < 0) {
            songPos = 0.0
            songRate = rng.range(0.9f, 1.1f).toDouble()   // a little higher or lower each time
            songGain = rng.range(0.5f, 1.0f)
        }
    }

    override fun next(): Float {
        nearby.tick()
        var s = ambience[pos] * AMBIENCE_GAIN
        if (++pos >= ambience.size) pos = 0
        val clip = song
        if (clip != null && songPos >= 0) {
            val i = songPos.toInt()
            if (i + 1 < clip.size) {
                val frac = (songPos - i).toFloat()
                s += (clip[i] * (1 - frac) + clip[i + 1] * frac) * SONG_GAIN * songGain
                songPos += songRate
            } else songPos = -1.0
        }
        return s
    }

    private companion object {
        // Recordings are normalised loud; these bring them in line with the other soundscapes.
        const val AMBIENCE_GAIN = 0.31f
        const val SONG_GAIN = 0.15f
    }
}
