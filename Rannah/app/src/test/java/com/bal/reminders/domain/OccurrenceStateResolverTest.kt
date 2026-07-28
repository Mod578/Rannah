package com.bal.reminders.domain

import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class OccurrenceStateResolverTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    // Wednesday 2026-07-15, 10:00.
    private val now: Instant = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone).toInstant()

    private fun zdt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun resolve(reminder: Reminder, isResolved: (Instant) -> Boolean = { false }) =
        OccurrenceStateResolver.resolve(reminder, now, zone, isResolved)

    private fun reminder(schedule: Schedule) = Reminder(id = 1, title = "x", schedule = schedule)

    @Test
    fun `a future one-time reminder is upcoming`() {
        val v = resolve(reminder(Schedule.Once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0))))
        assertEquals(ReminderPhase.UPCOMING, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)), v.displayAt)
    }

    @Test
    fun `a one-time reminder that passed earlier today needs confirmation`() {
        val v = resolve(reminder(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))))
        assertEquals(ReminderPhase.NEEDS_CONFIRMATION, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)), v.occurrenceAt)
    }

    @Test
    fun `a one-time reminder from an earlier day is overdue, with its real date`() {
        val v = resolve(reminder(Schedule.Once(LocalDate.of(2026, 7, 10), LocalTime.of(9, 0))))
        assertEquals(ReminderPhase.OVERDUE, v.phase)
        // The date it was actually due — not a bare time filed under «اليوم».
        assertEquals(zdt(LocalDate.of(2026, 7, 10), LocalTime.of(9, 0)), v.displayAt)
    }

    @Test
    fun `an overdue one-time reminder that was answered is not overdue`() {
        val due = zdt(LocalDate.of(2026, 7, 10), LocalTime.of(9, 0))
        val v = resolve(reminder(Schedule.Once(LocalDate.of(2026, 7, 10), LocalTime.of(9, 0)))) { it == due }
        assertEquals(ReminderPhase.UPCOMING, v.phase)
    }

    @Test
    fun `a completed one-time reminder is completed`() {
        val r = reminder(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)))
            .copy(completedAt = now)
        assertEquals(ReminderPhase.COMPLETED, resolve(r).phase)
    }

    @Test
    fun `a recurring occurrence that already fired today needs confirmation`() {
        // Daily 09:00; now is 10:00 and today's 09:00 was never resolved.
        val v = resolve(reminder(Schedule.Daily(LocalTime.of(9, 0))))
        assertEquals(ReminderPhase.NEEDS_CONFIRMATION, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)), v.occurrenceAt)
    }

    @Test
    fun `a recurring occurrence later today is upcoming today`() {
        val v = resolve(reminder(Schedule.Daily(LocalTime.of(21, 0))))
        assertEquals(ReminderPhase.UPCOMING, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), v.displayAt)
    }

    @Test
    fun `a snoozed reminder is snoozed until its snooze instant`() {
        val until = now.plusSeconds(600)
        val r = reminder(Schedule.Daily(LocalTime.of(9, 0))).copy(snoozedUntil = until)
        val v = resolve(r)
        assertEquals(ReminderPhase.SNOOZED, v.phase)
        assertEquals(until, v.displayAt)
    }

    @Test
    fun `a snoozed reminder keeps the identity of the occurrence it postponed`() {
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        val until = now.plusSeconds(600)
        val r = reminder(Schedule.Daily(LocalTime.of(9, 0)))
            .copy(snoozedUntil = until, snoozedOccurrenceAt = todayNine)
        val v = resolve(r)
        assertEquals(ReminderPhase.SNOOZED, v.phase)
        assertEquals(until, v.displayAt) // shown: when it comes back
        assertEquals(todayNine, v.occurrenceAt) // acted on: what rang
    }

    @Test
    fun `a disabled reminder is paused and shows what it would resume to`() {
        val r = reminder(Schedule.Daily(LocalTime.of(9, 0))).copy(enabled = false)
        val v = resolve(r)
        assertEquals(ReminderPhase.PAUSED, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), v.displayAt)
    }

    @Test
    fun `a recurring occurrence resolved today points at the next occurrence`() {
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        val v = resolve(reminder(Schedule.Daily(LocalTime.of(9, 0)))) { it == todayNine }
        assertEquals(ReminderPhase.UPCOMING, v.phase)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), v.displayAt)
    }

    @Test
    fun `a legacy ended recurring series is paused, not completed`() {
        val r = reminder(Schedule.Daily(LocalTime.of(9, 0))).copy(completedAt = now)
        assertEquals(ReminderPhase.PAUSED, resolve(r).phase)
    }
}
