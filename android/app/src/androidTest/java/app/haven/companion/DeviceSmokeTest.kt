package app.haven.companion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.haven.companion.core.Voices
import app.haven.companion.voice.AssetAudio
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on every supported Android version in CI: the app starts, the funnel works, bundled audio decodes. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun funnelOpensAndMovesForward() {
        compose.onNodeWithText("Let's begin").assertIsDisplayed().performClick()
        compose.onNodeWithText("Your name").performTextInput("Asha")
        compose.onNodeWithText("Nice to meet you →").performClick()
        compose.onNodeWithText("🤍 I'm not a therapist").assertIsDisplayed()
    }

    @Test
    fun bundledRecordingsDecodeOnThisDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (path in listOf("sounds/birds_song.ogg", "sounds/birds_ambience.ogg")) {
            assertTrue(path, AssetAudio.decodeMono(context, path).size > 24_000)
        }
        for (voice in Voices.CALM) {
            val clip = AssetAudio.decodeMono(context, "voices/${voice.name}.ogg")
            assertTrue(voice.name, clip.size > 24_000 * 3)
        }
    }
}
