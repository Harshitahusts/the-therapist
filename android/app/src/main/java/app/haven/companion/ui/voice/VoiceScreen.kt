package app.haven.companion.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haven.companion.ui.components.CrisisCard
import app.haven.companion.ui.components.VoiceOrb
import app.haven.companion.core.VoicePhase
import app.haven.companion.voice.VoiceSessionController
import java.time.LocalTime

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hi"
}

/** The main screen: an orb, a status line, and nothing else to get in the way. */
@Composable
fun VoiceScreen(
    controller: VoiceSessionController,
    preferredName: String?,
    autoStart: Boolean,
    onAutoStarted: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) controller.start()
    }

    fun talk() {
        controller.clearError()
        if (hasMic()) controller.start() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        if (autoStart && hasMic() && state.phase == VoicePhase.IDLE) {
            onAutoStarted()
            controller.start()
        }
    }

    val active = state.phase !in setOf(VoicePhase.IDLE, VoicePhase.ERROR)
    val status = when (state.phase) {
        VoicePhase.IDLE -> "Tap to talk"
        VoicePhase.CONNECTING -> "Connecting…"
        VoicePhase.LISTENING -> if (state.muted) "Muted" else "Listening…"
        VoicePhase.THINKING -> "Thinking…"
        VoicePhase.SPEAKING -> "Speaking…"
        VoicePhase.ERROR -> state.error ?: "Something went wrong. Tap to try again."
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(visible = !active) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val name = preferredName?.takeIf { it.isNotBlank() }
                    Text(
                        greetingFor(LocalTime.now().hour) + (name?.let { ", $it." } ?: "."),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("How are you feeling?", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(36.dp))
                }
            }

            VoiceOrb(
                phase = state.phase,
                micLevel = if (state.muted) 0f else state.micLevel,
                modifier = Modifier
                    .semantics { contentDescription = if (active) status else "Start talking" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !active,
                    ) { talk() },
            )

            Spacer(Modifier.height(28.dp))
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                color = if (state.phase == VoicePhase.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (micDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Haven needs the microphone to talk with you. You can allow it in Android Settings > Apps > Haven.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { controller.setMuted(!state.muted) }) {
                        Text(if (state.muted) "Unmute" else "Mute")
                    }
                    OutlinedButton(onClick = { controller.stop() }) { Text("End conversation") }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            state.crisis?.let { CrisisCard(it, onDismiss = controller::dismissCrisisCard) }
            Text(
                "AI wellbeing companion · not a therapist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
