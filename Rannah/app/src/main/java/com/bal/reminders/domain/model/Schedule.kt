package com.bal.reminders.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The three kinds of reminder رَنّة puts in front of the user, and the first
 * question the editor asks: «ما نوع التذكير؟».
 *
 * This is a *presentation* classification over the one scheduling engine, not a
 * second model. [DAILY] is [Schedule.Daily], a preset, not a separate code path
 * but it is named and chosen on its own because "every day" is what most
 * people actually want and burying it inside «متكرر» made them hunt for it.
 */
enum class ReminderKind {
    /** مرة واحدة: one Gregorian date and time, then it is finished. */
    ONCE,

    /** يومي: every day at the same time. */
    DAILY,

    /** متكرر: weekly days, monthly, or yearly. */
    RECURRING,
}

/**
 * The calendar system a date-bearing schedule is defined in. This is part of
 * the reminder's scheduling semantics, not a display preference: a Hijri
 * reminder recurs in Hijri months/years and is never silently converted to
 * Gregorian recurrence, and vice versa.
 */
enum class CalendarSystem(val id: String) {
    GREGORIAN("gregorian"),
    HIJRI("hijri");

    companion object {
        fun fromId(id: String?): CalendarSystem =
            entries.firstOrNull { it.id == id } ?: GREGORIAN
    }
}

/**
 * When a reminder should fire. All schedules use wall-clock semantics:
 * "9:00" means 9:00 in whatever timezone the device is currently in.
 *
 * Hijri schedules use the computed Umm al-Qura tables (java.time's
 * HijrahChronology) plus the user's ±2-day sighting adjustment, applied by
 * [com.bal.reminders.domain.RecurrenceCalculator] at scheduling time.
 */
sealed interface Schedule {
    val time: LocalTime

    /** Fires a single time on [date] at [time]. */
    data class Once(val date: LocalDate, override val time: LocalTime) : Schedule

    /**
     * Fires a single time on the announced Hijri date [year]-[month]-[day].
     * [day] 30 clamps to the month's last day in 29-day months.
     */
    data class OnceHijri(
        val year: Int,
        val month: Int,
        val day: Int,
        override val time: LocalTime,
    ) : Schedule

    /** Fires every day at [time]. */
    data class Daily(override val time: LocalTime) : Schedule

    /** Fires on each day in [days] at [time]. [days] must not be empty. */
    data class Weekly(val days: Set<DayOfWeek>, override val time: LocalTime) : Schedule

    /**
     * Fires monthly on [dayOfMonth] (1..31) at [time]. For months shorter than
     * [dayOfMonth] the occurrence is clamped to the last day of that month,
     * so "day 31" means "last day of the month" everywhere.
     */
    data class Monthly(val dayOfMonth: Int, override val time: LocalTime) : Schedule

    /** Fires on [dayOfMonth] of every Umm al-Qura Hijri month (clamped to month end). */
    data class HijriMonthly(val dayOfMonth: Int, override val time: LocalTime) : Schedule

    /**
     * Fires every Gregorian year on [month]/[day] at [time]. February 29
     * clamps to February 28 in common years.
     */
    data class Yearly(val month: Int, val day: Int, override val time: LocalTime) : Schedule

    /**
     * Fires every Hijri year on [month]/[day] at [time]. [day] 30 clamps to
     * the month's last day in 29-day months.
     */
    data class HijriYearly(val month: Int, val day: Int, override val time: LocalTime) : Schedule

    val isRecurring: Boolean get() = this !is Once && this !is OnceHijri

    /**
     * Which of the three user-facing kinds this schedule is. A [Weekly] covering
     * all seven days is «يومي»: the editor normalises that case to [Daily] when
     * saving, and this keeps a reminder written by an older build reading the
     * same way it would be written today.
     */
    val kind: ReminderKind
        get() = when (this) {
            is Once, is OnceHijri -> ReminderKind.ONCE
            is Daily -> ReminderKind.DAILY
            is Weekly -> if (days.size == DayOfWeek.entries.size) {
                ReminderKind.DAILY
            } else {
                ReminderKind.RECURRING
            }
            else -> ReminderKind.RECURRING
        }

    /** The calendar system the schedule's dates are defined in. */
    val calendar: CalendarSystem
        get() = when (this) {
            is OnceHijri, is HijriMonthly, is HijriYearly -> CalendarSystem.HIJRI
            else -> CalendarSystem.GREGORIAN
        }
}
