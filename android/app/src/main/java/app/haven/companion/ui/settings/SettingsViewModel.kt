package app.haven.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haven.companion.AppContainer
import app.haven.companion.data.ApiException
import app.haven.companion.data.Config
import app.haven.companion.data.Me
import app.haven.companion.data.MemoryItem
import app.haven.companion.data.NetworkException
import app.haven.companion.data.SettingsUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val me: Me? = null,
    val memories: List<MemoryItem>? = null,
    val autoStart: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val c: AppContainer, private val onMeChanged: (Me?) -> Unit) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(autoStart = c.store.autoStart))
    val state: StateFlow<SettingsUiState> = _state

    init {
        refresh()
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: Exception) {
                val msg = when (e) {
                    is NetworkException -> Config.NETWORK_ERROR_MESSAGE
                    is ApiException -> e.message
                    else -> "Something went wrong."
                }
                _state.update { it.copy(message = msg) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun refresh() = launchAction {
        val me = c.api.me()
        _state.update { it.copy(me = me) }
        onMeChanged(me)
    }

    fun loadMemories() = launchAction {
        val list = c.api.memories()
        _state.update { it.copy(memories = list) }
    }

    fun setMemoryEnabled(enabled: Boolean) = launchAction {
        val me = c.api.updateSettings(SettingsUpdate(memory_enabled = enabled))
        _state.update { it.copy(me = me) }
        onMeChanged(me)
    }

    fun setCrisisRegion(region: String) = launchAction {
        val me = c.api.updateSettings(SettingsUpdate(crisis_region = region))
        _state.update { it.copy(me = me) }
        onMeChanged(me)
    }

    fun setAutoStart(enabled: Boolean) {
        c.store.autoStart = enabled
        _state.update { it.copy(autoStart = enabled) }
    }

    fun deleteMemory(id: String) = launchAction {
        c.api.deleteMemory(id)
        _state.update { s -> s.copy(memories = s.memories?.filterNot { it.id == id }) }
    }

    fun forgetEverything() = launchAction {
        c.api.deleteAllMemories()
        _state.update { it.copy(memories = emptyList(), message = "Done. I've forgotten everything you told me.") }
    }

    fun exportTo(write: (String) -> Unit) = launchAction {
        val json = c.api.exportData()
        write(json)
        _state.update { it.copy(message = "Your data was exported.") }
    }

    fun deleteAllData(onDone: () -> Unit) = launchAction {
        c.api.deleteAllData()
        c.store.introCompleted = false
        onMeChanged(null)
        onDone()
    }

    fun deleteAccount(onDone: () -> Unit) = launchAction {
        c.api.deleteAccount()
        c.auth.signOutLocally()
        c.store.clearAll()
        onMeChanged(null)
        onDone()
    }

    fun signOut(onDone: () -> Unit) = launchAction {
        c.auth.signOut()
        onMeChanged(null)
        onDone()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
