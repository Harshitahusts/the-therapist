package app.haven.companion.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haven.companion.core.SUPPORTED_CRISIS_REGIONS
import app.haven.companion.ui.onboarding.DISCLAIMER
import app.haven.companion.ui.onboarding.PRIVACY_SUMMARY
import app.haven.companion.ui.theme.HavenColors
import java.time.Instant
import java.time.ZoneId

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, busy: Boolean, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Item(title: String, subtitle: String? = null, color: Color? = null,
                 trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = HavenColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeletedEverything: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var newKey by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.reload() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo { json ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }

    ScreenScaffold("Settings", onBack, state.busy) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            val memoryOn = state.data.settings.memoryEnabled
            Item(
                "Remember what I tell you?",
                if (memoryOn) "On: useful things are remembered between conversations, on this phone."
                else "Off: no long-term memories are created.",
                trailing = { Switch(checked = memoryOn, onCheckedChange = vm::setMemoryEnabled) },
            )
            Item("What I remember", "See and delete individual memories", onClick = onOpenMemories)
            Item(
                "Start talking when I open the app",
                trailing = { Switch(checked = state.autoStart, onCheckedChange = vm::setAutoStart) },
            )
            val region = state.data.settings.crisisRegion
            Item("Crisis support region", SUPPORTED_CRISIS_REGIONS[region] ?: region, onClick = { dialog = "region" })
            HorizontalDivider()
            Item("Gemini API key", "Replace the key Haven uses", onClick = { newKey = ""; dialog = "key" })
            Item("Voice model", state.liveModel ?: "Not set", onClick = { vm.loadLiveModels(); dialog = "model" })
            HorizontalDivider()
            Item("Export my data", "Save everything Haven stores as a JSON file",
                onClick = { exportLauncher.launch("haven-export.json") })
            Item("Privacy & disclaimer", onClick = onOpenPrivacy)
            HorizontalDivider()
            Item("Forget everything about me", "Deletes all memories and conversation summaries",
                color = HavenColors.Danger, onClick = { dialog = "forget" })
            Item("Delete everything", "All data, your Gemini key and settings. Like a fresh install.",
                color = HavenColors.Danger, onClick = { dialog = "wipe" })
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "region" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Crisis support region") },
            text = {
                Column {
                    Text("Used to show the right emergency and crisis numbers if you ever need them.",
                        style = MaterialTheme.typography.bodyMedium)
                    SUPPORTED_CRISIS_REGIONS.forEach { (code, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setCrisisRegion(code); dialog = null }) {
                            RadioButton(selected = state.data.settings.crisisRegion == code, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
        "key" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Gemini API key") },
            text = {
                OutlinedTextField(
                    value = newKey, onValueChange = { newKey = it.trim() }, singleLine = true,
                    label = { Text("New key") }, visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(enabled = newKey.length >= 20, onClick = { vm.changeKey(newKey); dialog = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "model" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Voice model") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val models = state.liveModels
                    if (models == null) Text("Loading…") else models.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setLiveModel(m); dialog = null }) {
                            RadioButton(selected = state.liveModel == m, onClick = null)
                            Text(m, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
        "forget" -> Confirm("Forget everything?", "All memories and conversation summaries will be permanently deleted.",
            "Forget everything", vm::forgetEverything) { dialog = null }
        "wipe" -> Confirm("Delete everything?",
            "All your data, your Gemini key and your settings will be permanently deleted from this phone.",
            "Delete everything", { vm.deleteEverything(onDeletedEverything) }) { dialog = null }
    }
}

@Composable
fun MemoriesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }
    val memories = state.data.memories.sortedByDescending { it.updatedAt }
    LaunchedEffect(Unit) { vm.reload() }

    ScreenScaffold("What I remember", onBack, state.busy) {
        if (memories.isEmpty()) {
            Text(
                if (!state.data.settings.memoryEnabled) "Memory is off, so nothing is being remembered."
                else "Nothing yet. As you talk, I'll remember things that help, like goals or what's been on your mind.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(memories, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.content, style = MaterialTheme.typography.bodyLarge)
                        val date = Instant.ofEpochMilli(m.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(m.category.replace('_', ' ') + " · " + date,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteMemory(m.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Forget this", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
        if (memories.isNotEmpty()) {
            TextButton(onClick = { confirmAll = true }, modifier = Modifier.padding(16.dp)) {
                Text("Forget everything about me", color = HavenColors.Danger)
            }
        }
    }
    if (confirmAll) {
        Confirm("Forget everything?", "All memories and conversation summaries will be permanently deleted.",
            "Forget everything", vm::forgetEverything) { confirmAll = false }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold("Privacy & disclaimer", onBack, busy = false) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Not a therapist", DISCLAIMER)
            Section("In short", PRIVACY_SUMMARY)
            Section(
                "What is stored on this phone",
                "Your name, timezone and language; your settings; and, when memory is on, short summaries of " +
                    "conversations and a few useful long-term facts. Start and end times of conversations. If a safety " +
                    "concern is detected, only its risk level and category, never what you said. All of it is encrypted " +
                    "with a key kept in the Android Keystore and excluded from cloud backups."
            )
            Section(
                "What is never stored",
                "Audio. Full transcripts (they are used once at the end of a conversation to write the summary, then " +
                    "discarded). Passwords, card numbers, API keys or ID numbers are filtered out of memories."
            )
            Section(
                "What goes to Google",
                "During a conversation, your microphone audio and the companion's context (your name, relevant " +
                    "memories and recent summaries) go to the Gemini API. At the end, the transcript is sent once to " +
                    "write the summary. Google handles it under the Gemini API terms; on the free tier it may be used " +
                    "to improve Google's products and may be read by human reviewers."
            )
            Section(
                "Your controls",
                "Turn memory off at any time, delete single memories or everything, export all your data as JSON, " +
                    "or delete everything including your key from Settings."
            )
            Section("Full policy", "The full privacy policy and terms are published with the project's source code in PRIVACY_POLICY.md and TERMS.md.")
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
