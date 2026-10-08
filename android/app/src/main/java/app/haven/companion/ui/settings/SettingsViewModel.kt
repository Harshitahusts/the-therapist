package app.haven.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haven.companion.AppContainer
import app.haven.companion.core.CompanionData
import app.haven.companion.core.GeminiException
import app.haven.companion.core.Voices
import app.haven.companion.voice.VoiceSessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val data: CompanionData = CompanionData(),
    val autoStart: Boolean = true,
    val liveModel: String? = null,
    val voice: String = Voices.DEFAULT,
    val previewing: String? = null,
    val liveModels: List<String>? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

private const val PREVIEW_LINE =
    "Say softly, slowly and warmly, like a calm friend: Hi, I'm Haven. Let's take one slow breath together... " +
        "It's really good to have you here."

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<SettingsUiState> = _state

    private fun snapshot(base: SettingsUiState = SettingsUiState()) =
        base.copy(data = c.repo.data, autoStart = c.store.autoStart, liveModel = c.store.liveModel, voice = Voices.find(c.store.voice).name)

    private fun refresh(message: String? = _state.value.message) = _state.update { snapshot(it).copy(message = message) }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: GeminiException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(message = VoiceSessionController.NETWORK_ERROR) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        c.repo.updateSettings { it.copy(memoryEnabled = enabled) }
        refresh()
    }

    fun setCrisisRegion(region: String) {
        c.repo.updateSettings { it.copy(crisisRegion = region) }
        refresh()
    }

    fun setAutoStart(enabled: Boolean) {
        c.store.autoStart = enabled
        refresh()
    }

    fun deleteMemory(id: String) {
        c.repo.deleteMemory(id)
        refresh()
    }

    fun forgetEverything() {
        c.repo.forgetEverything()
        refresh("Done. I've forgotten everything you told me, including your check-ins.")
    }

    fun changeKey(key: String) = launchAction {
        val error = c.connectGeminiKey(key)
        refresh(error ?: "Gemini key updated.")
    }

    fun loadLiveModels() = launchAction {
        val models = c.liveModels()
        _state.update { it.copy(liveModels = models) }
    }

    fun setLiveModel(model: String) {
        c.store.liveModel = model
        refresh()
    }

    fun setVoice(name: String) {
        c.store.voice = name
        refresh()
    }

    /** Plays a short sample of [name] through the speaker, using a Gemini text-to-speech model. */
    fun previewVoice(name: String) {
        val tts = c.store.ttsModel
        if (tts == null) {
            _state.update { it.copy(message = "Voice previews aren't available with this key. You'll hear the voice in your next conversation.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(previewing = name, message = null) }
            try {
                val pcm = withContext(Dispatchers.IO) { c.gemini.speak(tts, name, PREVIEW_LINE) }
                c.sound.enqueueVoice(pcm)
            } catch (e: GeminiException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: IOException) {
                _state.update { it.copy(message = VoiceSessionController.NETWORK_ERROR) }
            } finally {
                _state.update { it.copy(previewing = null) }
            }
        }
    }

    fun exportTo(write: (String) -> Unit) = launchAction {
        withContext(Dispatchers.IO) { write(c.repo.exportJson()) }
        refresh("Your data was exported.")
    }

    /** Deletes all data, the Gemini key and settings: like a fresh install. */
    fun deleteEverything(onDone: () -> Unit) {
        c.voice.stop()
        c.sound.setSounds(emptySet())
        c.repo.wipe()
        c.store.clearAll()
        refresh()
        onDone()
    }

    /** Re-read stored data (memories may have been added by a conversation that just ended). */
    fun reload() = refresh(null)

    fun clearMessage() = _state.update { it.copy(message = null) }
}
