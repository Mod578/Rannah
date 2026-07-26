package com.bal.reminders.format

import android.content.Context
import com.bal.reminders.R
import com.bal.reminders.domain.HijriDates
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Arabic-first formatting: Arabic-Indic numerals, natural dayparts
 * (فجرًا/صباحًا/ظهرًا/عصرًا/مساءً) and human date words (اليوم/غدًا).
 */
object BalFormats {

    val arabicLocale: Locale = Locale("ar")

    /** ٩:٠٠ — converts ASCII digits to Arabic-Indic. */
    fun arabicDigits(text: String): String =
        buildString(text.length) {
            text.forEach { ch -> append(if (ch in '0'..'9') '٠' + (ch - '0') else ch) }
        }

    // Bidi isolation marks. Latin names and dotted version numbers keep their own
    // direction and internal order when dropped into an otherwise RTL paragraph,
    // instead of being reordered by the surrounding Arabic.
    private const val LRI = '⁦' // left-to-right isolate
    private const val PDI = '⁩' // pop directional isolate

    /** Forces a Latin/mixed run left-to-right inside RTL text, e.g. «Jetpack Compose». */
    fun ltr(text: String): String = "$LRI$text$PDI"

    /** «٩:٠٠ صباحًا» */
    fun time(context: Context, time: LocalTime): String {
        val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
        val digits = arabicDigits("%d:%02d".format(hour12, time.minute))
        return "$digits ${context.getString(periodRes(time.hour))}"
    }

    private fun periodRes(hour24: Int): Int = when (hour24) {
        in 0..5 -> R.string.period_fajr
        in 6..11 -> R.string.period_morning
        12 -> R.string.period_noon
        in 13..16 -> R.string.period_afternoon
        else -> R.string.period_evening
    }

    /** «اليوم» / «غدًا» / «الثلاثاء ١٥ يوليو» (+ السنة إذا اختلفت). */
    fun date(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String {
        return when (date) {
            today -> context.getString(R.string.date_today)
            today.plusDays(1) -> context.getString(R.string.date_tomorrow)
            else -> {
                val pattern = if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy"
                arabicDigits(date.format(DateTimeFormatter.ofPattern(pattern, arabicLocale)))
            }
        }
    }

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, arabicLocale)

    /** «٩:٠٠ صباحًا» for an instant in [zone]. */
    fun time(context: Context, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        time(context, at.atZone(zone).toLocalTime())

    // ------------------------------------------------------------- التقويم

    private val DATE_WORDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", arabicLocale)

    private val DAY_MONTH: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM", arabicLocale)

    /** «الأربعاء» for the date's actual weekday (calendar-independent). */
    fun weekdayName(date: LocalDate): String = dayName(date.dayOfWeek)

    /** «السبت، ١٨ يوليو» — the stable home header (weekday + day + month, no year). */
    fun headerDate(date: LocalDate): String =
        weekdayName(date) + "، " + arabicDigits(date.format(DAY_MONTH))

    /**
     * The full Hijri date, rendered like the Gregorian one and just as concise:
     * «١٨ صفر ١٤٤٨ هـ». The Umm al-Qura calendar (java.time's official
     * "islamic-umalqura") is the reference; the day is shown in full rather than
     * hedged. Null outside the supported table range.
     */
    fun hijriFull(date: LocalDate): String? = runCatching {
        val hijri = HijrahDate.from(date)
        val day = arabicDigits(hijri.get(ChronoField.DAY_OF_MONTH).toString())
        val month = hijriMonthName(hijri.get(ChronoField.MONTH_OF_YEAR))
        val year = arabicDigits(hijri.get(ChronoField.YEAR).toString())
        "$day $month $year هـ"
    }.getOrNull()

    /** «١٥ يوليو ٢٠٢٦م» */
    fun gregorianDate(date: LocalDate): String =
        arabicDigits(date.format(DATE_WORDS)) + "م"

    /** The Hijri month names, index 0 = محرم. */
    val hijriMonthNames: List<String> = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    fun hijriMonthName(month: Int): String = hijriMonthNames[(month - 1).coerceIn(0, 11)]

    fun gregorianMonthName(month: Int): String =
        java.time.Month.of(month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, arabicLocale)

    /**
     * The date header: a primary line (weekday + Gregorian date, the sole
     * scheduling calendar) and the full Hijri date as a companion line.
     */
    fun dateLines(date: LocalDate): Pair<String, String?> =
        "${weekdayName(date)} ${gregorianDate(date)}" to hijriFull(date)

    fun dayNames(days: Set<DayOfWeek>): String {
        // Saturday-first ordering — the Arabic week.
        val ordered = listOf(
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ).filter { it in days }
        return ordered.joinToString(" و") { dayName(it) }
    }

    /**
     * Names the common week shapes instead of listing them out. «أيام العمل»
     * is how people say it, and it stays readable on a card; spelling out five
     * day names for a clock-in reminder is technically true and useless.
     */
    private fun dayNamesShort(context: Context, days: Set<DayOfWeek>): String = when (days) {
        WORKDAYS -> context.getString(R.string.schedule_days_workdays)
        WEEKEND -> context.getString(R.string.schedule_days_weekend)
        ALL_DAYS -> context.getString(R.string.schedule_days_all)
        else -> dayNames(days)
    }

    private val WORKDAYS = setOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    )
    private val WEEKEND = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    private val ALL_DAYS = DayOfWeek.entries.toSet()

    /**
     * The repeat badge on a list row: «كل يوم», «كل أحد», «أيام العمل», «كل شهر».
     * Null for a one-time reminder — that is the whole point of the badge. Two
     * or three named days are spelled out; past that the list stops being
     * readable at a glance and «أسبوعيًا» says the same thing.
     */
    fun repeatLabel(context: Context, schedule: Schedule): String? = when (schedule) {
        is Schedule.Once, is Schedule.OnceHijri -> null
        is Schedule.Daily -> context.getString(R.string.repeat_daily)
        is Schedule.Weekly -> when {
            schedule.days == ALL_DAYS -> context.getString(R.string.repeat_daily)
            schedule.days == WORKDAYS -> context.getString(R.string.schedule_days_workdays)
            schedule.days == WEEKEND -> context.getString(R.string.schedule_days_weekend)
            schedule.days.size <= 3 -> context.getString(R.string.repeat_weekly, dayNames(schedule.days))
            else -> context.getString(R.string.repeat_weekly_many)
        }
        is Schedule.Monthly, is Schedule.HijriMonthly -> context.getString(R.string.repeat_monthly)
        is Schedule.Yearly, is Schedule.HijriYearly -> context.getString(R.string.repeat_yearly)
    }

    /** «يوميًا، ٩:٠٠ صباحًا» — the one-line schedule description. */
    fun scheduleSummary(context: Context, schedule: Schedule, today: LocalDate = LocalDate.now()): String {
        val timeText = time(context, schedule.time)
        return when (schedule) {
            is Schedule.Once ->
                context.getString(R.string.schedule_once, date(context, schedule.date, today), timeText)
            is Schedule.Daily ->
                context.getString(R.string.schedule_daily, timeText)
            is Schedule.Weekly ->
                context.getString(R.string.schedule_weekly, dayNamesShort(context, schedule.days), timeText)
            is Schedule.Monthly ->
                if (schedule.dayOfMonth >= 29) {
                    context.getString(
                        R.string.schedule_monthly_clamped,
                        arabicDigits(schedule.dayOfMonth.toString()),
                        timeText,
                    )
                } else {
                    context.getString(
                        R.string.schedule_monthly,
                        arabicDigits(schedule.dayOfMonth.toString()),
                        timeText,
                    )
                }
            is Schedule.HijriMonthly ->
                context.getString(
                    if (schedule.dayOfMonth == 30) {
                        R.string.schedule_hijri_monthly_clamped
                    } else {
                        R.string.schedule_hijri_monthly
                    },
                    arabicDigits(schedule.dayOfMonth.toString()),
                    timeText,
                )
            is Schedule.OnceHijri -> {
                // Legacy Hijri one-time reminders keep firing, but their summary is
                // shown as the reliable Gregorian date (no authoritative Hijri day).
                val civil = HijriDates.toGregorian(schedule.year, schedule.month, schedule.day)
                val dateText = if (civil != null) {
                    date(context, civil, today)
                } else {
                    hijriMonthName(schedule.month) + arabicDigits(" ${schedule.year}") + " هـ"
                }
                context.getString(R.string.schedule_once, dateText, timeText)
            }
            is Schedule.Yearly ->
                context.getString(
                    R.string.schedule_yearly,
                    arabicDigits(schedule.day.toString()),
                    gregorianMonthName(schedule.month),
                    timeText,
                )
            is Schedule.HijriYearly ->
                context.getString(
                    R.string.schedule_hijri_yearly,
                    arabicDigits(schedule.day.toString()),
                    hijriMonthName(schedule.month),
                    timeText,
                )
        }
    }

    /**
     * A moment in the shortest words that are still exact — the one formatter
     * every surface uses for "when": the home rows, the closed rows, the details
     * status lines, the history and the widget.
     *
     * «اليوم، ٩:٠٠ صباحًا» · «غدًا، ٦:٠٠ صباحًا» · «الأحد، ٦:٠٠ صباحًا» ·
     * «١ أغسطس، ٨:٠٠ صباحًا» · «١ أغسطس ٢٠٢٧، ٨:٠٠ صباحًا»
     *
     * The weekday form is used only inside the coming week, where «الأحد» can
     * only mean one Sunday; a date in the past or further out is named outright,
     * so nothing ever reads as a relative day it is not. Everything is derived
     * from the instant that was actually scheduled — there is no fixed phrase
     * anywhere that assumes "tomorrow".
     */
    fun dateTime(
        context: Context,
        at: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): String {
        val zoned = at.atZone(zone)
        val date = zoned.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val dayText = when {
            date == today -> context.getString(R.string.date_today)
            date == today.plusDays(1) -> context.getString(R.string.date_tomorrow)
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> weekdayName(date)
            date.year == today.year -> arabicDigits(date.format(DAY_MONTH))
            else -> arabicDigits(date.format(DATE_WORDS))
        }
        return context.getString(R.string.date_time_at, dayText, time(context, zoned.toLocalTime()))
    }
}
