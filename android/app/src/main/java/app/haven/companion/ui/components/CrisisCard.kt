package app.haven.companion.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.haven.companion.data.CrisisResources
import app.haven.companion.ui.theme.HavenColors

/**
 * Shown whenever the safety check finds a high risk. Buttons open the dialler
 * or SMS app pre-filled; the user stays in control of placing the call.
 */
@Composable
fun CrisisCard(resources: CrisisResources, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun dial(number: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.filter { it.isDigit() || it == '+' }}")))
    }
    fun sms(number: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${number.filter { it.isDigit() }}")))
    }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HavenColors.SurfaceRaised),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("You deserve support right now", style = MaterialTheme.typography.titleMedium)
            Text(
                "If you might be in danger, please reach a person now. You can also ask someone near you to help.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val emergency = resources.emergency
            Button(
                onClick = { if (emergency != null) dial(emergency) else open(resources.directory) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HavenColors.Danger),
            ) {
                Text(if (emergency != null) "Call emergency services ($emergency)" else "Find your local emergency number")
            }
            resources.lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    line.phone?.let { phone ->
                        OutlinedButton(onClick = { dial(phone) }, modifier = Modifier.weight(1f)) {
                            Text("Call ${line.name}")
                        }
                    }
                    line.text?.let { text ->
                        OutlinedButton(onClick = { sms(text) }, modifier = Modifier.weight(if (line.phone != null) 0.6f else 1f)) {
                            Text("Text $text")
                        }
                    }
                }
            }
            TextButton(onClick = { open(resources.directory) }) { Text("Find a helpline in your country") }
            TextButton(onClick = onDismiss) { Text("Hide") }
        }
    }
}
