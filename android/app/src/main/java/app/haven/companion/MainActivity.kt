package app.haven.companion

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.haven.companion.core.WellbeingCheck
import app.haven.companion.ui.checkin.CheckinFlow
import app.haven.companion.ui.checkin.ResultScreen
import app.haven.companion.ui.onboarding.FunnelScreen
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
        // Light, leafy screens: dark status-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // Never keep the microphone (or the soundscapes) running once the app leaves the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (container.voice.isActive) container.voice.stop()
                container.sound.setSounds(emptySet())
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
    val wbc by vm.wbc.collectAsStateWithLifecycle()
    val c = vm.container

    when (screen) {
        Screen.Funnel -> FunnelScreen(
            resumeAtKey = vm.resumeAtKey,
            hasKey = { !c.store.apiKey.isNullOrBlank() && !c.store.liveModel.isNullOrBlank() },
            checkKey = { key -> c.connectGeminiKey(key) },
            onProfile = vm::saveProfile,
            onCheck = vm::saveCheck,
            onFinished = vm::finishFunnel,
        )
        Screen.Voice -> VoiceScreen(
            controller = c.voice,
            sound = c.sound,
            preferredName = c.repo.data.profile.preferredName,
            wbc = wbc,
            autoStart = c.store.autoStart && !vm.autoStartConsumed,
            onAutoStarted = { vm.autoStartConsumed = true },
            onOpenSettings = { c.voice.stop(); vm.go(Screen.Settings) },
            onCheckIn = { vm.go(Screen.Checkin) },
        )
        Screen.Checkin -> {
            var result by remember { mutableStateOf<WellbeingCheck?>(null) }
            BackHandler { vm.go(Screen.Voice) }
            val r = result
            if (r == null) {
                CheckinFlow(onDone = { check -> vm.saveCheck(check); result = check }, onSkip = { vm.go(Screen.Voice) })
            } else {
                ResultScreen(
                    r, primaryLabel = "Talk with Haven 🌿", onPrimary = { vm.go(Screen.Voice) },
                    onRetake = { result = null },
                )
            }
        }
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
