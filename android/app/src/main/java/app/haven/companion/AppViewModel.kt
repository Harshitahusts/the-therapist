package app.haven.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haven.companion.data.ApiException
import app.haven.companion.data.Config
import app.haven.companion.data.Me
import app.haven.companion.data.NetworkException
import app.haven.companion.data.OnboardingRequest
import app.haven.companion.data.SUPPORTED_CRISIS_REGIONS
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

sealed interface Screen {
    data object Onboarding : Screen
    data object Auth : Screen
    data object Loading : Screen
    data class Failed(val message: String) : Screen
    data object Voice : Screen
    data object Settings : Screen
    data object Memories : Screen
    data object Privacy : Screen
}

/** Decides which screen to show, based on first-run state, sign-in, and the server profile. */
class AppViewModel(val container: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen

    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me

    private var resolving: Job? = null

    /** Auto-start happens once per app launch, not every time the voice screen reappears. */
    var autoStartConsumed = false

    init {
        resolve()
        // Sign-in / sign-out from anywhere re-evaluates where the user should be.
        viewModelScope.launch { container.auth.signedIn.drop(1).collect { resolve() } }
    }

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun setMe(me: Me?) {
        _me.value = me
    }

    fun finishIntro(name: String, memoryEnabled: Boolean) {
        container.store.draftName = name
        container.store.draftMemoryEnabled = memoryEnabled
        container.store.introCompleted = true
        resolve()
    }

    fun resolve() {
        resolving?.cancel()
        resolving = viewModelScope.launch {
            val store = container.store
            if (!store.introCompleted) return@launch go(Screen.Onboarding)
            if (!container.auth.signedIn.value) return@launch go(Screen.Auth)
            go(Screen.Loading)
            try {
                var me = container.api.me()
                if (!me.settings.onboarding_completed) {
                    me = container.api.completeOnboarding(
                        OnboardingRequest(
                            preferred_name = store.draftName?.takeIf { it.isNotBlank() } ?: "friend",
                            memory_enabled = store.draftMemoryEnabled,
                            timezone = ZoneId.systemDefault().id,
                            language = Locale.getDefault().language,
                            crisis_region = Locale.getDefault().country.uppercase().takeIf { it in SUPPORTED_CRISIS_REGIONS },
                        )
                    )
                }
                _me.value = me
                go(Screen.Voice)
            } catch (e: ApiException) {
                if (e.code == 401) go(Screen.Auth) else go(Screen.Failed(e.message ?: "Something went wrong."))
            } catch (e: NetworkException) {
                go(Screen.Failed(Config.NETWORK_ERROR_MESSAGE))
            }
        }
    }
}
