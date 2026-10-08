package app.haven.companion

import androidx.lifecycle.ViewModel
import app.haven.companion.core.SUPPORTED_CRISIS_REGIONS
import app.haven.companion.core.WellbeingCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId
import java.util.Locale

sealed interface Screen {
    /** First run: welcome → name → promise → check-in → score → key → mic. */
    data object Funnel : Screen
    data object Voice : Screen
    /** A fresh check-in from the talk screen. */
    data object Checkin : Screen
    data object Settings : Screen
    data object Memories : Screen
    data object Privacy : Screen
}

/** Decides which screen to show. There is no account: everything lives on this phone. */
class AppViewModel(val container: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow(if (container.store.isReady) Screen.Voice else Screen.Funnel)
    val screen: StateFlow<Screen> = _screen

    private val _wbc = MutableStateFlow(container.repo.latestCheck?.score)
    /** Latest Well-Being Count, if the user has checked in. */
    val wbc: StateFlow<Int?> = _wbc

    /** Auto-start happens once per app launch, not every time the voice screen reappears. */
    var autoStartConsumed = false

    /** The intro was finished before but the key is missing (e.g. after replacing it failed). */
    val resumeAtKey: Boolean get() = container.store.introCompleted && container.store.apiKey.isNullOrBlank()

    fun go(screen: Screen) {
        // Check-ins may have been forgotten in Settings meanwhile.
        if (screen == Screen.Voice) _wbc.value = container.repo.latestCheck?.score
        _screen.value = screen
    }

    fun saveProfile(name: String, memoryEnabled: Boolean) {
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
    }

    fun saveCheck(check: WellbeingCheck) {
        container.repo.saveCheck(check)
        _wbc.value = check.score
    }

    fun finishFunnel() {
        container.store.introCompleted = true
        go(Screen.Voice)
    }

    /** After "delete everything": back to a brand-new install. */
    fun reset() {
        autoStartConsumed = false
        _wbc.value = null
        go(Screen.Funnel)
    }
}
