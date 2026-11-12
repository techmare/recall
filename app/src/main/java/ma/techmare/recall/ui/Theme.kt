package ma.techmare.recall.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import ma.techmare.recall.R

object C {
    val Ground = Color(0xFF0F1115)
    val Surface = Color(0xFF181B22)
    val Surface2 = Color(0xFF22262F)
    val Nav = Color(0xFF13151B)
    val Track = Color(0xFF2A2E38)
    val Text = Color(0xFFE9EBF0)
    val Muted = Color(0xFF9AA0AD)
    val Accent = Color(0xFF8B7CF6)
    val AccentSoft = Color(0xFFB3A8FF)
    val AccentPale = Color(0xFFC9C1FF)
    val AccentDim = Color(0xFF4B3F9E)
    val AccentDeep = Color(0xFF2E2860)
    val AccentChip = Color(0xFF2A2550)
    val Blue = Color(0xFF6AA8F7); val BlueBg = Color(0xFF172131)
    val Amber = Color(0xFFF5B544); val AmberBg = Color(0xFF261F12)
    val Green = Color(0xFF5FD39A); val GreenBg = Color(0xFF13251D)
    val Rose = Color(0xFFF59A9A); val RoseBg = Color(0xFF2A1719)
    val deckColors = listOf(Accent, Green, Amber, Blue)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(weights.map { w ->
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

val Display = variable(R.font.space_grotesk, 500, 600, 700)
val Body = variable(R.font.manrope, 400, 500, 600, 700)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val t = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Body),
        bodyMedium = base.bodyMedium.copy(fontFamily = Body),
        bodySmall = base.bodySmall.copy(fontFamily = Body),
        labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Body),
        titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.Ground, background = C.Ground, onBackground = C.Text,
            surface = C.Surface, onSurface = C.Text, surfaceVariant = C.Surface2, onSurfaceVariant = C.Muted,
            surfaceContainerHigh = C.Surface, surfaceContainer = C.Surface, outline = C.Track,
            secondary = C.AccentSoft, error = C.Rose,
        ),
        typography = t,
        content = content,
    )
}

fun body(size: Int, weight: Int = 500, color: Color = C.Text) =
    TextStyle(fontFamily = Body, fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight(weight), color = color)

fun display(size: Int, weight: Int = 600, color: Color = C.Text) =
    TextStyle(fontFamily = Display, fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight(weight), color = color)
