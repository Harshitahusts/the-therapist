package app.haven.companion.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haven.companion.core.VoicePhase
import app.haven.companion.core.audio.SoundId
import app.haven.companion.ui.checkin.WbcPill
import app.haven.companion.ui.components.CrisisCard
import app.haven.companion.ui.components.LeafyBackground
import app.haven.companion.ui.components.VoiceOrb
import app.haven.companion.ui.theme.HavenColors
import app.haven.companion.voice.SoundEngine
import app.haven.companion.voice.VoiceSessionController
import java.time.LocalTime

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hi"
}

/** The talk screen: the leaf, status, and the soundscape mixer. */
@Composable
fun VoiceScreen(
    controller: VoiceSessionController,
    sound: SoundEngine,
    preferredName: String?,
    wbc: Int?,
    autoStart: Boolean,
    onAutoStarted: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckIn: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val sounds by sound.sounds.collectAsStateWithLifecycle()
    val volume by sound.volume.collectAsStateWithLifecycle()
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
        VoicePhase.IDLE -> "Tap me to talk"
        VoicePhase.CONNECTING -> "Getting comfy…"
        VoicePhase.LISTENING -> if (state.muted) "Muted" else "I'm listening…"
        VoicePhase.THINKING -> "Thinking…"
        VoicePhase.SPEAKING -> "Speaking…"
        VoicePhase.ERROR -> state.error ?: "Something went wrong. Tap to try again."
    }

    LeafyBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Haven", style = MaterialTheme.typography.headlineMedium, color = HavenColors.LeafDeep, modifier = Modifier.weight(1f))
                WbcPill(wbc, onClick = { controller.stop(); onCheckIn() })
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = HavenColors.InkSoft)
                }
            }

            AnimatedVisibility(visible = !active) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val name = preferredName?.takeIf { it.isNotBlank() }
                    Text(
                        greetingFor(LocalTime.now().hour) + (name?.let { ", $it" } ?: "") + " 🌿",
                        style = MaterialTheme.typography.headlineMedium, color = HavenColors.Ink, textAlign = TextAlign.Center,
                    )
                    Text("How are you feeling?", style = MaterialTheme.typography.titleMedium, color = HavenColors.InkSoft)
                }
            }

            VoiceOrb(
                phase = state.phase,
                micLevel = if (state.muted) 0f else state.micLevel,
                size = 210.dp,
                modifier = Modifier
                    .semantics { contentDescription = if (active) status else "Start talking" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !active,
                    ) { talk() },
            )

            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                color = if (state.phase == VoicePhase.ERROR) MaterialTheme.colorScheme.error else HavenColors.InkSoft,
                textAlign = TextAlign.Center,
            )
            if (micDenied) {
                Text(
                    "Haven needs the microphone to talk with you. You can allow it in Android Settings > Apps > Haven.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = HavenColors.InkSoft,
                )
            }
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { controller.setMuted(!state.muted) }) {
                        Text(if (state.muted) "Unmute" else "Mute", color = HavenColors.LeafDeep)
                    }
                    OutlinedButton(onClick = { controller.stop() }, shape = RoundedCornerShape(50)) {
                        Text("End conversation", color = HavenColors.LeafDeep)
                    }
                }
            }

            state.crisis?.let { CrisisCard(it, onDismiss = controller::dismissCrisisCard) }

            SoundMixer(sounds, volume, onToggle = sound::toggle, onVolume = sound::setVolume)

            Text(
                "AI wellbeing companion · not a therapist",
                style = MaterialTheme.typography.bodyMedium,
                color = HavenColors.InkSoft.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SoundMixer(active: Set<SoundId>, volume: Float, onToggle: (SoundId) -> Unit, onVolume: (Float) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(HavenColors.Card.copy(alpha = 0.85f), RoundedCornerShape(26.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SOUNDSCAPE", style = MaterialTheme.typography.labelMedium, color = HavenColors.Leaf, modifier = Modifier.weight(1f))
            Text(
                if (active.isEmpty()) "Tap to mix" else SoundId.entries.filter { it in active }.joinToString(" + ") { it.label },
                style = MaterialTheme.typography.bodyMedium, color = HavenColors.InkSoft,
            )
        }
        SoundId.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { id ->
                    val on = id in active
                    Surface(
                        onClick = { onToggle(id) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (on) HavenColors.LeafLight.copy(alpha = 0.55f) else HavenColors.CardSoft,
                        border = if (on) BorderStroke(1.5.dp, HavenColors.Leaf) else null,
                        modifier = Modifier.weight(1f).semantics {
                            stateDescription = if (on) "On" else "Off"
                        },
                    ) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(id.emoji, fontSize = 24.sp)
                            Text(id.label, style = MaterialTheme.typography.bodyMedium, color = if (on) HavenColors.LeafDeep else HavenColors.InkSoft)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔈", fontSize = 16.sp)
            Slider(value = volume, onValueChange = onVolume, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("🔊", fontSize = 16.sp)
        }
    }
}
