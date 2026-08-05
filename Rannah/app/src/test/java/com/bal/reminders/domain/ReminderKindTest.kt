package com.bal.reminders.domain

import com.bal.reminders.domain.model.ReminderKind
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three kinds the user chooses between, read back off the schedule.
 *
 * «يومي» is a preset over [Schedule.Daily], not a separate scheduling path, 
 * these tests pin that it is a *label* decision and that the recurrence engine
 * underneath is the same one every other repeating reminder uses.
 */
class ReminderKindTest {

    private val nine = LocalTime.of(9, 0)

    @Test
    fun `a dated schedule is مرة واحدة`() {
        assertEquals(ReminderKind.ONCE, Schedule.Once(LocalDate.of(2026, 8, 2), nine).kind)
    }

    @Test
    fun `a daily schedule is يومي`() {
        assertEquals(ReminderKind.DAILY, Schedule.Daily(nine).kind)
    }

    @Test
    fun `all seven weekdays is يومي, not أسبوعي`() {
        val everyDay = Schedule.Weekly(DayOfWeek.entries.toSet(), nine)
        assertEquals(ReminderKind.DAILY, everyDay.kind)
    }

    @Test
    fun `some weekdays is متكرر`() {
        val workdays = Schedule.Weekly(
            setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            nine,
        )
        assertEquals(ReminderKind.RECURRING, workdays.kind)
    }

    @Test
    fun `monthly and yearly are متكرر`() {
        assertEquals(ReminderKind.RECURRING, Schedule.Monthly(15, nine).kind)
        assertEquals(ReminderKind.RECURRING, Schedule.Yearly(8, 2, nine).kind)
    }

    @Test
    fun `legacy hijri schedules still classify, and still repeat`() {
        assertEquals(ReminderKind.ONCE, Schedule.OnceHijri(1448, 2, 18, nine).kind)
        assertEquals(ReminderKind.RECURRING, Schedule.HijriMonthly(13, nine).kind)
        assertEquals(ReminderKind.RECURRING, Schedule.HijriYearly(9, 1, nine).kind)
    }

    @Test
    fun `only مرة واحدة is non-recurring`() {
        assertFalse(Schedule.Once(LocalDate.of(2026, 8, 2), nine).isRecurring)
        assertFalse(Schedule.OnceHijri(1448, 2, 18, nine).isRecurring)
        assertTrue(Schedule.Daily(nine).isRecurring)
        assertTrue(Schedule.Weekly(setOf(DayOfWeek.FRIDAY), nine).isRecurring)
        assertTrue(Schedule.Monthly(1, nine).isRecurring)
        assertTrue(Schedule.Yearly(1, 1, nine).isRecurring)
    }

    @Test
    fun `every kind that repeats can be skipped, and مرة واحدة cannot`() {
        // «تخطي اليوم» is defined by having a tomorrow to keep, not by the label.
        assertTrue(Schedule.Daily(nine).isRecurring)
        assertFalse(Schedule.Once(LocalDate.of(2026, 8, 2), nine).isRecurring)
    }
}
