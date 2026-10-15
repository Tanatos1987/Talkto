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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkto.app.R

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

/** Nunito: round, friendly letters with full Cyrillic, easy for children to read. */
private val Rounded = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/** Rubik Bubbles: bubbly letters for the name Знайко and big playful titles. */
val Bubbles = FontFamily(Font(R.font.rubik_bubbles, FontWeight.Normal))

/** Bright letter colours for the name, like toy blocks. */
val LogoColours = listOf(
    Color(0xFFE4572E), Color(0xFFFF9F1C), Color(0xFFFFC857), Color(0xFF7BD389), Color(0xFF3F88C5), Color(0xFF9B5DE5), Color(0xFFF15BB5),
)

private val TalktoTypography = Typography(
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp),
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
