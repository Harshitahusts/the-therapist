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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haven.companion.data.SUPPORTED_CRISIS_REGIONS
import app.haven.companion.ui.onboarding.DISCLAIMER
import app.haven.companion.ui.theme.HavenColors

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
private fun Item(title: String, subtitle: String? = null, color: androidx.compose.ui.graphics.Color? = null,
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
    onSignedOut: () -> Unit,
    onDataDeleted: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    var regionPicker by remember { mutableStateOf(false) }

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
            val memoryOn = state.me?.settings?.memory_enabled ?: true
            Item(
                "Remember what I tell you?",
                if (memoryOn) "On: useful things are remembered between conversations."
                else "Off: no long-term memories are created.",
                trailing = { Switch(checked = memoryOn, enabled = state.me != null && !state.busy, onCheckedChange = vm::setMemoryEnabled) },
            )
            Item("What I remember", "See and delete individual memories", onClick = onOpenMemories)
            Item(
                "Start talking when I open the app",
                trailing = { Switch(checked = state.autoStart, onCheckedChange = vm::setAutoStart) },
            )
            val region = state.me?.settings?.crisis_region ?: "INTL"
            Item("Crisis support region", SUPPORTED_CRISIS_REGIONS[region] ?: region, onClick = { regionPicker = true })
            HorizontalDivider()
            Item("Export my data", "Download everything stored about you as JSON",
                onClick = { exportLauncher.launch("haven-export.json") })
            Item("Privacy & disclaimer", onClick = onOpenPrivacy)
            Item("Sign out", state.me?.email, onClick = { vm.signOut(onSignedOut) })
            HorizontalDivider()
            Item("Delete all my data", "Memories, summaries, profile and history. Keeps your login.",
                color = HavenColors.Danger, onClick = { confirm = "data" })
            Item("Delete my account", "Deletes all data and your login. This can't be undone.",
                color = HavenColors.Danger, onClick = { confirm = "account" })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (regionPicker) {
        AlertDialog(
            onDismissRequest = { regionPicker = false },
            title = { Text("Crisis support region") },
            text = {
                Column {
                    Text("Used to show the right emergency and crisis numbers if you ever need them.",
                        style = MaterialTheme.typography.bodyMedium)
                    SUPPORTED_CRISIS_REGIONS.forEach { (code, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setCrisisRegion(code); regionPicker = false }) {
                            RadioButton(selected = (state.me?.settings?.crisis_region ?: "INTL") == code, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { regionPicker = false }) { Text("Close") } },
        )
    }
    when (confirm) {
        "data" -> Confirm("Delete all your data?", "Everything stored about you will be permanently deleted. Your login stays.",
            "Delete", { vm.deleteAllData(onDataDeleted) }, { confirm = null })
        "account" -> Confirm("Delete your account?", "All your data and your login will be permanently deleted.",
            "Delete account", { vm.deleteAccount(onSignedOut) }, { confirm = null })
    }
}

@Composable
fun MemoriesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadMemories() }

    ScreenScaffold("What I remember", onBack, state.busy) {
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        val memories = state.memories
        if (memories != null && memories.isEmpty()) {
            Text(
                if (state.me?.settings?.memory_enabled == false) "Memory is off, so nothing is being remembered."
                else "Nothing yet. As you talk, I'll remember things that help, like goals or what's been on your mind.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(memories.orEmpty(), key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.content, style = MaterialTheme.typography.bodyLarge)
                        Text(m.category.replace('_', ' ') + " · " + m.updated_at.take(10),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteMemory(m.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Forget this", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
        if (!memories.isNullOrEmpty()) {
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
            Section(
                "What happens to your voice",
                "While a conversation is open, your microphone audio is streamed to OpenAI's Realtime API, which " +
                    "listens and replies with a voice. This app does not record or store audio. OpenAI processes it " +
                    "under its API data-usage policies."
            )
            Section(
                "What is stored",
                "Your email and login (Supabase Auth). Your profile (name, timezone, language) and settings. " +
                    "When memory is on: a short summary of each conversation and a few useful long-term facts. " +
                    "Start and end times of conversations. If a safety concern is detected, only its risk level " +
                    "and category are recorded, never what you said."
            )
            Section(
                "What is never stored",
                "Raw audio. Full transcripts (they are used once to write the summary and then discarded). " +
                    "Passwords, card numbers, API keys or ID numbers are filtered out of memories."
            )
            Section(
                "Your controls",
                "Turn memory off at any time. Delete single memories, or everything, from \"What I remember\". " +
                    "Export all your data as JSON. Delete all data, or your whole account, from Settings."
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
