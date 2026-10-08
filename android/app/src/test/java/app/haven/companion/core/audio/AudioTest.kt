package app.haven.companion.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class AudioTest {
    private val sr = 24_000f

    private fun stats(xs: FloatArray): Pair<Float, Float> {
        var peak = 0f; var sum = 0.0
        for (x in xs) { assertFalse("NaN/inf", x.isNaN() || x.isInfinite()); peak = maxOf(peak, abs(x)); sum += x * x }
        return peak to sqrt(sum / xs.size).toFloat()
    }

    @Test
    fun everySoundscapeIsAudibleBoundedAndDeterministic() {
        for (id in SoundId.entries) {
            val a = Soundscape.create(id, sr)
            val b = Soundscape.create(id, sr)
            val xs = FloatArray(24_000 * 12) { a.next() }
            val ys = FloatArray(24_000 * 12) { b.next() }
            val (peak, rms) = stats(xs)
            assertTrue("$id too quiet: rms=$rms", rms > 0.005f)
            assertTrue("$id too loud: peak=$peak", peak < 2.5f)
            assertTrue("$id not deterministic", xs.contentEquals(ys))
        }
    }

    @Test
    fun soundscapesSoundDifferent() {
        val rms = SoundId.entries.associateWith { id -> val s = Soundscape.create(id, sr); stats(FloatArray(48_000) { s.next() }).second }
        assertEquals(SoundId.entries.size, rms.values.map { (it * 1000).toInt() }.toSet().size)
    }

    /** Brightness: energy of the sample-to-sample change relative to the signal (high for hiss, low for rumble). */
    private fun brightness(id: SoundId): Double {
        val s = Soundscape.create(id, sr)
        val xs = FloatArray(24_000 * 6) { s.next() }.drop(24_000)
        var e = 0.0; var d = 0.0
        for (i in 1 until xs.size) { e += xs[i] * xs[i]; val diff = xs[i] - xs[i - 1]; d += diff * diff }
        return d / e
    }

    @Test
    fun rainAndRiverSoundClearlyDifferent() {
        val rain = brightness(SoundId.RAIN)
        val river = brightness(SoundId.RIVER)
        assertTrue("rain should be much brighter than the river: rain=$rain river=$river", rain > river * 2)
        assertEquals("Rain", SoundId.RAIN.label)
    }

    @Test
    fun echoRepeatsTheVoiceQuietlyAfterTheDelay() {
        val echo = Echo(sr, delaySec = 0.1f, feedback = 0.25f, wet = 0.2f)
        val out = FloatArray(12_000) { i -> echo.process(if (i == 0) 1f else 0f) }
        val delay = (0.1f * sr).toInt()
        assertEquals(0f, out.take(delay).maxOf { abs(it) }, 0f)
        assertTrue(abs(out[delay]) in 0.15f..0.2f)
        val second = out.drop(delay * 2 - 5).take(40).maxOf { abs(it) }
        assertTrue("second repeat is much quieter: $second", second in 0.005f..0.06f)
        echo.clear()
        assertEquals(0f, echo.process(0f), 0f)
    }

    @Test
    fun mixerPlaysVoiceWithEchoThenGoesQuiet() {
        val m = Mixer()
        assertFalse(m.isActive())
        m.enqueueVoice(ShortArray(2400) { 10_000 })
        val out = ShortArray(24_000)
        m.render(out)
        assertTrue(abs(out[10] - 10_000) <= 2)
        // The voice ends at 2400; its echo (130 ms = 3120 frames later) is still sounding at 4000.
        assertTrue("echo tail present after the voice", abs(out[4000].toInt()) in 1200..2200)
        assertEquals(2399L, m.lastVoiceFrame)
        assertFalse(m.hasPendingVoice())
        repeat(5) { m.render(out) }
        assertFalse("echo has died away", m.isActive())
    }

    @Test
    fun flushStopsVoiceImmediately() {
        val m = Mixer()
        m.enqueueVoice(ShortArray(24_000) { 8_000 })
        val out = ShortArray(1000)
        m.render(out)
        m.flushVoice()
        m.render(out)
        assertTrue(out.all { it.toInt() == 0 })
    }

    @Test
    fun soundsFadeInDipUnderVoiceAndFadeOut() {
        val m = Mixer()
        m.setSounds(setOf(SoundId.RIVER, SoundId.BIRDS))
        assertEquals(setOf(SoundId.RIVER, SoundId.BIRDS), m.activeSounds())
        val block = ShortArray(24_000)
        m.render(block) // fade in
        m.render(block)
        val alone = rms(block)
        m.enqueueVoice(ShortArray(48_000)) // silent "voice" so only the ducking changes level
        m.render(block); m.render(block)
        val ducked = rms(block)
        assertTrue("ducked $ducked vs $alone", ducked < alone * 0.7)
        m.flushVoice()
        m.setSounds(emptySet())
        repeat(4) { m.render(block) }
        assertTrue(m.activeSounds().isEmpty())
        assertFalse(m.isActive())
    }

    @Test
    fun everythingAtFullVolumeNeverClipsHard() {
        val m = Mixer()
        m.volume = 1f
        m.setSounds(SoundId.entries.toSet())
        m.enqueueVoice(ShortArray(24_000) { if (it % 2 == 0) 32_000 else -32_000 })
        val out = ShortArray(24_000)
        m.render(out)
        assertTrue(out.all { abs(it.toInt()) <= 32_735 })
        assertEquals(0.5f, Mixer.softClip(0.5f), 0f)
        assertTrue(Mixer.softClip(5f) < 1f && Mixer.softClip(-5f) > -1f)
    }

    private fun rms(xs: ShortArray) = sqrt(xs.sumOf { (it / 32768.0) * (it / 32768.0) } / xs.size)
}
