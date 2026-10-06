package app.haven.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HavenColors {
    val Background = Color(0xFF0E1720)
    val Surface = Color(0xFF152330)
    val SurfaceRaised = Color(0xFF1B2D3B)
    val Text = Color(0xFFE8EEF2)
    val TextMuted = Color(0xFF9DB0BD)
    val Sea = Color(0xFF9FD4C9)       // listening
    val Dusk = Color(0xFFB7A6E0)      // thinking
    val Sun = Color(0xFFF2C894)       // speaking
    val Idle = Color(0xFF7FA3B8)
    val Danger = Color(0xFFF08F86)
}

private val scheme = darkColorScheme(
    primary = HavenColors.Sea,
    onPrimary = Color(0xFF0B2A26),
    secondary = HavenColors.Dusk,
    background = HavenColors.Background,
    onBackground = HavenColors.Text,
    surface = HavenColors.Surface,
    onSurface = HavenColors.Text,
    surfaceVariant = HavenColors.SurfaceRaised,
    onSurfaceVariant = HavenColors.TextMuted,
    error = HavenColors.Danger,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
