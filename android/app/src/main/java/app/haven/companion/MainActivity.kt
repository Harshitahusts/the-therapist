package app.haven.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.haven.companion.data.Config
import app.haven.companion.ui.auth.AuthScreen
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
    val me by vm.me.collectAsStateWithLifecycle()
    val c = vm.container

    if (!Config.isConfigured) {
        Message("This build is missing its Supabase configuration. See docs/BUILD_APK.md.")
        return
    }

    when (val s = screen) {
        Screen.Onboarding -> OnboardingScreen(
            initialName = c.store.draftName,
            initialMemory = c.store.draftMemoryEnabled,
            onFinished = vm::finishIntro,
        )
        Screen.Auth -> AuthScreen(c.auth)
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Screen.Failed -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = vm::resolve) { Text("Try again") }
        }
        Screen.Voice -> VoiceScreen(
            controller = c.voice,
            preferredName = me?.profile?.preferred_name ?: c.store.draftName,
            autoStart = c.store.autoStart && !vm.autoStartConsumed,
            onAutoStarted = { vm.autoStartConsumed = true },
            onOpenSettings = { c.voice.stop(); vm.go(Screen.Settings) },
        )
        Screen.Settings, Screen.Memories, Screen.Privacy -> {
            val settingsVm: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(c) { vm.setMe(it) } } }
            )
            when (s) {
                Screen.Settings -> SettingsScreen(
                    vm = settingsVm,
                    onBack = { vm.go(Screen.Voice) },
                    onOpenMemories = { vm.go(Screen.Memories) },
                    onOpenPrivacy = { vm.go(Screen.Privacy) },
                    onSignedOut = vm::resolve,
                    onDataDeleted = vm::resolve,
                )
                Screen.Memories -> MemoriesScreen(settingsVm, onBack = { vm.go(Screen.Settings) })
                else -> PrivacyScreen(onBack = { vm.go(Screen.Settings) })
            }
        }
    }

}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
