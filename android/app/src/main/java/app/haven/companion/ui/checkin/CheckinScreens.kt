package app.haven.companion.ui.checkin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haven.companion.core.Checkin
import app.haven.companion.core.WellbeingCheck
import app.haven.companion.ui.components.LeafyBackground
import app.haven.companion.ui.components.PillButton
import app.haven.companion.ui.theme.HavenColors

/** Pick how many questions → answer them one at a time (each with its own note) → [onDone]. */
@Composable
fun CheckinFlow(onDone: (WellbeingCheck) -> Unit, onSkip: (() -> Unit)? = null) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    if (count == 0) {
        HowManyScreen(onPick = { count = it }, onSkip = onSkip)
    } else {
        QuestionsScreen(Checkin.questionsFor(count), onDone)
    }
}

@Composable
private fun HowManyScreen(onPick: (Int) -> Unit, onSkip: (() -> Unit)?) {
    LeafyBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(36.dp))
            Text("A little check-in 🌱", style = MaterialTheme.typography.displaySmall, color = HavenColors.Ink)
            Text(
                "A few gentle questions about how life has been feeling. There are no right answers, and you choose how many.",
                style = MaterialTheme.typography.bodyLarge, color = HavenColors.InkSoft,
            )
            Spacer(Modifier.height(6.dp))
            val options = listOf(
                Triple(3, "Quick", "About a minute ☕"),
                Triple(5, "Balanced", "A couple of minutes 🍃"),
                Triple(10, "Deep", "A fuller picture 🌳"),
            )
            options.forEach { (n, name, hint) ->
                Surface(
                    onClick = { onPick(n) },
                    shape = RoundedCornerShape(22.dp),
                    color = HavenColors.Card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).background(HavenColors.CardSoft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("$n", style = MaterialTheme.typography.titleLarge, color = HavenColors.LeafDeep) }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text("$name · $n questions", style = MaterialTheme.typography.titleMedium, color = HavenColors.Ink)
                            Text(hint, style = MaterialTheme.typography.bodyMedium, color = HavenColors.InkSoft)
                        }
                        Text("→", color = HavenColors.Leaf, fontSize = 22.sp)
                    }
                }
            }
            if (onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip for now", color = HavenColors.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun QuestionsScreen(questions: List<Checkin.Question>, onDone: (WellbeingCheck) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, Int>() }
    val q = questions[index]
    val picked = answers[q.id]

    LeafyBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Progress: one little leaf-dot per question
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
                questions.forEachIndexed { i, _ ->
                    Box(
                        Modifier.height(6.dp).width(if (i == index) 22.dp else 6.dp)
                            .background(if (i <= index) HavenColors.Leaf else HavenColors.LeafLight.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
            Text(
                "${q.topic.uppercase()} · ${index + 1} OF ${questions.size}",
                style = MaterialTheme.typography.labelMedium, color = HavenColors.Leaf,
            )
            Text(q.text, style = MaterialTheme.typography.headlineMedium, color = HavenColors.Ink)

            Checkin.OPTIONS.forEachIndexed { i, label ->
                val selected = picked == i
                Surface(
                    onClick = { answers[q.id] = i },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) HavenColors.Leaf else HavenColors.Card,
                    border = if (selected) null else BorderStroke(1.dp, HavenColors.LeafLight),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else HavenColors.Ink,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = picked != null,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 },
            ) {
                Column(
                    Modifier.fillMaxWidth().background(HavenColors.CardSoft, RoundedCornerShape(22.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(q.noteTitle, style = MaterialTheme.typography.titleMedium, color = HavenColors.LeafDeep)
                    Text(q.note, style = MaterialTheme.typography.bodyLarge, color = HavenColors.Ink)
                }
            }

            Spacer(Modifier.height(4.dp))
            PillButton(
                text = if (index == questions.lastIndex) "See my Well-Being Count" else "Next",
                enabled = picked != null,
                onClick = {
                    if (index < questions.lastIndex) index++ else onDone(Checkin.record(answers.toMap()))
                },
            )
            if (index > 0) {
                TextButton(onClick = { index-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back", color = HavenColors.InkSoft)
                }
            }
        }
    }
}

/** The Well-Being Count: an animated ring, the band, and a kind word. */
@Composable
fun ResultScreen(check: WellbeingCheck, primaryLabel: String, onPrimary: () -> Unit, onRetake: (() -> Unit)? = null) {
    val band = Checkin.band(check.score)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(check.createdAt) {
        progress.animateTo(check.score / 100f, tween(1400, easing = FastOutSlowInEasing))
    }
    LeafyBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Text("YOUR WELL-BEING COUNT", style = MaterialTheme.typography.labelMedium, color = HavenColors.Leaf)
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 11.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(HavenColors.LeafLight.copy(alpha = 0.35f), -90f, 360f, false, topLeft, arcSize, style = stroke)
                    drawArc(HavenColors.Leaf, -90f, 360f * progress.value, false, topLeft, arcSize, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(progress.value * 100).toInt()}",
                        fontSize = 56.sp, fontWeight = FontWeight.SemiBold, color = HavenColors.Ink,
                    )
                    Text("out of 100", style = MaterialTheme.typography.bodyMedium, color = HavenColors.InkSoft)
                }
            }
            Text("${band.emoji} ${band.name}", style = MaterialTheme.typography.headlineMedium, color = HavenColors.LeafDeep)
            Text(
                band.message, style = MaterialTheme.typography.bodyLarge, color = HavenColors.Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                "A gentle reflection from ${check.answers.size} answers, not a diagnosis. Haven will keep it in mind when you talk.",
                style = MaterialTheme.typography.bodyMedium, color = HavenColors.InkSoft, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PillButton(primaryLabel, onPrimary)
            if (onRetake != null) {
                TextButton(onClick = onRetake) { Text("Answer different questions", color = HavenColors.InkSoft) }
            }
        }
    }
}

/** Tiny pill showing the latest count; tap to check in again. */
@Composable
fun WbcPill(score: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(HavenColors.Card, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (score == null) "🌱 Check in" else "🌱 WBC $score", style = MaterialTheme.typography.labelLarge, color = HavenColors.LeafDeep)
    }
}
