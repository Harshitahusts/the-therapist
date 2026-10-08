package app.haven.companion.core.audio

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class RecordedBirdsTest {
    private val sr = 24_000f

    @After
    fun clear() {
        SampleBank.birdsAmbience = null
        SampleBank.birdsSong = null
    }

    @Test
    fun usesTheRecordingWhenLoadedAndTheSynthOtherwise() {
        assertTrue(Soundscape.create(SoundId.BIRDS, sr) is Birds)
        SampleBank.birdsAmbience = FloatArray(1000) { 0.5f }
        assertTrue(Soundscape.create(SoundId.BIRDS, sr) is RecordedBirds)
    }

    @Test
    fun ambienceLoopsForeverAndTheSongVisitsNowAndThen() {
        val amb = FloatArray(2400) { 0.2f * sin(it * 0.05f) }
        val song = FloatArray(4800) { 0.9f * sin(it * 0.6f) }
        val birds = RecordedBirds(sr, 1, amb, song)
        // 60 s: the 0.1 s ambience loops hundreds of times; the song plays at least once.
        var songMoments = 0
        var peak = 0f
        repeat(24_000 * 60) {
            val x = birds.next()
            peak = maxOf(peak, abs(x))
            if (abs(x) > 0.2f * 0.31f + 0.01f) songMoments++
        }
        assertTrue("song heard: $songMoments", songMoments > 100)
        assertTrue("bounded: $peak", peak < 1f)
    }

    @Test
    fun mixerPlaysRecordedBirds() {
        SampleBank.birdsAmbience = FloatArray(24_000) { 0.5f * sin(it * 0.3f) }
        val m = Mixer()
        m.setSounds(setOf(SoundId.BIRDS))
        val out = ShortArray(48_000)
        m.render(out)
        assertTrue(out.drop(24_000).any { abs(it.toInt()) > 1000 })
    }
}
