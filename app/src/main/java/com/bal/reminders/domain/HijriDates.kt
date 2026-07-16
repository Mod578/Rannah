package com.bal.reminders.domain

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/**
 * Pure Umm al-Qura Hijri conversions for scheduling. The user's ±2-day
 * sighting adjustment means "the announced Hijri date differs from the
 * computed table by this many days"; an announced date therefore maps to the
 * civil day whose computed Hijri date is (announced - adjustment).
 */
object HijriDates {

    /** Length in days (29 or 30) of the given computed Hijri month, or null outside the tables. */
    fun monthLength(year: Int, month: Int): Int? = runCatching {
        HijrahDate.of(year, month, 1).range(ChronoField.DAY_OF_MONTH).maximum.toInt()
    }.getOrNull()

    /**
     * The civil date of the announced Hijri [year]-[month]-[day] under
     * [adjustmentDays]. [day] is clamped to the month's length (so day 30 in a
     * 29-day month means its last day). Null outside the supported table range.
     */
    fun toGregorian(year: Int, month: Int, day: Int, adjustmentDays: Int = 0): LocalDate? =
        runCatching {
            val length = monthLength(year, month) ?: return null
            val hijri = HijrahDate.of(year, month, day.coerceIn(1, length))
            LocalDate.from(hijri.minus(adjustmentDays.toLong(), ChronoUnit.DAYS))
        }.getOrNull()

    /** The announced Hijri date of civil [date] under [adjustmentDays], or null outside the tables. */
    fun fromGregorian(date: LocalDate, adjustmentDays: Int = 0): HijrahDate? = runCatching {
        HijrahDate.from(date).plus(adjustmentDays.toLong(), ChronoUnit.DAYS)
    }.getOrNull()

    /** Today's announced Hijri year, used to seed pickers. */
    fun yearOf(date: LocalDate, adjustmentDays: Int = 0): Int? =
        fromGregorian(date, adjustmentDays)?.get(ChronoField.YEAR)

    /** The inclusive Hijri year range representable by the Umm al-Qura tables. */
    val supportedYears: IntRange = HijrahChronology.INSTANCE.range(ChronoField.YEAR).let {
        it.minimum.toInt()..it.maximum.toInt()
    }
}
