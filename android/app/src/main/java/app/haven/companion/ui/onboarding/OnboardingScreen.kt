package app.haven.companion.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.haven.companion.core.VoicePhase
import app.haven.companion.ui.components.VoiceOrb
import kotlinx.coroutines.launch

const val DISCLAIMER =
    "This is an AI wellbeing companion, not a licensed therapist or medical professional. It cannot diagnose or " +
        "treat mental-health conditions. If you are in immediate danger or may hurt yourself or someone else, " +
        "contact local emergency services or a qualified crisis service."

const val PRIVACY_SUMMARY =
    "Haven has no server and no account. Your memories and conversation summaries are stored encrypted on this " +
        "phone only.\n\nTo talk, your voice and words are sent to Google's Gemini API using your own free API key. " +
        "On Gemini's free tier, Google may use what you say to improve its products, and human reviewers may read it. " +
        "Please keep that in mind and avoid sharing details you wouldn't want reviewed (full names, addresses, " +
        "account numbers).\n\nYou can export or delete everything at any time in Settings."

const val GET_KEY_URL = "https://aistudio.google.com/apikey"

/**
 * First-run flow: what it is, not a therapist, privacy, name, memory, Gemini key, microphone.
 * [checkKey] returns null when the key works, or a message explaining the problem.
 */
@Composable
fun OnboardingScreen(
    checkKey: suspend (String) -> String?,
    onFinished: (name: String, memoryEnabled: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var understood by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var memory by rememberSaveable { mutableStateOf(true) }
    var key by rememberSaveable { mutableStateOf("") }
    var keyBusy by rememberSaveable { mutableStateOf(false) }
    var keyError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var micGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) onFinished(name.trim(), memory)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        VoiceOrb(VoicePhase.IDLE, 0f, Modifier.align(Alignment.CenterHorizontally), size = 140.dp)
        when (step) {
            0 -> {
                Title("A calm place to talk things through")
                Body(
                    "Haven is a voice companion for moments when you want to reflect, untangle a feeling, or just " +
                        "say things out loud. You talk, it listens, and it responds gently. No typing, no chat feed."
                )
                Next { step = 1 }
            }
            1 -> {
                Title("Before we start")
                Body(DISCLAIMER)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("I understand", style = MaterialTheme.typography.bodyLarge)
                }
                Next(enabled = understood) { step = 2 }
            }
            2 -> {
                Title("Your privacy")
                Body(PRIVACY_SUMMARY)
                Next { step = 3 }
            }
            3 -> {
                Title("What should I call you?")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("Your name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Next(enabled = name.isNotBlank()) { step = 4 }
            }
            4 -> {
                Title("Remember what I tell you?")
                Body(
                    "On: I can remember useful things between conversations, like what you're working towards or " +
                        "what's been on your mind, so you don't have to repeat yourself. It stays on this phone.\n\n" +
                        "Off: each conversation starts fresh and nothing long-term is saved.\n\n" +
                        "You can change this, or say \"forget that\", any time."
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (memory) "Memory on" else "Memory off", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = memory, onCheckedChange = { memory = it })
                }
                Next { step = 5 }
            }
            5 -> {
                Title("Connect your free Gemini key")
                Body(
                    "Haven's voice runs on Google Gemini. Create a free API key in Google AI Studio (sign in with " +
                        "your Google account, then \"Create API key\"), copy it, and paste it here. It's stored " +
                        "encrypted on this phone and only ever sent to Google."
                )
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GET_KEY_URL))) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Get a free key") }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); keyError = null },
                    singleLine = true,
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                keyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = {
                        keyBusy = true
                        scope.launch {
                            keyError = checkKey(key)
                            keyBusy = false
                            if (keyError == null) step = 6
                        }
                    },
                    enabled = key.length >= 20 && !keyBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (keyBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Check key and continue")
                }
            }
            else -> {
                Title("Microphone")
                Body(
                    "Haven is voice-first, so it needs the microphone while a conversation is open. It never " +
                        "listens in the background. Headphones give the clearest conversation."
                )
                Button(
                    onClick = {
                        if (micGranted) onFinished(name.trim(), memory)
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (micGranted) "Start" else "Allow microphone") }
                if (!micGranted) {
                    TextButton(onClick = { onFinished(name.trim(), memory) }) { Text("Not now") }
                }
            }
        }
        if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Next(enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
