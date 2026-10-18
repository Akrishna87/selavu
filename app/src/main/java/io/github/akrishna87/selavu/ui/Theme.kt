package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colours for money: spending, income and the budget bar's warning shade, plus the soft
 * "container" tints the Expense and Income buttons are filled with.
 */
@Immutable
data class MoneyColors(
    val expense: Color,
    val income: Color,
    val warning: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val dark: Boolean,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(Color.Red, Color.Green, Color.Yellow, Color.Red, Color.White, Color.Green, Color.White, false)
}

// A calm palette: muted terracotta on warm off-white, with dusty rose for spending and sage for income.
private val LightMoney = MoneyColors(
    expense = Color(0xFFA9564C),
    income = Color(0xFF4F7A5A),
    warning = Color(0xFFB98A3E),
    expenseContainer = Color(0xFFF1DCD7),
    onExpenseContainer = Color(0xFF6E322B),
    incomeContainer = Color(0xFFDCE8DD),
    onIncomeContainer = Color(0xFF2D4F36),
    dark = false,
)

private val DarkMoney = MoneyColors(
    expense = Color(0xFFE0A49B),
    income = Color(0xFFA5C9AD),
    warning = Color(0xFFDDBE85),
    expenseContainer = Color(0xFF4A2F2B),
    onExpenseContainer = Color(0xFFF1D3CE),
    incomeContainer = Color(0xFF2B3D30),
    onIncomeContainer = Color(0xFFCFE5D3),
    dark = true,
)

private val Light = lightColorScheme(
    primary = Color(0xFF94593F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E0D6),
    onPrimaryContainer = Color(0xFF3A2116),
    secondary = Color(0xFF7A6156),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE0D8),
    onSecondaryContainer = Color(0xFF2E1F18),
    background = Color(0xFFFBF8F5),
    onBackground = Color(0xFF241D19),
    surface = Color(0xFFFBF8F5),
    onSurface = Color(0xFF241D19),
    surfaceVariant = Color(0xFFEFE4DE),
    onSurfaceVariant = Color(0xFF574841),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F1ED),
    surfaceContainer = Color(0xFFF3EBE6),
    surfaceContainerHigh = Color(0xFFEEE5DF),
    surfaceContainerHighest = Color(0xFFE8DED7),
    outline = Color(0xFF8A7A72),
    outlineVariant = Color(0xFFDACBC3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE3B9A3),
    onPrimary = Color(0xFF4A2818),
    primaryContainer = Color(0xFF4E3428),
    onPrimaryContainer = Color(0xFFF4DDD1),
    secondary = Color(0xFFDCC4B8),
    onSecondary = Color(0xFF3E2D25),
    secondaryContainer = Color(0xFF4A3C35),
    onSecondaryContainer = Color(0xFFF0E0D7),
    background = Color(0xFF1A1512),
    onBackground = Color(0xFFEDE1DA),
    surface = Color(0xFF1A1512),
    onSurface = Color(0xFFEDE1DA),
    surfaceVariant = Color(0xFF4F443E),
    onSurfaceVariant = Color(0xFFD4C4BB),
    surfaceContainerLowest = Color(0xFF14100D),
    surfaceContainerLow = Color(0xFF221C18),
    surfaceContainer = Color(0xFF27201C),
    surfaceContainerHigh = Color(0xFF312a25),
    surfaceContainerHighest = Color(0xFF3B332E),
    outline = Color(0xFF9D8D85),
    outlineVariant = Color(0xFF4F443E),
)

/**
 * A category's colour as it's drawn: softened (less saturated, even brightness) so icons and
 * chart slices stay colourful without shouting. Applied when drawing rather than to the saved
 * colour, so categories already on the phone and ones picked later all look the same.
 */
fun softColor(argb: Long, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    hsv[1] = hsv[1] * if (dark) 0.55f else 0.62f
    hsv[2] = if (dark) 0.82f else 0.74f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun categoryColor(argb: Long): Color = softColor(argb, LocalMoneyColors.current.dark)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun SelavuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val money = if (dark) DarkMoney else LightMoney
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
