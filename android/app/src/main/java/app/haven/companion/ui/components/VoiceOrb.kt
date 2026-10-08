package app.haven.companion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.haven.companion.core.VoicePhase
import app.haven.companion.ui.theme.HavenColors
import kotlin.math.abs
import kotlin.math.sin

/**
 * The single visual element of a conversation: Haven's gold-seamed leaf with a small face.
 *  - Idle: sways gently and blinks now and then.
 *  - Listening: leans in, its pieces open a little with your voice, ripples flow *into* it, and its seams glow.
 *  - Thinking: eyes closed, gold travels along the seams.
 *  - Speaking: bounces softly, mouth moving, ripples flow out and tiny leaves float up.
 */
@Composable
fun VoiceOrb(phase: VoicePhase, micLevel: Float, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val model = remember { LeafModel() }
    val clock by rememberInfiniteTransition(label = "leaf").animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "leafClock",
    )
    val listening by animateFloatAsState(if (phase == VoicePhase.LISTENING) 1f else 0f, tween(500), label = "listening")
    val speaking by animateFloatAsState(if (phase == VoicePhase.SPEAKING) 1f else 0f, tween(400), label = "speaking")
    val thinking by animateFloatAsState(
        if (phase == VoicePhase.THINKING || phase == VoicePhase.CONNECTING) 1f else 0f, tween(500), label = "thinking",
    )
    val level by animateFloatAsState(if (phase == VoicePhase.LISTENING) micLevel.coerceIn(0f, 1f) else 0f, tween(110), label = "level")
    val dim = phase == VoicePhase.ERROR

    Canvas(modifier.size(size)) {
        val t = clock
        val c = center
        val r = this.size.minDimension / 2f

        // Ripples: inward while listening (taking your words in), outward while speaking.
        repeat(3) { k ->
            val f = ((t * 0.45f + k / 3f) % 1f)
            if (listening > 0.01f) {
                val p = 1f - f
                drawCircle(HavenColors.Sea.copy(alpha = 0.28f * f * listening * (0.5f + level)), r * (0.55f + 0.45f * p), c, style = Stroke(2.dp.toPx()))
            }
            if (speaking > 0.01f) {
                drawCircle(HavenColors.Sun.copy(alpha = 0.30f * (1f - f) * speaking), r * (0.55f + 0.45f * f), c, style = Stroke(2.dp.toPx()))
            }
        }
        // Soft halo in the mood colour.
        val mood = when {
            dim -> HavenColors.Danger
            speaking > 0.5f -> HavenColors.Sun
            listening > 0.5f -> HavenColors.Sea
            thinking > 0.5f -> HavenColors.Dusk
            else -> HavenColors.Idle
        }
        drawCircle(Brush.radialGradient(listOf(mood.copy(alpha = 0.30f), Color.Transparent), c, r), r, c)

        // Body motion.
        val sway = sin(t * 1.1f) * 4f * (1f - speaking) + sin(t * 9f) * 5f * speaking
        val bounce = abs(sin(t * 6.5f)) * speaking
        val lean = 7f * listening
        val grow = 0.78f + 0.03f * sin(t * 1.6f) + 0.05f * level + 0.04f * bounce
        val apart = 0.06f * listening + 0.22f * level + 0.05f * bounce
        val gold = if (thinking > 0.5f) (t * 0.6f) % 1f else 1f

        rotate(sway + lean, pivot = Offset(c.x, c.y + r * 0.7f)) {
            scale(grow, pivot = c) {
                translate(top = -bounce * r * 0.06f) {
                    drawLeaf(model, apart = apart, crack = 0f, gold = gold, variety = 0.25f * listening, time = t)
                    drawFace(t, listening, speaking, thinking)
                }
            }
        }

        // Tiny leaves drifting up while Haven speaks.
        if (speaking > 0.01f) {
            repeat(4) { k ->
                val f = (t * 0.35f + k / 4f) % 1f
                val x = c.x + (k - 1.5f) * r * 0.32f + sin(t * 2f + k) * r * 0.06f
                val y = c.y - r * 0.25f - f * r * 0.75f
                drawMiniLeaf(Offset(x, y), r * 0.07f, (k * 50f + t * 60f) % 360f, HavenColors.LeafLight.copy(alpha = (1f - f) * speaking))
            }
        }
    }
}

/** A small, friendly face on the leaf (in the canvas' own coordinates, matching drawLeaf's 100-unit box). */
private fun DrawScope.drawFace(t: Float, listening: Float, speaking: Float, thinking: Float) {
    val unit = size.minDimension / 100f
    val ox = (size.width - 100f * unit) / 2f
    val oy = (size.height - 100f * unit) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)
    val ink = HavenColors.Ink

    // A soft patch of leaf behind the face, so the gold seams fade out under it.
    val patch = Color(0xFF5BAA79)
    drawCircle(
        Brush.radialGradient(
            0f to patch.copy(alpha = 0.95f), 0.6f to patch.copy(alpha = 0.75f), 1f to patch.copy(alpha = 0f),
            center = p(50f, 53f), radius = 17f * unit,
        ),
        radius = 17f * unit, center = p(50f, 53f),
    )

    // Cheeks
    val blush = HavenColors.Blush.copy(alpha = 0.55f + 0.25f * speaking)
    drawCircle(blush, 3.4f * unit, p(35f, 55f))
    drawCircle(blush, 3.4f * unit, p(65f, 55f))

    // Eyes: closed and content while thinking, a blink every few seconds otherwise.
    val blink = ((t % 4.3f) < 0.14f)
    val eyeY = 46f - 1.5f * listening
    val closed = thinking > 0.5f || blink
    for (ex in listOf(40f, 60f)) {
        if (closed) {
            drawArc(ink, 20f, 140f, false, p(ex - 3.2f, eyeY - 2.2f), Size(6.4f * unit, 4.4f * unit),
                style = Stroke(1.6f * unit, cap = StrokeCap.Round))
        } else {
            val big = 1f + 0.18f * listening
            drawCircle(ink, 2.7f * unit * big, p(ex, eyeY))
            drawCircle(Color.White, 0.95f * unit * big, p(ex + 0.9f, eyeY - 1f))
        }
    }

    // Mouth: a soft smile, an attentive little "o" while listening, and chatting while speaking.
    val talk = speaking * (0.35f + 0.65f * abs(sin(t * 11f) * sin(t * 4.3f + 1f)))
    when {
        talk > 0.08f -> {
            val h = 2f + 6f * talk
            drawOval(HavenColors.LeafDeep, p(45.5f, 57f), Size(9f * unit, h * unit))
            drawOval(HavenColors.Blush, p(47.5f, 57f + h * 0.55f), Size(5f * unit, h * 0.4f * unit))
        }
        listening > 0.5f -> drawOval(ink, p(48f, 57f), Size(4f * unit, 3.6f * unit), style = Stroke(1.5f * unit))
        else -> drawArc(ink, 20f, 140f, false, p(44f, 53f), Size(12f * unit, 7f * unit),
            style = Stroke(1.7f * unit, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMiniLeaf(at: Offset, len: Float, angle: Float, color: Color) {
    rotate(angle, pivot = at) {
        drawOval(color, Offset(at.x - len, at.y - len * 0.45f), Size(len * 2f, len * 0.9f))
    }
}
