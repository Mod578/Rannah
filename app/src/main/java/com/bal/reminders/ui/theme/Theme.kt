package com.bal.reminders.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bal.reminders.R

// ---------------------------------------------------------------- الألوان
// هوية «ليل ونهار»: رمل دافئ، ليل عميق، زعفران.

val Sand = Color(0xFFFAF6EF)
val SandCard = Color(0xFFFFFDF8)
val SandVariant = Color(0xFFEFE7D8)
val Ink = Color(0xFF1F2430)
val InkSoft = Color(0xFF5A6072)
val Saffron = Color(0xFFC77E23)
val SaffronDeep = Color(0xFF9C6218)
val Teal = Color(0xFF2A6B6B)
val TealSoft = Color(0xFFDCEAE8)

val Night = Color(0xFF151A26)
val NightCard = Color(0xFF1E2433)
val NightVariant = Color(0xFF2A3145)
val Cream = Color(0xFFEFE9DD)
val CreamSoft = Color(0xFFB2AC9F)
val Amber = Color(0xFFE0A458)
val AmberSoft = Color(0xFF4A3A22)
val TealNight = Color(0xFF7FB5B5)

val LightColors = lightColorScheme(
    primary = Saffron,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3C8),
    onPrimaryContainer = Color(0xFF4A2F09),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF0F3535),
    background = Sand,
    onBackground = Ink,
    surface = Sand,
    onSurface = Ink,
    surfaceVariant = SandVariant,
    onSurfaceVariant = InkSoft,
    surfaceContainer = SandCard,
    surfaceContainerHigh = Color(0xFFF3EDE1),
    surfaceContainerHighest = Color(0xFFEDE5D5),
    outline = Color(0xFFC9C0AE),
    outlineVariant = Color(0xFFE2DAC8),
    error = Color(0xFFA43E2C),
    onError = Color.White,
    errorContainer = Color(0xFFF6DCD5),
    onErrorContainer = Color(0xFF541F14),
    tertiary = SaffronDeep,
    onTertiary = Color.White,
    inverseSurface = Night,
    inverseOnSurface = Cream,
)

val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1E0A),
    primaryContainer = AmberSoft,
    onPrimaryContainer = Color(0xFFF3D9B3),
    secondary = TealNight,
    onSecondary = Color(0xFF0E2A2A),
    secondaryContainer = Color(0xFF244646),
    onSecondaryContainer = Color(0xFFCBE4E2),
    background = Night,
    onBackground = Cream,
    surface = Night,
    onSurface = Cream,
    surfaceVariant = NightVariant,
    onSurfaceVariant = CreamSoft,
    surfaceContainer = NightCard,
    surfaceContainerHigh = Color(0xFF252C3D),
    surfaceContainerHighest = Color(0xFF2D3447),
    outline = Color(0xFF565E73),
    outlineVariant = Color(0xFF394052),
    error = Color(0xFFE58B77),
    onError = Color(0xFF3D120A),
    errorContainer = Color(0xFF5E2417),
    onErrorContainer = Color(0xFFF6DCD5),
    tertiary = Color(0xFFEBC088),
    onTertiary = Color(0xFF3C2A0E),
    inverseSurface = Sand,
    inverseOnSurface = Ink,
)

// ---------------------------------------------------------------- الخط

val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

// Arabic script needs taller line-heights than the Material defaults.
val BalTypography = Typography(
    displayLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 68.sp),
    displaySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 50.sp),
    headlineLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

val BalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * App theme. Layout direction is forced RTL: «رَنّة» is an Arabic app whatever
 * the system language is.
 */
@Composable
fun BalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = BalTypography,
            shapes = BalShapes,
            content = content,
        )
    }
}
