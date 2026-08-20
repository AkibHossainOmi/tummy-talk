package com.omi.kickcounter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A warm, low-glare palette. This screen gets looked at in a dark bedroom at 3am,
// so the dark scheme is the one that was designed first.
private val Blush = Color(0xFFF2A9C0)
private val Rose = Color(0xFFE07A9C)
private val RoseDeep = Color(0xFFB03D66)
private val Plum = Color(0xFF7C4DFF)
private val Honey = Color(0xFFF0C36D)

private val DarkColors = darkColorScheme(
    primary = Blush,
    onPrimary = Color(0xFF3E0B20),
    primaryContainer = Color(0xFF5E1E39),
    onPrimaryContainer = Color(0xFFFFE0EA),
    secondary = Honey,
    onSecondary = Color(0xFF3A2C05),
    secondaryContainer = Color(0xFF4E3C0C),
    onSecondaryContainer = Color(0xFFFFEEC2),
    tertiary = Color(0xFFB9A6FF),
    background = Color(0xFF100E16),
    onBackground = Color(0xFFEFE9F2),
    surface = Color(0xFF1A1722),
    onSurface = Color(0xFFEFE9F2),
    surfaceVariant = Color(0xFF272231),
    onSurfaceVariant = Color(0xFFB6ADC0),
    outline = Color(0xFF3A3345),
    outlineVariant = Color(0xFF2B2635),
)

private val LightColors = lightColorScheme(
    primary = RoseDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E5),
    onPrimaryContainer = Color(0xFF43081F),
    secondary = Color(0xFF7A5C0E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEFC7),
    onSecondaryContainer = Color(0xFF3A2C05),
    tertiary = Color(0xFF5B3FCB),
    background = Color(0xFFFFF9FB),
    onBackground = Color(0xFF1C1920),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1920),
    surfaceVariant = Color(0xFFF6EDF1),
    onSurfaceVariant = Color(0xFF5C525A),
    outline = Color(0xFFE3D6DD),
    outlineVariant = Color(0xFFEFE4EA),
)

/** Gradients and accents that aren't expressible as a flat M3 colour role. */
data class Accents(
    val screenGradient: Brush,
    val dialGradient: Brush,
    val barGradient: Brush,
    val glassBorder: Color,
    val glassFill: Color,
)

val LocalAccents = staticCompositionLocalOf<Accents> { error("Accents not provided") }

private fun darkAccents() = Accents(
    screenGradient = Brush.verticalGradient(
        listOf(Color(0xFF1B1226), Color(0xFF120F19), Color(0xFF100E16)),
    ),
    dialGradient = Brush.sweepGradient(listOf(Rose, Honey, Plum, Rose)),
    barGradient = Brush.verticalGradient(listOf(Blush, Rose)),
    glassBorder = Color(0x1FFFFFFF),
    glassFill = Color(0x0DFFFFFF),
)

private fun lightAccents() = Accents(
    screenGradient = Brush.verticalGradient(
        listOf(Color(0xFFFFF1F5), Color(0xFFFFF9FB), Color(0xFFFFFFFF)),
    ),
    dialGradient = Brush.sweepGradient(listOf(RoseDeep, Honey, Plum, RoseDeep)),
    barGradient = Brush.verticalGradient(listOf(Rose, RoseDeep)),
    glassBorder = Color(0x14000000),
    glassFill = Color(0x08000000),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
    )
}

/** Uppercase, wide-tracked caption used for section labels. */
val OverlineStyle = TextStyle(
    fontSize = 11.sp,
    letterSpacing = 1.6.sp,
    fontWeight = FontWeight.SemiBold,
)

@Composable
fun KickCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAccents provides if (darkTheme) darkAccents() else lightAccents()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
