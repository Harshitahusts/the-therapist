package app.haven.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A light, leafy palette: morning sage, fresh leaf green, warm sunlight. */
object HavenColors {
    val Mist = Color(0xFFF4F9F2)       // top of the background
    val Meadow = Color(0xFFDDEEDB)     // bottom of the background
    val Card = Color(0xFFFFFFFF)
    val CardSoft = Color(0xFFEAF4E7)
    val Ink = Color(0xFF1F3A2E)        // main text
    val InkSoft = Color(0xFF5B7768)    // secondary text
    val Leaf = Color(0xFF4F9C6E)       // primary
    val LeafDeep = Color(0xFF2E6B4A)
    val LeafLight = Color(0xFFA8D5B5)
    val Sun = Color(0xFFF4B183)        // speaking
    val Dusk = Color(0xFFB4A5E6)       // thinking
    val Sea = Color(0xFF63C2A0)        // listening
    val Idle = Color(0xFF8FC1A1)
    val Butter = Color(0xFFFFDF8A)     // the leaf buddy's face
    val Blush = Color(0xFFF7A9A0)
    val Danger = Color(0xFFD9655A)
}

private val scheme = lightColorScheme(
    primary = HavenColors.Leaf,
    onPrimary = Color.White,
    primaryContainer = HavenColors.CardSoft,
    onPrimaryContainer = HavenColors.LeafDeep,
    secondary = HavenColors.LeafDeep,
    background = HavenColors.Mist,
    onBackground = HavenColors.Ink,
    surface = HavenColors.Card,
    onSurface = HavenColors.Ink,
    surfaceVariant = HavenColors.CardSoft,
    onSurfaceVariant = HavenColors.InkSoft,
    outline = HavenColors.LeafLight,
    error = HavenColors.Danger,
)

private val rounded = FontFamily.SansSerif
private val display = FontFamily.Serif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = display, fontSize = 34.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = display, fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = rounded, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = rounded, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = rounded, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = rounded, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = rounded, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = rounded, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

@Composable
fun HavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
