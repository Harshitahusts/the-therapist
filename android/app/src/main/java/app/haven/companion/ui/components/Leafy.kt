package app.haven.companion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.haven.companion.ui.theme.HavenColors

/** A soft morning gradient with a few leaves swaying at the edges. */
@Composable
fun LeafyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "swayValue",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HavenColors.Mist, HavenColors.Meadow)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            leaf(Offset(size.width + 10f, 40f), 210f, 205f + sway * 4f, HavenColors.LeafLight.copy(alpha = 0.55f))
            leaf(Offset(size.width - 30f, 10f), 150f, 240f + sway * 6f, HavenColors.Leaf.copy(alpha = 0.25f))
            leaf(Offset(-20f, size.height * 0.62f), 180f, 20f - sway * 5f, HavenColors.LeafLight.copy(alpha = 0.45f))
            leaf(Offset(-10f, size.height * 0.66f), 120f, 55f - sway * 4f, HavenColors.Leaf.copy(alpha = 0.18f))
            leaf(Offset(size.width + 20f, size.height - 30f), 230f, 160f + sway * 3f, HavenColors.LeafLight.copy(alpha = 0.4f))
        }
        content()
    }
}

/** A simple leaf: two curves meeting at a tip, with a centre vein. */
fun DrawScope.leaf(base: Offset, length: Float, angleDeg: Float, color: Color) {
    rotate(angleDeg, pivot = base) {
        val w = length * 0.38f
        val p = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(base.x + length * 0.3f, base.y - w, base.x + length * 0.75f, base.y - w * 0.8f, base.x + length, base.y)
            cubicTo(base.x + length * 0.75f, base.y + w * 0.8f, base.x + length * 0.3f, base.y + w, base.x, base.y)
            close()
        }
        drawPath(p, color)
        drawLine(Color.White.copy(alpha = 0.35f), base, Offset(base.x + length * 0.9f, base.y), strokeWidth = 2.5f)
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = HavenColors.Leaf, contentColor = Color.White),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) { Text(text, color = HavenColors.LeafDeep, modifier = Modifier.padding(horizontal = 4.dp)) }
}
