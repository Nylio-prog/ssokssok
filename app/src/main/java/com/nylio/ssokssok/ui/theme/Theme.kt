package com.nylio.ssokssok.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nylio.ssokssok.R

/*
 * Palette: 쪽빛 (jjok, traditional indigo dye) for action, 청자 (celadon) green-grey for
 * surfaces and success, 단청 vermilion for the streak and for "Hard".
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F3E9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE1FA),
    onPrimaryContainer = Color(0xFF111A5E),
    inversePrimary = Color(0xFFB8C3FF),
    secondary = Color(0xFF3F7F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DF),
    onSecondaryContainer = Color(0xFF0E3A30),
    tertiary = Color(0xFFB8452B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF4A1206),
    background = Color(0xFFF4F6F3),
    onBackground = Color(0xFF161B1A),
    surface = Color(0xFFF4F6F3),
    onSurface = Color(0xFF161B1A),
    surfaceVariant = Color(0xFFDDE5E1),
    onSurfaceVariant = Color(0xFF4A5552),
    surfaceTint = Color(0xFF2F3E9E),
    inverseSurface = Color(0xFF2B3130),
    inverseOnSurface = Color(0xFFEDF2EF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF79847F),
    outlineVariant = Color(0xFFC8D1CD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFCFA),
    surfaceDim = Color(0xFFD7DDD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2EF),
    surfaceContainer = Color(0xFFE8EDEA),
    surfaceContainerHigh = Color(0xFFE2E8E5),
    surfaceContainerHighest = Color(0xFFDCE3DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF18257A),
    primaryContainer = Color(0xFF34449F),
    onPrimaryContainer = Color(0xFFDEE1FA),
    inversePrimary = Color(0xFF2F3E9E),
    secondary = Color(0xFF9ED3C3),
    onSecondary = Color(0xFF0B372D),
    secondaryContainer = Color(0xFF275F52),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFFFB4A1),
    onTertiary = Color(0xFF5E1A0A),
    tertiaryContainer = Color(0xFF8C2E19),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF0E1312),
    onBackground = Color(0xFFE3E9E6),
    surface = Color(0xFF0E1312),
    onSurface = Color(0xFFE3E9E6),
    surfaceVariant = Color(0xFF3A4542),
    onSurfaceVariant = Color(0xFFA9B5B1),
    surfaceTint = Color(0xFFB8C3FF),
    inverseSurface = Color(0xFFE3E9E6),
    inverseOnSurface = Color(0xFF2B3130),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF74807C),
    outlineVariant = Color(0xFF3A4542),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B39),
    surfaceDim = Color(0xFF0E1312),
    surfaceContainerLowest = Color(0xFF090D0C),
    surfaceContainerLow = Color(0xFF151B1A),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF232B29),
    surfaceContainerHighest = Color(0xFF2D3634),
)

/** Colors with a meaning of their own, separate from the brand accent. */
@Immutable
data class SsokColors(
    val hard: Color,
    val hardContainer: Color,
    val medium: Color,
    val mediumContainer: Color,
    val easy: Color,
    val easyContainer: Color,
    val streak: Color,
    val heatScale: List<Color>,
    val deckPalette: List<Pair<Color, Color>>,
)

private val LightSsok = SsokColors(
    hard = Color(0xFFB8452B),
    hardContainer = Color(0xFFFFE3DB),
    medium = Color(0xFF9A6A12),
    mediumContainer = Color(0xFFFCEBC8),
    easy = Color(0xFF2C7A62),
    easyContainer = Color(0xFFD3EEE4),
    streak = Color(0xFFD9542F),
    heatScale = listOf(
        Color(0xFFE2E8E5), Color(0xFFC3CBF4), Color(0xFF8E9BE3), Color(0xFF5566C4), Color(0xFF2F3E9E),
    ),
    // (tile background, symbol color)
    deckPalette = listOf(
        Color(0xFFDEE1FA) to Color(0xFF2F3E9E),
        Color(0xFFCDE8DF) to Color(0xFF2C6B5C),
        Color(0xFFFFDBD1) to Color(0xFFA63C24),
        Color(0xFFEBDDF3) to Color(0xFF6A3F86),
        Color(0xFFFCEBC8) to Color(0xFF85590C),
        Color(0xFFD6E6F5) to Color(0xFF2A5D8A),
        Color(0xFFF7D9E3) to Color(0xFF9A3459),
        Color(0xFFDCE6CF) to Color(0xFF4A6B27),
    ),
)

private val DarkSsok = SsokColors(
    hard = Color(0xFFFF9A82),
    hardContainer = Color(0xFF4A1E14),
    medium = Color(0xFFF2C46D),
    mediumContainer = Color(0xFF42320F),
    easy = Color(0xFF86D9BC),
    easyContainer = Color(0xFF173F33),
    streak = Color(0xFFFF8A65),
    heatScale = listOf(
        Color(0xFF232B29), Color(0xFF2E3A73), Color(0xFF4656B8), Color(0xFF7D8DF0), Color(0xFFB8C3FF),
    ),
    deckPalette = listOf(
        Color(0xFF2A3368) to Color(0xFFC6CEFF),
        Color(0xFF1F4A40) to Color(0xFFA6DCCB),
        Color(0xFF5A2316) to Color(0xFFFFB9A6),
        Color(0xFF412A52) to Color(0xFFE2C4F2),
        Color(0xFF4A3712) to Color(0xFFF5CF84),
        Color(0xFF1E3B57) to Color(0xFFB2D3F2),
        Color(0xFF55233A) to Color(0xFFF5B3CB),
        Color(0xFF2E4220) to Color(0xFFC4DDA5),
    ),
)

val LocalSsokColors = staticCompositionLocalOf { LightSsok }

private fun jakarta(weight: Int) = Font(
    R.font.jakarta,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(
    jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800),
)

/** Rounded Korean face for the cards themselves. */
val KoreanDisplay = FontFamily(Font(R.font.gowun_dodum, FontWeight.Normal))

private val base = Typography()

private fun TextStyle.j(weight: Int, size: Int? = null, line: Int? = null, spacing: Double? = null) = copy(
    fontFamily = Jakarta,
    fontWeight = FontWeight(weight),
    fontSize = size?.sp ?: fontSize,
    lineHeight = line?.sp ?: lineHeight,
    letterSpacing = spacing?.em ?: letterSpacing,
)

val SsokTypography = Typography(
    displayLarge = base.displayLarge.j(800, 52, 58, -0.03),
    displayMedium = base.displayMedium.j(800, 42, 48, -0.03),
    displaySmall = base.displaySmall.j(800, 34, 40, -0.02),
    headlineLarge = base.headlineLarge.j(800, 30, 36, -0.02),
    headlineMedium = base.headlineMedium.j(700, 26, 32, -0.015),
    headlineSmall = base.headlineSmall.j(700, 22, 28, -0.01),
    titleLarge = base.titleLarge.j(700, 20, 26, -0.01),
    titleMedium = base.titleMedium.j(700, 16, 22, 0.0),
    titleSmall = base.titleSmall.j(600, 14, 20, 0.0),
    bodyLarge = base.bodyLarge.j(400, 16, 24, 0.0),
    bodyMedium = base.bodyMedium.j(400, 14, 21, 0.0),
    bodySmall = base.bodySmall.j(500, 12, 17, 0.0),
    labelLarge = base.labelLarge.j(700, 14, 20, 0.0),
    labelMedium = base.labelMedium.j(600, 12, 16, 0.02),
    labelSmall = base.labelSmall.j(700, 11, 14, 0.06),
)

val SsokShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SsokTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalSsokColors provides if (dark) DarkSsok else LightSsok) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SsokTypography,
            shapes = SsokShapes,
            content = content,
        )
    }
}

object Ssok {
    val colors: SsokColors
        @Composable get() = LocalSsokColors.current
}
