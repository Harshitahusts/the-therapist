package app.haven.companion.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.haven.companion.core.WellbeingCheck
import app.haven.companion.ui.checkin.CheckinFlow
import app.haven.companion.ui.checkin.ResultScreen
import app.haven.companion.ui.components.LeafBuddy
import app.haven.companion.ui.components.LeafyBackground
import app.haven.companion.ui.components.PillButton
import app.haven.companion.ui.components.SoftButton
import app.haven.companion.ui.theme.HavenColors
import kotlinx.coroutines.launch

const val DISCLAIMER =
    "This is an AI wellbeing companion, not a licensed therapist or medical professional. It cannot diagnose or " +
        "treat mental-health conditions. If you are in immediate danger or may hurt yourself or someone else, " +
        "contact local emergency services or a qualified crisis service."

const val PRIVACY_SUMMARY =
    "Haven has no server and no account. Your memories, check-ins and conversation summaries are stored encrypted on " +
        "this phone only.\n\nTo talk, your voice and words are sent to Google's Gemini API using your own free API key. " +
        "On Gemini's free tier, Google may use what you say to improve its products, and human reviewers may read it. " +
        "Please avoid sharing details you wouldn't want reviewed (full names, addresses, account numbers).\n\n" +
        "You can export or delete everything at any time in Settings."

const val GET_KEY_URL = "https://aistudio.google.com/apikey"

private enum class Step { WELCOME, NAME, PROMISE, CHECKIN, RESULT, KEY, MIC }

/**
 * First-run journey: welcome → name → promise (not a therapist, privacy, memory) →
 * check-in → Well-Being Count → voice key → microphone → talk.
 * Sign-in will slot in before the check-in when Haven launches publicly.
 */
@Composable
fun FunnelScreen(
    resumeAtKey: Boolean,
    hasKey: () -> Boolean,
    checkKey: suspend (String) -> String?,
    onProfile: (name: String, memoryEnabled: Boolean) -> Unit,
    onCheck: (WellbeingCheck) -> Unit,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(if (resumeAtKey) Step.KEY else Step.WELCOME) }
    var name by rememberSaveable { mutableStateOf("") }
    var memory by rememberSaveable { mutableStateOf(true) }
    var understood by rememberSaveable { mutableStateOf(false) }
    var check by remember { mutableStateOf<WellbeingCheck?>(null) }
    val context = LocalContext.current

    fun micGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Next stop after the check-in: whatever is still missing before Haven can talk. */
    fun advance() {
        when {
            !hasKey() -> step = Step.KEY
            !micGranted() -> step = Step.MIC
            else -> onFinished()
        }
    }

    BackHandler(enabled = step != Step.WELCOME && !resumeAtKey) {
        step = when (step) {
            Step.NAME -> Step.WELCOME
            Step.PROMISE -> Step.NAME
            Step.CHECKIN -> Step.PROMISE
            Step.RESULT -> Step.CHECKIN
            Step.KEY -> Step.CHECKIN
            Step.MIC -> Step.KEY
            Step.WELCOME -> Step.WELCOME
        }
    }

    when (step) {
        Step.WELCOME -> Page {
            Spacer(Modifier.height(12.dp))
            LeafBuddy()
            Text("Hello, I'm Haven 🌿", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
            Text(
                "A calm, friendly place to talk things through. You speak, I listen, and we figure things out " +
                    "together, one gentle step at a time.",
                style = MaterialTheme.typography.bodyLarge, color = HavenColors.InkSoft,
            )
            Spacer(Modifier.height(8.dp))
            PillButton("Let's begin", { step = Step.NAME })
        }

        Step.NAME -> Page {
            Spacer(Modifier.height(40.dp))
            Text("What should I call you?", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text("Your name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PillButton("Nice to meet you →", { step = Step.PROMISE }, enabled = name.isNotBlank())
        }

        Step.PROMISE -> Page {
            Spacer(Modifier.height(24.dp))
            Text("A few honest words", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
            InfoCard {
                Text("🤍 I'm not a therapist", style = MaterialTheme.typography.titleMedium, color = HavenColors.LeafDeep)
                Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Ink)
            }
            InfoCard {
                Text("🔒 Your privacy", style = MaterialTheme.typography.titleMedium, color = HavenColors.LeafDeep)
                Text(PRIVACY_SUMMARY, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Ink)
            }
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🧠 Remember what I tell you?", style = MaterialTheme.typography.titleMedium, color = HavenColors.LeafDeep)
                        Text(
                            if (memory) "Yes: I'll remember what helps, on this phone." else "No: every conversation starts fresh.",
                            style = MaterialTheme.typography.bodyMedium, color = HavenColors.InkSoft,
                        )
                    }
                    Switch(checked = memory, onCheckedChange = { memory = it })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = understood, onCheckedChange = { understood = it })
                Text("I understand", style = MaterialTheme.typography.bodyLarge, color = HavenColors.Ink)
            }
            PillButton("Continue", {
                onProfile(name.trim(), memory)
                step = Step.CHECKIN
            }, enabled = understood)
        }

        Step.CHECKIN -> CheckinFlow(
            onDone = { c -> check = c; onCheck(c); step = Step.RESULT },
            onSkip = { advance() },
        )

        Step.RESULT -> {
            val c = check
            if (c == null) LaunchedEffect(Unit) { advance() }
            else ResultScreen(c, primaryLabel = "Talk with Haven 🌿", onPrimary = { advance() })
        }

        Step.KEY -> KeyStep(checkKey = checkKey, onDone = { advance() })

        Step.MIC -> MicStep(onDone = onFinished)
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    LeafyBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(HavenColors.Card.copy(alpha = 0.92f), RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun KeyStep(checkKey: suspend (String) -> String?, onDone: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Page {
        Spacer(Modifier.height(24.dp))
        Text("Give me a voice 🎙️", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
        Text(
            "Haven talks using Google Gemini. Create a free API key in Google AI Studio (sign in with your Google " +
                "account, then tap \"Create API key\"), copy it, and paste it here. It's stored encrypted on this phone " +
                "and only ever sent to Google.",
            style = MaterialTheme.typography.bodyLarge, color = HavenColors.InkSoft,
        )
        SoftButton("Get a free key", { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GET_KEY_URL))) } })
        OutlinedTextField(
            value = key, onValueChange = { key = it.trim(); error = null }, singleLine = true,
            label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (busy) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
        else PillButton("Check key and continue", {
            busy = true
            scope.launch {
                error = checkKey(key)
                busy = false
                if (error == null) onDone()
            }
        }, enabled = key.length >= 20)
    }
}

@Composable
private fun MicStep(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    Page {
        Spacer(Modifier.height(24.dp))
        LeafBuddy(roam = 40.dp, size = 80.dp)
        Text("Can I hear you?", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
        Text(
            "Haven is voice-first, so it needs the microphone while a conversation is open. It never listens in the " +
                "background. Headphones give the clearest conversation.",
            style = MaterialTheme.typography.bodyLarge, color = HavenColors.InkSoft,
        )
        Spacer(Modifier.height(8.dp))
        PillButton("Allow microphone", { launcher.launch(Manifest.permission.RECORD_AUDIO) })
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not now", color = HavenColors.InkSoft)
        }
    }
}
