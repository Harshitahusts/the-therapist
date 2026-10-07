package app.haven.companion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.haven.companion.ui.theme.HavenColors
import app.haven.companion.core.VoicePhase
import kotlin.math.PI
import kotlin.math.sin

/**
 * The single visual element of a conversation. Breathes slowly when idle,
 * follows the user's voice when listening, swirls while thinking and pulses
 * while speaking.
 */
@Composable
fun VoiceOrb(phase: VoicePhase, micLevel: Float, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val color by animateColorAsState(
        when (phase) {
            VoicePhase.LISTENING -> HavenColors.Sea
            VoicePhase.THINKING, VoicePhase.CONNECTING -> HavenColors.Dusk
            VoicePhase.SPEAKING -> HavenColors.Sun
            VoicePhase.ERROR -> HavenColors.Danger
            VoicePhase.IDLE -> HavenColors.Idle
        },
        animationSpec = tween(600),
        label = "orbColor",
    )
    val transition = rememberInfiniteTransition(label = "orb")
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "pulse",
    )
    val level by animateFloatAsState(if (phase == VoicePhase.LISTENING) micLevel else 0f, tween(120), label = "level")

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val scale = when (phase) {
            VoicePhase.IDLE, VoicePhase.ERROR -> 0.80f + 0.05f * breath
            VoicePhase.LISTENING -> 0.82f + 0.03f * breath + 0.13f * level
            VoicePhase.THINKING, VoicePhase.CONNECTING -> 0.80f + 0.02f * sin(spin)
            VoicePhase.SPEAKING -> 0.85f + 0.05f * (0.5f + 0.5f * sin(pulse))
        }
        val core = r * scale
        // Soft halo
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center, r),
            radius = r,
        )
        // Body
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f)),
                center = center + Offset(-core * 0.25f, -core * 0.3f),
                radius = core * 1.4f,
            ),
            radius = core * 0.78f,
        )
        // Thinking: a slowly orbiting arc of light
        if (phase == VoicePhase.THINKING || phase == VoicePhase.CONNECTING) {
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = Math.toDegrees(spin.toDouble()).toFloat(),
                sweepAngle = 70f,
                useCenter = false,
                topLeft = center - Offset(core * 0.9f, core * 0.9f),
                size = androidx.compose.ui.geometry.Size(core * 1.8f, core * 1.8f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
