package app.haven.companion.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.companion.ui.theme.HavenColors
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Haven's logo is a leaf made of six pieces held together by gold, like kintsugi.
 * The gold seams are where it broke — and they are also its veins: the broken places
 * are what carry life through it. When the app opens, the leaf cracks, drifts apart,
 * and grows back together.
 */

private val Gold = Color(0xFFE8B64C)
private val GoldLight = Color(0xFFFFDF8A)
private val ShardShades = listOf(
    Color(0xFF5BAA79), Color(0xFF4A9468), Color(0xFF6BB487),
    Color(0xFF448C61), Color(0xFF58A374), Color(0xFF3F8559),
)

internal class Shard(val path: Path, val centroid: Offset, val drift: Offset, val spin: Float)

internal class LeafModel {
    val shards: List<Shard>
    val seams: List<Path>

    init {
        val center = Offset(50f, 48f)
        shards = LogoGeometry.shards.mapIndexed { i, pts ->
            val path = Path()
            var cx = 0f
            var cy = 0f
            val n = pts.size / 2
            for (k in 0 until n) {
                val x = pts[2 * k]
                val y = pts[2 * k + 1]
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                cx += x
                cy += y
            }
            path.close()
            val c = Offset(cx / n, cy / n)
            val d = c - center
            val len = hypot(d.x, d.y).coerceAtLeast(1f)
            // Each piece drifts outward along its own direction, a little further for some, and turns gently.
            val reach = 15f + (i % 3) * 4f
            Shard(path, c, Offset(d.x / len * reach, d.y / len * reach - 3f), if (i % 2 == 0) 14f else -11f)
        }
        seams = LogoGeometry.seams.map { pts ->
            Path().apply {
                moveTo(pts[0], pts[1])
                for (k in 1 until pts.size / 2) lineTo(pts[2 * k], pts[2 * k + 1])
            }
        }
    }
}

/**
 * Draws the leaf.
 * [apart] 0 = whole, 1 = fully drifted apart. [crack] how visible the bare cracks are.
 * [gold] how much of each seam is traced in gold (0..1). [variety] how different the pieces' greens look.
 * [time] seconds, for the floating sway while apart.
 */
internal fun DrawScope.drawLeaf(model: LeafModel, apart: Float, crack: Float, gold: Float, variety: Float, time: Float) {
    val unit = size.minDimension / 100f
    translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f) {
        scale(unit, unit, pivot = Offset.Zero) {
            model.shards.forEachIndexed { i, s ->
                val sway = sin(time * 2.2f + i) * 1.6f * apart
                val dx = s.drift.x * apart + sway
                val dy = s.drift.y * apart + sin(time * 1.7f + i * 0.7f) * 1.2f * apart
                translate(dx, dy) {
                    rotate(s.spin * apart + sway * 2f, pivot = s.centroid) {
                        drawPath(s.path, lerp(HavenColors.Leaf, ShardShades[i], variety))
                        if (crack > 0f) {
                            drawPath(s.path, HavenColors.LeafDeep.copy(alpha = 0.55f * crack), style = Stroke(width = 0.7f))
                        }
                    }
                }
            }
            if (gold > 0f) {
                val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glow = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val measure = PathMeasure()
                model.seams.forEach { seam ->
                    measure.setPath(seam, false)
                    val traced = Path()
                    measure.getSegment(0f, measure.length * gold, traced, true)
                    drawPath(traced, GoldLight.copy(alpha = 0.35f * gold), style = glow)
                    drawPath(traced, Brush.linearGradient(listOf(GoldLight, Gold)), style = stroke)
                }
            }
        }
    }
}

/** The Haven leaf, whole and gold-seamed: the logo at rest. */
@Composable
fun HavenLogo(modifier: Modifier = Modifier) {
    val model = remember { LeafModel() }
    Canvas(modifier.semantics { contentDescription = "Haven" }) {
        drawLeaf(model, apart = 0f, crack = 0f, gold = 1f, variety = 0f, time = 0f)
    }
}

private val Soft = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

private fun phase(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

/**
 * The opening animation (about 5 s, tap to skip): the leaf appears whole, cracks,
 * drifts apart like falling leaves, floats back together, and its cracks fill with gold —
 * "broken things can come together again".
 */
@Composable
fun OpeningAnimation(onDone: () -> Unit) {
    val done = rememberUpdatedState(onDone)
    val model = remember { LeafModel() }
    val clock = remember { Animatable(0f) }
    val total = 5.2f

    LaunchedEffect(Unit) {
        // Respect "remove animations" in the system settings: show the finished logo briefly instead.
        if (!ValueAnimator.areAnimatorsEnabled()) {
            clock.snapTo(total)
            kotlinx.coroutines.delay(900)
        } else {
            clock.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing))
        }
        done.value()
    }

    val t = clock.value
    val appear = Soft.transform(phase(t, 0f, 0.6f))
    val crack = phase(t, 0.6f, 0.9f) * (1f - phase(t, 2.6f, 3.0f))
    val apart = Soft.transform(phase(t, 0.9f, 1.9f)) * (1f - Soft.transform(phase(t, 2.0f, 3.1f)))
    val variety = phase(t, 0.7f, 1.2f) * (1f - phase(t, 2.6f, 3.3f))
    val gold = Soft.transform(phase(t, 3.0f, 3.8f))
    val words = phase(t, 3.6f, 4.2f)

    LeafyBackground(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done.value() },
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(
                Modifier
                    .size(220.dp)
                    .alpha(appear)
                    .semantics { contentDescription = "Haven logo" },
            ) {
                val grow = 0.86f + 0.14f * appear
                scale(grow) { drawLeaf(model, apart, crack, gold, variety, t) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Haven",
                style = MaterialTheme.typography.displaySmall,
                color = HavenColors.Ink,
                modifier = Modifier.alpha(words),
            )
            Text(
                "Broken things can come together again.",
                style = MaterialTheme.typography.bodyLarge,
                color = HavenColors.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).alpha(words),
            )
        }
    }
}
