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
// «مدار الرنّة»: بنفسجي كهربائي للحضور والفعل، ومشمشي حيّ للحظة والتنبيه.
// المحايدات ليلكية لا رمادية؛ لذلك تظل الهوية حاضرة حتى حين لا يظهر لون
// العلامة. اللونان المشبعان محدودان بالأفعال والحالات المهمة كي يبقى التطبيق
// هادئًا خلال الاستخدام اليومي، لا لوحة نيون.

// نهار — ضباب ليلكي وورق أبيض بارد
val Haze = Color(0xFFF7F5FF)
val Paper = Color(0xFFFFFBFF)
val HazeHigh = Color(0xFFF0EDFA)
val HazeHighest = Color(0xFFE9E5F5)
val HazeVariant = Color(0xFFE7E2F2)
val MidnightInk = Color(0xFF201A32)
val MidnightInkSoft = Color(0xFF5C566B)
val Iris = Color(0xFF5B3FD0)
val IrisContainer = Color(0xFFE4DEFF)
val Persimmon = Color(0xFFB94332)
val PersimmonContainer = Color(0xFFFFDAD3)

// ليل — حبر بنفسجي عميق، لا أسود خالص
val Night = Color(0xFF13101F)
val NightCard = Color(0xFF1D192C)
val NightHigh = Color(0xFF262137)
val NightHighest = Color(0xFF302A43)
val NightVariant = Color(0xFF312B43)
val Moon = Color(0xFFF0ECFA)
val MoonSoft = Color(0xFFCBC3D8)
val IrisNight = Color(0xFFC8BFFF)
val IrisNightContainer = Color(0xFF422A9D)
val ApricotNight = Color(0xFFFFB4A6)
val ApricotNightContainer = Color(0xFF7D2A20)

val LightColors = lightColorScheme(
    primary = Iris,
    onPrimary = Color.White,
    primaryContainer = IrisContainer,
    onPrimaryContainer = Color(0xFF1B0061),
    secondary = Persimmon,
    onSecondary = Color.White,
    secondaryContainer = PersimmonContainer,
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF6A5900),
    onTertiary = Color.White,
    background = Haze,
    onBackground = MidnightInk,
    surface = Haze,
    onSurface = MidnightInk,
    surfaceVariant = HazeVariant,
    onSurfaceVariant = MidnightInkSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainer = Paper,
    surfaceContainerHigh = HazeHigh,
    surfaceContainerHighest = HazeHighest,
    outline = Color(0xFF797184),
    outlineVariant = Color(0xFFD0C8DB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFF7DDD8),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF322D40),
    inverseOnSurface = Color(0xFFF5EFFF),
)

val DarkColors = darkColorScheme(
    primary = IrisNight,
    onPrimary = Color(0xFF2A117F),
    primaryContainer = IrisNightContainer,
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = ApricotNight,
    onSecondary = Color(0xFF680007),
    secondaryContainer = ApricotNightContainer,
    onSecondaryContainer = Color(0xFFFFDAD3),
    tertiary = Color(0xFFE4C444),
    onTertiary = Color(0xFF382F00),
    background = Night,
    onBackground = Moon,
    surface = Night,
    onSurface = Moon,
    surfaceVariant = NightVariant,
    onSurfaceVariant = MoonSoft,
    surfaceContainerLowest = Color(0xFF0D0A16),
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    outline = Color(0xFF948BA0),
    outlineVariant = Color(0xFF474052),
    error = Color(0xFFF0B4AC),
    onError = Color(0xFF5E150E),
    errorContainer = Color(0xFF83271D),
    onErrorContainer = Color(0xFFF7DDD8),
    inverseSurface = Moon,
    inverseOnSurface = MidnightInk,
)

// ---------------------------------------------------------------- الشعار
// العلامة «مدار»: نواة موعد يحيط بها أثران غير متساويين، كنبضة وصلت للتو.
// لا جرس حرفي ولا موجات زخرفية؛ الصورة تقول الرنّة بالحركة والفراغ.

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
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
