package app.haven.companion.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.haven.companion.ui.theme.HavenColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val BUDDY_PROMPT = "Click me! 👋"
const val BUDDY_SMILE = "Smiling makes you even more beautiful ✨"

/**
 * Haven's little helper: a smiling face with a leaf sprout that drifts around
 * like a leaf on the breeze (side to side, up and down, diagonally) and tilts
 * forward in the direction it's moving. Tap it for a smile.
 */
@Composable
fun LeafBuddy(modifier: Modifier = Modifier, roam: Dp = 70.dp, size: Dp = 96.dp) {
    var tapped by rememberSaveable { mutableStateOf(false) }
    val clock by rememberInfiniteTransition(label = "buddy").animateFloat(
        initialValue = 0f, targetValue = LOOP_SECONDS,
        animationSpec = infiniteRepeatable(tween((LOOP_SECONDS * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "buddyClock",
    )
    val joy by animateFloatAsState(if (tapped) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "joy")

    // A smooth, non-repeating-looking path: two incommensurate sines per axis.
    val t = clock * 2 * PI.toFloat() / LOOP_SECONDS
    val x = 0.75f * sin(3 * t) + 0.25f * sin(7 * t + 1f)
    val y = 0.6f * sin(4 * t + 0.6f) + 0.4f * sin(2 * t)
    val dx = 0.75f * 3 * cos(3 * t) + 0.25f * 7 * cos(7 * t + 1f)
    val tilt = (dx * 7f).coerceIn(-18f, 18f) // lean forward into the motion

    Box(modifier.fillMaxWidth().height(size + roam * 1.3f + 56.dp), contentAlignment = Alignment.Center) {
        // The bubble and the buddy move together, so "Click me" is always on the leaf itself.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationX = x * roam.toPx()
                translationY = y * roam.toPx() * 0.55f
            },
        ) {
            AnimatedContent(
                targetState = tapped,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith fadeOut() },
                label = "bubble",
            ) { smiling ->
                Text(
                    if (smiling) BUDDY_SMILE else BUDDY_PROMPT,
                    style = MaterialTheme.typography.labelLarge,
                    color = HavenColors.LeafDeep,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .shadow(3.dp, RoundedCornerShape(14.dp))
                        .background(HavenColors.Card, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Canvas(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = tilt
                        val bounce = 1f + 0.12f * joy
                        scaleX = bounce; scaleY = bounce
                    }
                    .semantics { contentDescription = if (tapped) BUDDY_SMILE else "Leaf buddy. Tap me." }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { tapped = !tapped }
            ) { drawBuddy(joy) }
        }
    }
}

private const val LOOP_SECONDS = 40f

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBuddy(joy: Float) {
    val r = size.minDimension / 2f * 0.82f
    val c = Offset(size.width / 2f, size.height / 2f + r * 0.12f)
    // Sprout on top
    leaf(Offset(c.x, c.y - r * 0.92f), r * 0.75f, -60f, HavenColors.Leaf)
    leaf(Offset(c.x, c.y - r * 0.92f), r * 0.55f, -125f, HavenColors.LeafLight)
    // Face
    drawCircle(HavenColors.Butter, r, c)
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.35f, Offset(c.x - r * 0.38f, c.y - r * 0.42f))
    // Cheeks (rosier when smiling)
    val blush = HavenColors.Blush.copy(alpha = 0.45f + 0.4f * joy)
    drawCircle(blush, r * 0.16f, Offset(c.x - r * 0.52f, c.y + r * 0.18f))
    drawCircle(blush, r * 0.16f, Offset(c.x + r * 0.52f, c.y + r * 0.18f))
    // Eyes: round, or happy closed arcs after a tap
    val eyeY = c.y - r * 0.12f
    if (joy < 0.5f) {
        drawCircle(HavenColors.Ink, r * 0.1f, Offset(c.x - r * 0.32f, eyeY))
        drawCircle(HavenColors.Ink, r * 0.1f, Offset(c.x + r * 0.32f, eyeY))
        drawCircle(Color.White, r * 0.035f, Offset(c.x - r * 0.29f, eyeY - r * 0.035f))
        drawCircle(Color.White, r * 0.035f, Offset(c.x + r * 0.35f, eyeY - r * 0.035f))
    } else {
        val s = Stroke(width = r * 0.08f, cap = StrokeCap.Round)
        for (ex in listOf(-0.32f, 0.32f)) {
            drawArc(HavenColors.Ink, 200f, 140f, false, Offset(c.x + r * ex - r * 0.13f, eyeY - r * 0.06f), Size(r * 0.26f, r * 0.22f), style = s)
        }
    }
    // Smile, wider with joy
    val w = r * (0.7f + 0.25f * joy)
    drawArc(
        HavenColors.Ink, 15f, 150f, false,
        Offset(c.x - w / 2, c.y - r * 0.05f), Size(w, r * (0.45f + 0.2f * joy)),
        style = Stroke(width = r * 0.085f, cap = StrokeCap.Round),
    )
}
