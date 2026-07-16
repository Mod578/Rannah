package com.bal.reminders.format

import android.content.Context
import com.bal.reminders.R
import com.bal.reminders.data.DateDisplay
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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

    // ------------------------------------------------------------- التقويم

    private val DATE_WORDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", arabicLocale)

    /** «الأربعاء» for the date's actual weekday (calendar-independent). */
    fun weekdayName(date: LocalDate): String = dayName(date.dayOfWeek)

    /**
     * «١ صفر ١٤٤٨هـ» — Umm al-Qura Hijri date (the official KACST tables that
     * ship with java.time, not an arithmetic approximation), with a user
     * adjustment of up to ±2 days for local moon-sighting differences.
     * Null only if the date falls outside the supported Hijri table range.
     */
    fun hijriDate(date: LocalDate, adjustmentDays: Int = 0): String? =
        runCatching {
            val hijri = HijrahDate.from(date).plus(adjustmentDays.toLong(), ChronoUnit.DAYS)
            arabicDigits(DATE_WORDS.format(hijri)) + "هـ"
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

    /** «١٥ شعبان ١٤٤٨هـ» from raw announced Hijri components. */
    fun hijriDateText(year: Int, month: Int, day: Int): String =
        arabicDigits("$day ") + hijriMonthName(month) + arabicDigits(" $year") + "هـ"

    /**
     * The date header per the user's calendar preference: a primary line
     * (weekday + preferred calendar) and an optional secondary line (the other
     * calendar when both are shown). Falls back to Gregorian if the Hijri
     * table cannot represent the date.
     */
    fun dateLines(
        date: LocalDate,
        display: DateDisplay,
        hijriAdjustmentDays: Int = 0,
    ): Pair<String, String?> {
        val weekday = weekdayName(date)
        val gregorian = gregorianDate(date)
        val hijri = if (display == DateDisplay.GREGORIAN) {
            null
        } else {
            hijriDate(date, hijriAdjustmentDays)
        }
        return when {
            hijri == null -> "$weekday $gregorian" to null
            display == DateDisplay.HIJRI -> "$weekday $hijri" to null
            else -> "$weekday $hijri" to gregorian
        }
    }

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
            is Schedule.OnceHijri ->
                context.getString(
                    R.string.schedule_once_hijri,
                    hijriDateText(schedule.year, schedule.month, schedule.day),
                    timeText,
                )
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

    /** «سيتم تذكيرك بـ«بصمة الدوام» يوميًا، ٩:٠٠ صباحًا» — the parse preview. */
    fun interpretation(context: Context, title: String, schedule: Schedule): String =
        context.getString(R.string.parse_interpretation, title, scheduleSummary(context, schedule))

    /** «بعد ٢٥ دقيقة» / «قبل ساعتين» / a date for far things. */
    fun relative(context: Context, target: Instant, now: Instant = Instant.now()): String {
        val overdue = target.isBefore(now)
        val duration = Duration.between(now, target).abs()
        val minutes = duration.toMinutes()
        val body = when {
            minutes < 1 -> return context.getString(R.string.relative_now)
            minutes < 60 -> context.resources.getQuantityString(
                R.plurals.duration_minutes, minutes.toInt(), arabicDigits(minutes.toString()),
            )
            duration.toHours() < 24 -> {
                val hours = duration.toHours().toInt()
                val rem = (minutes % 60).toInt()
                val hourText = context.resources.getQuantityString(
                    R.plurals.duration_hours, hours, arabicDigits(hours.toString()),
                )
                if (rem >= 5) {
                    hourText + " و" + context.resources.getQuantityString(
                        R.plurals.duration_minutes, rem, arabicDigits(rem.toString()),
                    )
                } else {
                    hourText
                }
            }
            else -> {
                val days = duration.toDays().toInt()
                context.resources.getQuantityString(
                    R.plurals.duration_days, days, arabicDigits(days.toString()),
                )
            }
        }
        return context.getString(if (overdue) R.string.relative_ago else R.string.relative_in, body)
    }

    /** «الثلاثاء ١٥ يوليو، ٩:٠٠ صباحًا» for a full instant. */
    fun dateTime(context: Context, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = at.atZone(zone)
        return context.getString(
            R.string.date_time_at,
            date(context, zoned.toLocalDate()),
            time(context, zoned.toLocalTime()),
        )
    }
}
