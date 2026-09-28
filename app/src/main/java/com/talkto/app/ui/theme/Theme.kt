package com.talkto.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Toy-like palette: a cream "device shell", mint creature, sunflower accents. */
object TalktoColors {
    val Shell = Color(0xFFF7F1E8)
    val ShellDark = Color(0xFF1E1B22)
    val Screen = Color(0xFFE8F3E1)
    val ScreenDark = Color(0xFF2A3326)
    val Mint = Color(0xFF7BD389)
    val Sunflower = Color(0xFFFFC857)
    val Tomato = Color(0xFFE4572E)
    val Ink = Color(0xFF2D2A32)
    val Denim = Color(0xFF3F88C5)
}

private val Light = lightColorScheme(
    primary = TalktoColors.Ink,
    onPrimary = TalktoColors.Shell,
    secondary = TalktoColors.Sunflower,
    onSecondary = TalktoColors.Ink,
    tertiary = TalktoColors.Mint,
    background = TalktoColors.Shell,
    onBackground = TalktoColors.Ink,
    surface = Color.White,
    onSurface = TalktoColors.Ink,
    surfaceVariant = TalktoColors.Screen,
    error = TalktoColors.Tomato,
)

private val Dark = darkColorScheme(
    primary = TalktoColors.Sunflower,
    onPrimary = TalktoColors.Ink,
    secondary = TalktoColors.Mint,
    onSecondary = TalktoColors.Ink,
    tertiary = TalktoColors.Mint,
    background = TalktoColors.ShellDark,
    onBackground = TalktoColors.Shell,
    surface = Color(0xFF29252E),
    onSurface = TalktoColors.Shell,
    surfaceVariant = TalktoColors.ScreenDark,
    error = TalktoColors.Tomato,
)

private val Rounded = FontFamily.SansSerif

private val TalktoTypography = Typography(
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp),
)

private val TalktoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(32.dp),
)

@Composable
fun TalktoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = TalktoTypography,
        shapes = TalktoShapes,
        content = content,
    )
}
