package app.haven.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicLevelTest {
    @Test
    fun silenceIsZero() = assertEquals(0f, micLevel(ByteArray(960)), 0f)

    @Test
    fun loudSignalIsClamped() {
        val pcm = ByteArray(960) { i -> if (i % 2 == 0) 0x00 else 0x7f }
        assertEquals(1f, micLevel(pcm), 0f)
    }

    @Test
    fun quietSignalIsBetween() {
        val pcm = ByteArray(960) { i -> if (i % 2 == 0) 0x00 else 0x02 } // ~512 amplitude
        val level = micLevel(pcm)
        assertTrue(level > 0f && level < 1f)
    }
}
