package com.bal.reminders.domain

import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceCalculatorTest {

    private val zone = ZoneId.of("Asia/Riyadh")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    // ------------------------------------------------------------------ once

    @Test
    fun `once in the future returns its moment`() {
        val schedule = Schedule.Once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 20, 8), next)
    }

    @Test
    fun `once in the past returns null`() {
        val schedule = Schedule.Once(LocalDate.of(2026, 7, 10), LocalTime.of(8, 0))
        assertNull(RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)))
    }

    @Test
    fun `once exactly now returns null - strictly after`() {
        val schedule = Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0))
        assertNull(RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)))
    }

    // ----------------------------------------------------------------- daily

    @Test
    fun `daily before today's time fires today`() {
        val schedule = Schedule.Daily(LocalTime.of(21, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 15, 21), next)
    }

    @Test
    fun `daily after today's time fires tomorrow`() {
        val schedule = Schedule.Daily(LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 16, 9), next)
    }

    @Test
    fun `daily at midnight fires next midnight`() {
        val schedule = Schedule.Daily(LocalTime.MIDNIGHT)
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 0, 0))
        assertEquals(at(2026, 7, 16, 0, 0), next)
    }

    @Test
    fun `daily across a month boundary`() {
        val schedule = Schedule.Daily(LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 31, 12))
        assertEquals(at(2026, 8, 1, 9), next)
    }

    // ---------------------------------------------------------------- weekly

    @Test
    fun `weekly picks the nearest selected day`() {
        // 2026-07-15 is a Wednesday.
        val schedule = Schedule.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY), LocalTime.of(18, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(DayOfWeek.SUNDAY, next!!.dayOfWeek)
        assertEquals(at(2026, 7, 19, 18), next)
    }

    @Test
    fun `weekly on today fires today when time remains`() {
        val schedule = Schedule.Weekly(setOf(DayOfWeek.WEDNESDAY), LocalTime.of(18, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 15, 18), next)
    }

    @Test
    fun `weekly on today moves a week when time passed`() {
        val schedule = Schedule.Weekly(setOf(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 22, 9), next)
    }

    @Test
    fun `weekly with no days returns null`() {
        val schedule = Schedule.Weekly(emptySet(), LocalTime.of(9, 0))
        assertNull(RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)))
    }

    // --------------------------------------------------------------- monthly

    @Test
    fun `monthly later this month`() {
        val schedule = Schedule.Monthly(25, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 7, 25, 9), next)
    }

    @Test
    fun `monthly already passed moves to next month`() {
        val schedule = Schedule.Monthly(10, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 8, 10, 9), next)
    }

    @Test
    fun `monthly day 31 clamps to end of shorter months`() {
        val schedule = Schedule.Monthly(31, LocalTime.of(9, 0))
        // After August 31 has passed → September has 30 days.
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 8, 31, 10))
        assertEquals(at(2026, 9, 30, 9), next)
    }

    @Test
    fun `monthly day 30 clamps to February 28`() {
        val schedule = Schedule.Monthly(30, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 2, 1, 10))
        assertEquals(at(2026, 2, 28, 9), next)
    }

    @Test
    fun `monthly day 29 hits February 29 on leap years`() {
        val schedule = Schedule.Monthly(29, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2028, 2, 1, 10))
        assertEquals(at(2028, 2, 29, 9), next)
    }

    @Test
    fun `monthly on the clamped last day that already passed`() {
        val schedule = Schedule.Monthly(31, LocalTime.of(9, 0))
        // Feb 28 (clamped) at 09:00 has passed → March 31.
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 2, 28, 10))
        assertEquals(at(2026, 3, 31, 9), next)
    }

    @Test
    fun `monthly with invalid day returns null`() {
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                Schedule.Monthly(0, LocalTime.of(9, 0)),
                at(2026, 7, 15, 10),
            ),
        )
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                Schedule.Monthly(32, LocalTime.of(9, 0)),
                at(2026, 7, 15, 10),
            ),
        )
    }

    @Test
    fun `hijri monthly stays on the requested Hijri day`() {
        val next = RecurrenceCalculator.nextOccurrence(
            Schedule.HijriMonthly(15, LocalTime.of(9, 0)),
            at(2026, 7, 15, 10),
        )!!
        assertEquals(15, HijrahDate.from(next.toLocalDate()).get(ChronoField.DAY_OF_MONTH))
        assert(next.isAfter(at(2026, 7, 15, 10)))
    }

    @Test
    fun `hijri day 30 clamps in a 29 day month`() {
        val next = RecurrenceCalculator.nextOccurrence(
            Schedule.HijriMonthly(30, LocalTime.of(9, 0)),
            at(2026, 7, 15, 10),
        )!!
        val hijri = HijrahDate.from(next.toLocalDate())
        val last = hijri.range(ChronoField.DAY_OF_MONTH).maximum.toInt()
        assertEquals(last, hijri.get(ChronoField.DAY_OF_MONTH))
    }

    // ------------------------------------------------------------ once hijri

    @Test
    fun `once hijri resolves through the Umm al-Qura tables`() {
        // 15 Sha'ban 1448 = 2027-01-23 per the calculated tables.
        val schedule = Schedule.OnceHijri(1448, 8, 15, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(LocalDate.from(HijrahDate.of(1448, 8, 15)), next!!.toLocalDate())
        assertEquals(LocalTime.of(9, 0), next.toLocalTime())
    }

    @Test
    fun `once hijri in the past returns null`() {
        val schedule = Schedule.OnceHijri(1440, 1, 1, LocalTime.of(9, 0))
        assertNull(RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)))
    }

    @Test
    fun `once hijri outside the supported tables returns null`() {
        val schedule = Schedule.OnceHijri(2000, 1, 1, LocalTime.of(9, 0))
        assertNull(RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)))
    }

    @Test
    fun `once hijri day 30 clamps to the last day of a 29 day month`() {
        // Sha'ban 1448 has 29 days in the Umm al-Qura tables.
        val length = HijriDates.monthLength(1448, 8)!!
        val schedule = Schedule.OnceHijri(1448, 8, 30, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))!!
        assertEquals(
            LocalDate.from(HijrahDate.of(1448, 8, length)),
            next.toLocalDate(),
        )
    }

    // ------------------------------------------------------------- adjustment

    @Test
    fun `hijri adjustment shifts the civil firing day the same way as display`() {
        val schedule = Schedule.OnceHijri(1448, 8, 15, LocalTime.of(9, 0))
        val base = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))!!
        val plusOne = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10), 1)!!
        // Announced dates run ahead of the tables by +1 → the civil day is one earlier.
        assertEquals(base.toLocalDate().minusDays(1), plusOne.toLocalDate())
    }

    @Test
    fun `hijri monthly with adjustment keeps the announced day of month`() {
        val adjusted = RecurrenceCalculator.nextOccurrence(
            Schedule.HijriMonthly(15, LocalTime.of(9, 0)),
            at(2026, 7, 15, 10),
            2,
        )!!
        val announced = HijriDates.fromGregorian(adjusted.toLocalDate(), 2)!!
        assertEquals(15, announced.get(ChronoField.DAY_OF_MONTH))
    }

    // ---------------------------------------------------------------- yearly

    @Test
    fun `yearly later this year`() {
        val schedule = Schedule.Yearly(9, 1, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2026, 9, 1, 9), next)
    }

    @Test
    fun `yearly already passed moves to next year`() {
        val schedule = Schedule.Yearly(1, 23, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))
        assertEquals(at(2027, 1, 23, 9), next)
    }

    @Test
    fun `yearly February 29 clamps to 28 in common years and hits 29 in leap years`() {
        val schedule = Schedule.Yearly(2, 29, LocalTime.of(9, 0))
        assertEquals(
            at(2027, 2, 28, 9),
            RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10)),
        )
        assertEquals(
            at(2028, 2, 29, 9),
            RecurrenceCalculator.nextOccurrence(schedule, at(2027, 7, 15, 10)),
        )
    }

    @Test
    fun `yearly with an impossible date returns null`() {
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                Schedule.Yearly(2, 30, LocalTime.of(9, 0)),
                at(2026, 7, 15, 10),
            ),
        )
    }

    @Test
    fun `hijri yearly hits the same announced date every Hijri year`() {
        val schedule = Schedule.HijriYearly(9, 1, LocalTime.of(5, 0))
        val first = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))!!
        val hijri = HijrahDate.from(first.toLocalDate())
        assertEquals(9, hijri.get(ChronoField.MONTH_OF_YEAR))
        assertEquals(1, hijri.get(ChronoField.DAY_OF_MONTH))

        val second = RecurrenceCalculator.nextOccurrence(schedule, first.plusMinutes(1))!!
        assertEquals(
            hijri.get(ChronoField.YEAR) + 1,
            HijrahDate.from(second.toLocalDate()).get(ChronoField.YEAR),
        )
    }

    @Test
    fun `hijri yearly day 30 clamps in a 29 day month instead of skipping the year`() {
        val schedule = Schedule.HijriYearly(8, 30, LocalTime.of(9, 0))
        val next = RecurrenceCalculator.nextOccurrence(schedule, at(2026, 7, 15, 10))!!
        val hijri = HijrahDate.from(next.toLocalDate())
        assertEquals(8, hijri.get(ChronoField.MONTH_OF_YEAR))
        val last = hijri.range(ChronoField.DAY_OF_MONTH).maximum.toInt()
        assertEquals(last, hijri.get(ChronoField.DAY_OF_MONTH))
    }

    @Test
    fun `hijriDayClamps flags day 30 only for 29 day months`() {
        val around = LocalDate.from(HijrahDate.of(1448, 8, 1))
        val shabanLength = HijriDates.monthLength(1448, 8)!!
        assertEquals(shabanLength == 29, RecurrenceCalculator.hijriDayClamps(8, 30, around))
        assertEquals(false, RecurrenceCalculator.hijriDayClamps(8, 15, around))
    }

    // -------------------------------------------------------------- timezone

    @Test
    fun `wall clock time is kept across timezones`() {
        val riyadh = Schedule.Daily(LocalTime.of(9, 0))
        val inRiyadh = RecurrenceCalculator.nextOccurrence(
            riyadh,
            ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneId.of("Asia/Riyadh")),
        )
        val inCairo = RecurrenceCalculator.nextOccurrence(
            riyadh,
            ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneId.of("Africa/Cairo")),
        )
        assertEquals(LocalTime.of(9, 0), inRiyadh!!.toLocalTime())
        assertEquals(LocalTime.of(9, 0), inCairo!!.toLocalTime())
        assertEquals(ZoneId.of("Africa/Cairo"), inCairo.zone)
    }

    @Test
    fun `DST gap resolves to a valid time`() {
        // In America/New_York, 2026-03-08 02:30 does not exist (spring forward).
        val schedule = Schedule.Daily(LocalTime.of(2, 30))
        val next = RecurrenceCalculator.nextOccurrence(
            schedule,
            ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, ZoneId.of("America/New_York")),
        )!!
        assertEquals(LocalDate.of(2026, 3, 8), next.toLocalDate())
        // java.time shifts the gap forward by the DST offset.
        assertEquals(LocalTime.of(3, 30), next.toLocalTime())
    }
}
