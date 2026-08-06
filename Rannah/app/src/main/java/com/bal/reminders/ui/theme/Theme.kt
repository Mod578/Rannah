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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

// ================================================================== الألوان
//
// لوحة واحدة: «حبر على ورق». لون فعل واحد — أزرق لازوردي عميق — ومحايدات دافئة
// نهارًا وزرقاء ليلًا. لا لون علامة منفصل عن لون التطبيق: خلفية الأيقونة وشاشة
// البداية والزر الأساسي كلها اللون نفسه.
//
// اللون الأساسي أزرق لا أخضر ولا أحمر، عمدًا: الأخضر محجوز لـ«أُنجزت» والأحمر
// لـ«متأخرة»، فلو كانت الهوية بأحدهما لتنازع لون العلامة مع لون الحالة. وهذا
// بالضبط ما كان يحدث حين كان «أُنجزت» أحمر مائلًا للبرتقالي بجوار أحمر الخطأ.
//
// كل ثنائية نص/سطح في الملف مقيسة: النص الأساسي لا يقلّ عن 7:1 والثانوي عن
// 4.5:1 في المظهرين. الجدول الكامل في docs/BRAND.md.

// ---- نهار: ورق دافئ وحبر لازوردي
private val Paper = Color(0xFFF6F3EC)          // الأرضية
private val PaperRaised = Color(0xFFFFFDF8)    // البطاقة
private val PaperLow = Color(0xFFFBF8F1)
private val PaperHigh = Color(0xFFEFEBE1)
private val PaperHighest = Color(0xFFE8E3D7)
private val PaperVariant = Color(0xFFE4DFD3)
private val Ink = Color(0xFF14202B)            // النص الأساسي
private val InkSoft = Color(0xFF4A5763)        // النص الثانوي
private val Lapis = Color(0xFF0F4C7A)          // الفعل
private val LapisContainer = Color(0xFFCDE7F5)
private val OnLapisContainer = Color(0xFF00293C)
private val Slate = Color(0xFF4E6272)          // تأكيد هادئ، لا حالة
private val SlateContainer = Color(0xFFDCE5EC)
private val OnSlateContainer = Color(0xFF16242E)

// ---- ليل: حبر أزرق داكن، لا أسود خالص
private val Night = Color(0xFF0F1418)
private val NightRaised = Color(0xFF171D23)
private val NightLowest = Color(0xFF0A0E12)
private val NightHigh = Color(0xFF1E252C)
private val NightHighest = Color(0xFF262E36)
private val NightVariant = Color(0xFF29323A)
private val Chalk = Color(0xFFEDEAE3)
private val ChalkSoft = Color(0xFFB6BFC7)
private val LapisNight = Color(0xFF8ACBEF)
private val LapisNightContainer = Color(0xFF004C6A)
private val OnLapisNightContainer = Color(0xFFC3E8FB)
private val SlateNight = Color(0xFFA8BAC7)
private val SlateNightContainer = Color(0xFF35454F)
private val OnSlateNightContainer = Color(0xFFDCE5EC)

val LightColors = lightColorScheme(
    primary = Lapis,
    onPrimary = Color.White,
    primaryContainer = LapisContainer,
    onPrimaryContainer = OnLapisContainer,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = OnSlateContainer,
    tertiary = Color(0xFF7A5312),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E3C4),
    onTertiaryContainer = Color(0xFF2A1B00),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    outline = Color(0xFF77808A),
    outlineVariant = Color(0xFFC7C0B2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF7DEDA),
    onErrorContainer = Color(0xFF410E0A),
    inverseSurface = Color(0xFF2A3440),
    inverseOnSurface = Color(0xFFF1EEE7),
    inversePrimary = LapisNight,
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = LapisNight,
    onPrimary = Color(0xFF00344A),
    primaryContainer = LapisNightContainer,
    onPrimaryContainer = OnLapisNightContainer,
    secondary = SlateNight,
    onSecondary = Color(0xFF16242E),
    secondaryContainer = SlateNightContainer,
    onSecondaryContainer = OnSlateNightContainer,
    tertiary = Color(0xFFEFC372),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFF6E3C4),
    background = Night,
    onBackground = Chalk,
    surface = Night,
    onSurface = Chalk,
    surfaceVariant = NightVariant,
    onSurfaceVariant = ChalkSoft,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = Color(0xFF13191F),
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    outline = Color(0xFF8A939C),
    outlineVariant = Color(0xFF3C444C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Chalk,
    inverseOnSurface = Ink,
    inversePrimary = Lapis,
    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------- ألوان الحالة
/**
 * ألوان الحالة، منفصلة عن أدوار Material.
 *
 * كانت الحالات تستعير `secondary` و`tertiary` و`error`، فصار «أُنجزت» يرث لون
 * التأكيد أيًّا كان — وانتهى الأمر بأحمرين متجاورين يعنيان النقيضين. الحالة
 * الآن تملك أسماءها: ما دلالته «تمّ» أخضر دائمًا، وما دلالته «فات» أحمر دائمًا،
 * ولا يتغيّر أحدهما حين تتغيّر هوية المنتج.
 *
 * اللون هنا تأكيد لا رسالة: كل حالة في الواجهة مصحوبة بكلمتها وأيقونتها، فلا
 * شيء يُقرأ باللون وحده.
 */
@Immutable
data class BalStatusColors(
    /** أُنجزت. */
    val done: Color,
    /** تم تخطيها عمدًا: قرار محايد، لا نجاح ولا فشل. */
    val skipped: Color,
    /** مؤجّلة. */
    val snoozed: Color,
    /** متأخرة، أو مرّت دون إجابة. */
    val overdue: Color,
    /** متوقف مؤقتًا. */
    val paused: Color,
)

private val LightStatus = BalStatusColors(
    done = Color(0xFF125536),
    skipped = Slate,
    snoozed = Color(0xFF7A5312),
    overdue = Color(0xFFB3261E),
    paused = Color(0xFF5A6470),
)

private val DarkStatus = BalStatusColors(
    done = Color(0xFF6EDBA6),
    skipped = SlateNight,
    snoozed = Color(0xFFEFC372),
    overdue = Color(0xFFFFB4AB),
    paused = Color(0xFF9AA4AE),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** حامل أدوار «رَنّة» التي لا يعرفها Material، يُقرأ مثل `MaterialTheme`. */
object BalTheme {
    val status: BalStatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current
}

// ================================================================ المسافات

/**
 * سلّم واحد بخطوات أربع لكل التطبيق. كانت الشاشات تخترع فجواتها (٦ و١٠ و١٤
 * و١٨ و٢٦)، ولهذا كانت تُقرأ مُجمَّعة لا مُصمَّمة؛ كل شاشة تنفق الآن من هنا.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** الهامش الأفقي المشترك بين كل الشاشات. */
    val screen = 20.dp

    /** فسحة أسفل كل محتوى قابل للتمرير، فوق حواف النظام. */
    val scrollBottom = 32.dp
}

// ================================================================== الخط
// Tajawal (SIL OFL 1.1): وجه عربي هندسي إنساني، أوضح من محايد مؤسسي، بعيون
// مفتوحة وأرقام هندية مقروءة عند الأحجام الكبيرة لعين كبيرة السن. أربعة أوزان
// ثابتة تخدم سلّمًا من أربع درجات، والأثقل للاسم والتاريخ وحدهما.

val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),   // 400 — القراءة
    Font(R.font.tajawal_medium, FontWeight.Medium),    // 500 — التسميات
    Font(R.font.tajawal_bold, FontWeight.SemiBold),    // 700 — العناوين والصفوف
    Font(R.font.tajawal_extrabold, FontWeight.Bold),   // 800 — الاسم والتاريخ
)

// العربية تريد ارتفاع سطر أعلى من افتراضيات Material اللاتينية، ورَنّة تُقرأ
// بعيون كبيرة السن، فأحجام الجسم درجة فوق افتراضيات Material.
//
// كل نمط يصفّر التباعد الحرفي: افتراضيات Material تباعد اللاتينية بأجزاء من
// الـem، والعربية خطّ متصل، والتباعد يفكّ وصلاتها — وهو أشيع طريقة تبدو بها
// واجهة عربية مكسورة من غير سبب ظاهر.
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

// زوايا هادئة: الصفوف والبطاقات لا تُقرأ فقاعات. ليس كل شيء بحاجة إلى حبّة
// كبيرة، وضبط نصف القطر جزء من جعل التطبيق يبدو مقصودًا لا مزخرفًا.
val BalShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * ثيم التطبيق. الاتجاه مفروض من اليمين إلى اليسار: «رَنّة» تطبيق عربي مهما
 * كانت لغة النظام.
 */
@Composable
fun BalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = BalTypography,
            shapes = BalShapes,
            content = content,
        )
    }
}
