package com.bal.reminders.domain

import com.bal.reminders.domain.model.Schedule
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/**
 * Pure scheduling math. Given a schedule and a point in time, computes the next
 * occurrence strictly after that point, or null when the schedule has no future
 * occurrences.
 *
 * [hijriAdjustmentDays] is the user's ±2-day sighting adjustment; it shifts
 * Hijri schedules the same way it shifts the displayed Hijri date, so what the
 * user reads and when the reminder fires always agree.
 *
 * Timezone gaps (DST spring-forward) are handled by [java.time]: a wall-clock
 * time that does not exist on a given day resolves to the shifted valid time.
 */
object RecurrenceCalculator {

    fun nextOccurrence(
        schedule: Schedule,
        after: ZonedDateTime,
        hijriAdjustmentDays: Int = 0,
    ): ZonedDateTime? {
        val zone = after.zone
        fun LocalDate.candidate() = atTime(schedule.time).atZone(zone)
        return when (schedule) {
            is Schedule.Once -> schedule.date.candidate().takeIf { it.isAfter(after) }

            is Schedule.OnceHijri -> {
                if (schedule.month !in 1..12 || schedule.day !in 1..30) return null
                HijriDates.toGregorian(
                    schedule.year, schedule.month, schedule.day, hijriAdjustmentDays,
                )?.candidate()?.takeIf { it.isAfter(after) }
            }

            is Schedule.Daily -> {
                val today = after.toLocalDate().candidate()
                if (today.isAfter(after)) {
                    today
                } else {
                    after.toLocalDate().plusDays(1).candidate()
                }
            }

            is Schedule.Weekly -> {
                if (schedule.days.isEmpty()) return null
                (0L..7L).firstNotNullOfOrNull { offset ->
                    val date = after.toLocalDate().plusDays(offset)
                    if (date.dayOfWeek !in schedule.days) return@firstNotNullOfOrNull null
                    date.candidate().takeIf { it.isAfter(after) }
                }
            }

            is Schedule.Monthly -> {
                if (schedule.dayOfMonth !in 1..31) return null
                var month = YearMonth.from(after)
                // Two iterations always suffice: if this month's occurrence has
                // passed, next month's cannot also have passed.
                repeat(2) {
                    val day = minOf(schedule.dayOfMonth, month.lengthOfMonth())
                    val candidate = month.atDay(day).candidate()
                    if (candidate.isAfter(after)) return candidate
                    month = month.plusMonths(1)
                }
                null
            }

            is Schedule.HijriMonthly -> {
                if (schedule.dayOfMonth !in 1..30) return null
                runCatching {
                    // Work in announced dates: today's announced month first.
                    var month = HijriDates.fromGregorian(after.toLocalDate(), hijriAdjustmentDays)
                        ?.with(ChronoField.DAY_OF_MONTH, 1) ?: return null
                    // Three iterations cover the adjustment straddling a month edge.
                    repeat(3) {
                        val lastDay = month.range(ChronoField.DAY_OF_MONTH).maximum.toInt()
                        val announced = month.with(
                            ChronoField.DAY_OF_MONTH,
                            minOf(schedule.dayOfMonth, lastDay).toLong(),
                        )
                        val civil = LocalDate.from(
                            announced.minus(hijriAdjustmentDays.toLong(), ChronoUnit.DAYS),
                        )
                        val candidate = civil.candidate()
                        if (candidate.isAfter(after)) return candidate
                        month = month.plus(1, ChronoUnit.MONTHS)
                    }
                    null
                }.getOrNull()
            }

            is Schedule.Yearly -> {
                if (schedule.month !in 1..12) return null
                if (runCatching { MonthDay.of(schedule.month, schedule.day) }.isFailure) return null
                var year = Year.from(after)
                repeat(2) {
                    val length = year.atMonth(schedule.month).lengthOfMonth()
                    val candidate = year.atMonth(schedule.month)
                        .atDay(minOf(schedule.day, length))
                        .candidate()
                    if (candidate.isAfter(after)) return candidate
                    year = year.plusYears(1)
                }
                null
            }

            is Schedule.HijriYearly -> {
                if (schedule.month !in 1..12 || schedule.day !in 1..30) return null
                runCatching {
                    var year = HijriDates.fromGregorian(after.toLocalDate(), hijriAdjustmentDays)
                        ?.get(ChronoField.YEAR) ?: return null
                    // Three iterations cover the adjustment straddling a year edge.
                    repeat(3) {
                        val civil = HijriDates.toGregorian(
                            year, schedule.month, schedule.day, hijriAdjustmentDays,
                        ) ?: return null
                        val candidate = civil.candidate()
                        if (candidate.isAfter(after)) return candidate
                        year += 1
                    }
                    null
                }.getOrNull()
            }
        }
    }
}
