package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's colours, after Apple's system colours: a grey grouped background with white
 * cards, black and grey text, hairline separators, and colour kept for small accents.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    /** systemGroupedBackground: behind the cards. */
    val background: Color,
    /** secondarySystemGroupedBackground: the cards. */
    val card: Color,
    /** tertiarySystemFill: segmented control tracks, unselected chips, text field wells. */
    val fill: Color,
    /** The selected segment of a segmented control. */
    val segment: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    /** systemBlue: buttons, links, selection. */
    val tint: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val gray: Color,
    /** The tab bar. */
    val bar: Color,
)

private val LightPalette = Palette(
    dark = false,
    background = Color(0xFFF2F2F7),
    card = Color.White,
    fill = Color(0x1F767680),
    segment = Color.White,
    separator = Color(0x4A3C3C43),
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    tint = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    gray = Color(0xFF8E8E93),
    bar = Color(0xFFF9F9F9),
)

private val DarkPalette = Palette(
    dark = true,
    background = Color.Black,
    card = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    segment = Color(0xFF636366),
    separator = Color(0x99545458),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    tint = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    gray = Color(0xFF8E8E93),
    bar = Color(0xFF161618),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Shorthand for the current palette inside composables. */
val palette: Palette
    @Composable get() = LocalPalette.current

// Apple's system colours, light and dark, that category colours are drawn in.
private val SystemLight = listOf(
    0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF30B0C7,
    0xFF32ADE6, 0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55,
)
private val SystemDark = listOf(
    0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF63E6E2, 0xFF40C8E0,
    0xFF64D2FF, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F,
)
private val SystemHues = listOf(3f, 35f, 48f, 135f, 177f, 190f, 199f, 211f, 241f, 286f, 349f)

/**
 * A category's colour as it's drawn: the nearest of Apple's system colours (by hue; brown and
 * grey for the muted ones). Saved colours stay as they are, so categories already on the phone
 * and ones picked later both come out in the same family.
 */
fun systemColor(argb: Long, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    if (s < 0.25f) return Color(0xFF8E8E93)
    if (h in 15f..45f && v < 0.7f) return if (dark) Color(0xFFAC8E68) else Color(0xFFA2845E)
    val i = SystemHues.indices.minBy { val d = kotlin.math.abs(SystemHues[it] - h); minOf(d, 360 - d) }
    return Color((if (dark) SystemDark else SystemLight)[i])
}

@Composable
fun categoryColor(argb: Long): Color = systemColor(argb, palette.dark)

/** Spending is shown in the ordinary text colour, as Apple's apps show money; income in green. */
@Composable
fun amountColor(isExpense: Boolean): Color = if (isExpense) palette.label else palette.green

// Apple's text styles: Large Title 34, Title 2 22, Headline 17 semibold, Body 17,
// Subheadline 15, Footnote 13, Caption 12, with SF's slightly tight tracking.
private fun t(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = -0.2) =
    TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.sp, lineHeight = (size * 1.25).sp)

private val AppTypography = Typography(
    displayLarge = t(48, FontWeight.Bold, -1.0),
    displayMedium = t(40, FontWeight.Bold, -0.8),
    displaySmall = t(34, FontWeight.Bold, -0.6),
    headlineLarge = t(28, FontWeight.Bold, -0.5),
    headlineMedium = t(28, FontWeight.Bold, -0.5),
    headlineSmall = t(22, FontWeight.Bold, -0.4),
    titleLarge = t(22, FontWeight.Bold, -0.4),
    titleMedium = t(17, FontWeight.SemiBold, -0.4),
    titleSmall = t(15, FontWeight.SemiBold, -0.2),
    bodyLarge = t(17, tracking = -0.4),
    bodyMedium = t(15, tracking = -0.2),
    bodySmall = t(13, tracking = -0.1),
    labelLarge = t(15, FontWeight.Medium, -0.2),
    labelMedium = t(13, FontWeight.Normal, -0.1),
    labelSmall = t(11, FontWeight.Medium, 0.0),
)

@Composable
fun SelavuTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.tint,
        onPrimary = Color.White,
        primaryContainer = p.fill,
        onPrimaryContainer = p.label,
        secondary = p.tint,
        secondaryContainer = p.fill,
        onSecondaryContainer = p.label,
        background = p.background,
        onBackground = p.label,
        surface = p.card,
        onSurface = p.label,
        surfaceVariant = p.fill,
        onSurfaceVariant = p.secondaryLabel,
        surfaceContainerLowest = p.card,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        outline = p.separator,
        outlineVariant = p.separator,
        error = p.red,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
