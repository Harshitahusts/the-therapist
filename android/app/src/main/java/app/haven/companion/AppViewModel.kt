package app.haven.companion

import androidx.lifecycle.ViewModel
import app.haven.companion.core.SUPPORTED_CRISIS_REGIONS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId
import java.util.Locale

sealed interface Screen {
    data object Onboarding : Screen
    data object Voice : Screen
    data object Settings : Screen
    data object Memories : Screen
    data object Privacy : Screen
}

/** Decides which screen to show. There is no account: everything lives on this phone. */
class AppViewModel(val container: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow(if (container.store.isReady) Screen.Voice else Screen.Onboarding)
    val screen: StateFlow<Screen> = _screen

    /** Auto-start happens once per app launch, not every time the voice screen reappears. */
    var autoStartConsumed = false

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun finishOnboarding(name: String, memoryEnabled: Boolean) {
        val country = Locale.getDefault().country.uppercase()
        container.repo.updateProfile {
            it.copy(
                name = it.name ?: name,
                preferredName = name,
                timezone = ZoneId.systemDefault().id,
                language = Locale.getDefault().language,
            )
        }
        container.repo.updateSettings {
            it.copy(memoryEnabled = memoryEnabled, crisisRegion = if (country in SUPPORTED_CRISIS_REGIONS) country else "INTL")
        }
        container.store.introCompleted = true
        go(Screen.Voice)
    }

    /** After "delete everything": back to a brand-new install. */
    fun reset() {
        autoStartConsumed = false
        go(Screen.Onboarding)
    }
}
