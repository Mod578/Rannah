package com.bal.reminders.ui.home

import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistGroupingTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    // Wednesday 2026-07-15, 10:00.
    private val now: Instant = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone).toInstant()

    private fun at(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun reminder(
        id: Long,
        schedule: Schedule,
        title: String = "تذكير $id",
        enabled: Boolean = true,
        nextTriggerAt: Instant? = null,
    ) = Reminder(
        id = id,
        title = title,
        schedule = schedule,
        enabled = enabled,
        nextTriggerAt = nextTriggerAt,
    )

    private fun record(
        reminderId: Long,
        occurrenceAt: Instant,
        status: OccurrenceStatus = OccurrenceStatus.COMPLETED,
        recordedAt: Instant = now,
    ) = OccurrenceRecord(
        id = occurrenceAt.toEpochMilli(),
        reminderId = reminderId,
        reminderTitle = "تذكير $reminderId",
        occurrenceAt = occurrenceAt,
        status = status,
        recordedAt = recordedAt,
    )

    private fun group(reminders: List<Reminder>, records: List<OccurrenceRecord> = emptyList()) =
        ChecklistGrouping.group(reminders, records, now, zone)

    // ------------------------------------------------ an occurrence still today

    @Test
    fun `a reminder completed today is not listed twice when its next ring is tomorrow`() {
        val daily = reminder(1, Schedule.Daily(LocalTime.of(9, 0)))
        val state = group(listOf(daily), listOf(record(1, at(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)))))

        assertTrue(state.today.isEmpty())
        assertTrue("tomorrow's ring is already described by the closed row", state.upcoming.isEmpty())
        assertEquals(1, state.closed.size)
        assertEquals(at(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), state.closed.single().returnsAt)
    }

    @Test
    fun `a reminder completed today is still listed when another occurrence rings today`() {
        // The defect this pins: complete the 09:00 daily, then edit it to 21:00.
        // The 21:00 occurrence is unresolved and its alarm is armed — the list
        // used to hide it and show only «مكتمل», so the app rang for something
        // it was telling the user was finished.
        val edited = reminder(1, Schedule.Daily(LocalTime.of(21, 0)))
        val morning = at(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))

        val state = group(listOf(edited), listOf(record(1, morning)))

        assertEquals(1, state.today.size)
        assertEquals(
            at(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)),
            state.today.single().displayAt,
        )
        assertEquals(ReminderPhase.UPCOMING, state.today.single().phase)
        // The earlier answer is still shown for what it was.
        assertEquals(1, state.closed.size)
    }

    @Test
    fun `a skipped occurrence does not hide a later occurrence on the same day`() {
        val edited = reminder(1, Schedule.Daily(LocalTime.of(20, 0)))
        val morning = at(LocalDate.of(2026, 7, 15), LocalTime.of(8, 0))

        val state = group(listOf(edited), listOf(record(1, morning, OccurrenceStatus.SKIPPED)))

        assertEquals(1, state.today.size)
        assertEquals(1, state.closed.size)
        assertFalse(state.closed.single().status == OccurrenceStatus.COMPLETED)
    }

    // --------------------------------------------------------------- overdue

    @Test
    fun `an unanswered one-time reminder from an earlier day is overdue, not today`() {
        val stale = reminder(1, Schedule.Once(LocalDate.of(2026, 6, 20), LocalTime.of(9, 0)))

        val state = group(listOf(stale))

        assertEquals(1, state.overdue.size)
        assertTrue(state.today.isEmpty())
        assertEquals(at(LocalDate.of(2026, 6, 20), LocalTime.of(9, 0)), state.overdue.single().displayAt)
    }

    @Test
    fun `a one-time reminder that passed earlier today stays in today`() {
        val thisMorning = reminder(1, Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(8, 0)))

        val state = group(listOf(thisMorning))

        assertTrue(state.overdue.isEmpty())
        assertEquals(1, state.today.size)
        assertEquals(ReminderPhase.NEEDS_CONFIRMATION, state.today.single().phase)
    }

    @Test
    fun `overdue reminders are ordered oldest first`() {
        val older = reminder(1, Schedule.Once(LocalDate.of(2026, 5, 1), LocalTime.of(9, 0)))
        val newer = reminder(2, Schedule.Once(LocalDate.of(2026, 7, 1), LocalTime.of(9, 0)))

        val state = group(listOf(newer, older))

        assertEquals(listOf(1L, 2L), state.overdue.map { it.reminderId })
    }

    @Test
    fun `an overdue reminder is never silently dropped`() {
        val ancient = reminder(1, Schedule.Once(LocalDate.of(2020, 1, 1), LocalTime.of(9, 0)))

        val state = group(listOf(ancient))

        assertEquals(1, state.overdue.size)
        assertTrue(state.hasAnyReminder)
    }

    // ----------------------------------------------------------- other buckets

    @Test
    fun `a paused reminder is reachable and never in the day`() {
        val paused = reminder(1, Schedule.Daily(LocalTime.of(9, 0)), enabled = false)

        val state = group(listOf(paused))

        assertEquals(1, state.paused.size)
        assertTrue(state.today.isEmpty())
        assertTrue(state.upcoming.isEmpty())
    }

    @Test
    fun `a future reminder is upcoming, not today`() {
        val later = reminder(1, Schedule.Once(LocalDate.of(2026, 8, 2), LocalTime.of(16, 0)))

        val state = group(listOf(later))

        assertEquals(1, state.upcoming.size)
        assertTrue(state.today.isEmpty())
    }

    @Test
    fun `a weekly reminder reports its real next occurrence, never a fixed phrase`() {
        // Saturdays only; today is Wednesday, so the next ring is in three days.
        val weekly = reminder(
            1,
            Schedule.Weekly(setOf(java.time.DayOfWeek.SATURDAY), LocalTime.of(9, 0)),
        )
        val lastSaturday = at(LocalDate.of(2026, 7, 11), LocalTime.of(9, 0))

        val state = group(listOf(weekly), listOf(record(1, lastSaturday)))

        assertEquals(
            at(LocalDate.of(2026, 7, 18), LocalTime.of(9, 0)),
            state.closed.single().returnsAt,
        )
    }

    @Test
    fun `nothingToday is false while something is overdue`() {
        val stale = reminder(1, Schedule.Once(LocalDate.of(2026, 6, 20), LocalTime.of(9, 0)))

        assertFalse(group(listOf(stale)).nothingToday)
    }
}
