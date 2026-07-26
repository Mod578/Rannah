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
// هوية «رَنّة»: جرس نحاسي على حجر. أخضر بحري عميق للفعل والثقة، ونحاس دافئ
// للجرس والرنّة، على أرضية حجرية نهارًا وفحمية دافئة ليلًا. الأخضر هنا ليس
// أخضر عامًّا بل «بترولي/بحري» عميق، والدفء يبقى في الحجر والنحاس.

// نهار
val Stone = Color(0xFFF4F1EA)        // أرضية حجرية دافئة
val StoneCard = Color(0xFFFCFBF6)    // بطاقة
val StoneHigh = Color(0xFFEDEAE0)
val StoneHighest = Color(0xFFE6E2D6)
val StoneVariant = Color(0xFFE3DFD3)
val Ink = Color(0xFF1B1D19)          // نص أساسي
val InkSoft = Color(0xFF52564D)      // نص ثانوي
val Teal = Color(0xFF0B6B5F)         // اللون الأساسي: أخضر بحري عميق
val TealContainer = Color(0xFFB7E7DD)
val Brass = Color(0xFF9A6B1E)        // النحاس: الرنّة، لمسات دافئة
val BrassContainer = Color(0xFFF5E4C4)

// ليل
val Coal = Color(0xFF1A1915)         // أرضية فحمية دافئة
val CoalCard = Color(0xFF232219)
val CoalHigh = Color(0xFF2C2A20)
val CoalHighest = Color(0xFF353327)
val CoalVariant = Color(0xFF322F27)
val Cream = Color(0xFFECE8DD)        // نص فاتح
val CreamSoft = Color(0xFFC7C3B6)
val TealNight = Color(0xFF5FCFBE)    // أخضر بحري مضيء
val TealNightContainer = Color(0xFF0C514A)
val BrassNight = Color(0xFFE1B667)   // نحاس مضيء
val BrassNightContainer = Color(0xFF57431A)

val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Brass,
    onSecondary = Color.White,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = Color(0xFF3A2A06),
    tertiary = Brass,
    onTertiary = Color.White,
    background = Stone,
    onBackground = Ink,
    surface = Stone,
    onSurface = Ink,
    surfaceVariant = StoneVariant,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainer = StoneCard,
    surfaceContainerHigh = StoneHigh,
    surfaceContainerHighest = StoneHighest,
    outline = Color(0xFF787B70),
    outlineVariant = Color(0xFFD4D0C3),
    error = Color(0xFFA5342A),
    onError = Color.White,
    errorContainer = Color(0xFFF7DDD8),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF303029),
    inverseOnSurface = Color(0xFFF2EFE7),
)

val DarkColors = darkColorScheme(
    primary = TealNight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = TealNightContainer,
    onPrimaryContainer = Color(0xFFAAEDE1),
    secondary = BrassNight,
    onSecondary = Color(0xFF3C2C05),
    secondaryContainer = BrassNightContainer,
    onSecondaryContainer = Color(0xFFFBE3B9),
    tertiary = BrassNight,
    onTertiary = Color(0xFF3C2C05),
    background = Coal,
    onBackground = Cream,
    surface = Coal,
    onSurface = Cream,
    surfaceVariant = CoalVariant,
    onSurfaceVariant = CreamSoft,
    surfaceContainerLowest = Color(0xFF121109),
    surfaceContainer = CoalCard,
    surfaceContainerHigh = CoalHigh,
    surfaceContainerHighest = CoalHighest,
    outline = Color(0xFF8B877A),
    outlineVariant = Color(0xFF3C3A30),
    error = Color(0xFFF0B4AC),
    onError = Color(0xFF5E150E),
    errorContainer = Color(0xFF83271D),
    onErrorContainer = Color(0xFFF7DDD8),
    inverseSurface = Stone,
    inverseOnSurface = Ink,
)

// ---------------------------------------------------------------- المسافات

/**
 * One spacing scale for the whole app, in steps of four. Screens used to invent
 * their own 6/10/14/18/26 gaps, which is why the layouts read as assembled
 * rather than designed; every screen now spends from this purse.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** The horizontal margin every screen shares. */
    val screen = 20.dp
}

// ---------------------------------------------------------------- الخط
// Tajawal (SIL OFL 1.1): a warm, geometric-humanist Arabic face, the strongest
// premium-feeling choice for a modern Saudi product — cleaner and more
// distinctive than a neutral corporate sans, with open counters and clear
// Arabic-Indic numerals that stay legible at large sizes for older eyes.
// Four static weights map to a restrained four-step hierarchy; the heaviest
// (ExtraBold) carries the wordmark and the date, the one place identity speaks.

val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),   // 400 — reading
    Font(R.font.tajawal_medium, FontWeight.Medium),    // 500 — labels
    Font(R.font.tajawal_bold, FontWeight.SemiBold),    // 700 — headings, rows
    Font(R.font.tajawal_extrabold, FontWeight.Bold),   // 800 — wordmark, date
)

// Arabic script wants taller line-heights than Material's Latin defaults, and
// رَنّة is read by older eyes, so body sizes sit a step above Material's. The
// scale is deliberately compact in step count: display / heading / title /
// body / label, no in-between sizes competing for the same job.
//
// Every style sets letterSpacing to zero. Material's defaults track Latin text
// apart by fractions of an em; Arabic is joined script, and tracking pulls the
// joins apart — the single most common way an Arabic UI ends up looking subtly
// broken. Titles and body are also separated by a full step now (18/16 against
// 17/15) so hierarchy comes from size, not only from weight.
private fun face(
    weight: FontWeight,
    size: Int,
    line: Int,
) = TextStyle(
    fontFamily = AppFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

val BalTypography = Typography(
    displayLarge = face(FontWeight.Bold, 44, 58),
    displaySmall = face(FontWeight.Bold, 34, 46),
    headlineLarge = face(FontWeight.Bold, 29, 40),
    headlineMedium = face(FontWeight.Bold, 26, 36),
    headlineSmall = face(FontWeight.SemiBold, 22, 32),
    titleLarge = face(FontWeight.SemiBold, 20, 30),
    titleMedium = face(FontWeight.SemiBold, 18, 27),
    titleSmall = face(FontWeight.SemiBold, 16, 23),
    bodyLarge = face(FontWeight.Normal, 17, 28),
    bodyMedium = face(FontWeight.Normal, 15, 24),
    bodySmall = face(FontWeight.Normal, 13, 20),
    labelLarge = face(FontWeight.Medium, 15, 20),
    labelMedium = face(FontWeight.Medium, 13, 17),
    labelSmall = face(FontWeight.Medium, 12, 16),
)

// Corners are calmer than before: rows and cards no longer read as large
// bubbles. Tightening the radius is part of making the app feel intentional
// rather than decorative — not everything needs to be a big pill.
val BalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
