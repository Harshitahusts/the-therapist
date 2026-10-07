package app.haven.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.haven.companion.ui.onboarding.OnboardingScreen
import app.haven.companion.ui.settings.MemoriesScreen
import app.haven.companion.ui.settings.PrivacyScreen
import app.haven.companion.ui.settings.SettingsScreen
import app.haven.companion.ui.settings.SettingsViewModel
import app.haven.companion.ui.theme.HavenTheme
import app.haven.companion.ui.voice.VoiceScreen

class MainActivity : ComponentActivity() {

    private val container get() = (application as HavenApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Never keep the microphone open once the app leaves the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (container.voice.isActive) container.voice.stop()
            }
        })

        setContent {
            HavenTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
                    HavenApp(vm)
                }
            }
        }
    }
}

@Composable
private fun HavenApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val c = vm.container

    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            checkKey = { key -> c.connectGeminiKey(key) },
            onFinished = vm::finishOnboarding,
        )
        Screen.Voice -> VoiceScreen(
            controller = c.voice,
            preferredName = c.repo.data.profile.preferredName,
            autoStart = c.store.autoStart && !vm.autoStartConsumed,
            onAutoStarted = { vm.autoStartConsumed = true },
            onOpenSettings = { c.voice.stop(); vm.go(Screen.Settings) },
        )
        Screen.Settings, Screen.Memories, Screen.Privacy -> {
            val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(c) } })
            when (screen) {
                Screen.Settings -> SettingsScreen(
                    vm = settingsVm,
                    onBack = { vm.go(Screen.Voice) },
                    onOpenMemories = { vm.go(Screen.Memories) },
                    onOpenPrivacy = { vm.go(Screen.Privacy) },
                    onDeletedEverything = vm::reset,
                )
                Screen.Memories -> MemoriesScreen(settingsVm, onBack = { vm.go(Screen.Settings) })
                else -> PrivacyScreen(onBack = { vm.go(Screen.Settings) })
            }
        }
    }
}
