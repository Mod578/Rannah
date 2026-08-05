package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.SnoozeDefaultProvider
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.ReminderKind
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three kinds a user actually chooses, «مرة واحدة», «يومي», «متكرر», each
 * followed all the way through the life it has: what completing does, what
 * skipping does, what pausing does, what deleting does, and what is left behind.
 *
 * «يومي» is a preset over the same recurrence engine, not a second code path,
 * and these tests are written to prove exactly that: it behaves like the other
 * recurring schedules everywhere except in what it is called.
 */
class ReminderLifecycleTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone)

    private val repository = FakeRepository()
    private val gateway = FakeAlarmGateway()
    private val notifications = FakeNotifications()
    private val clock = TestClock(zone, now.toInstant())
    private val scheduler = ReminderScheduler(
        repository, gateway, notifications, clock,
        HijriAdjustmentProvider { 0 },
        SnoozeDefaultProvider { 10 },
    )

    private fun zdt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private suspend fun save(schedule: Schedule, title: String = "تذكير"): Long =
        scheduler.save(Reminder(title = title, schedule = schedule, createdAt = clock.instant()))

    // ------------------------------------------------------------ مرة واحدة

    @Test
    fun `a one-time reminder is ONCE, finishes on completion, and is pruned the next day`() = runTest {
        val id = save(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(16, 0)))
        assertEquals(ReminderKind.ONCE, repository.getById(id)!!.kind)

        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        assertNotNull(scheduler.complete(id, occurrence))

        assertTrue(repository.getById(id)!!.isDone)
        assertNull("no alarm survives a finished one-timer", gateway.scheduled[id])

        // It stays for the rest of its day, undoable...
        scheduler.pruneFinished()
        assertNotNull(repository.getById(id))

        // ...and is gone, with its records, once the day turns.
        clock.now = clock.now.plus(Duration.ofDays(1))
        scheduler.pruneFinished()
        assertNull(repository.getById(id))
        assertTrue(repository.records.isEmpty())
    }

    @Test
    fun `a one-time reminder can never be skipped`() = runTest {
        val id = save(Schedule.Once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        assertNull(scheduler.skipOccurrence(id, occurrence))
        assertTrue(repository.records.isEmpty())
        assertEquals("the alarm is untouched", occurrence, gateway.scheduled[id])
    }

    @Test
    fun `an unanswered one-time reminder is kept, not pruned, however old`() = runTest {
        val id = save(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)))

        clock.now = clock.now.plus(Duration.ofDays(60))
        scheduler.pruneFinished()

        assertNotNull("an unresolved reminder is the user's to close, not ours", repository.getById(id))
        assertFalse(repository.getById(id)!!.isDone)
    }

    @Test
    fun `undo brings a completed one-time reminder and its alarm back`() = runTest {
        val id = save(Schedule.Once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        val occurrence = scheduler.complete(id, repository.getById(id)!!.nextTriggerAt!!)!!

        scheduler.undoComplete(id, occurrence)

        assertFalse(repository.getById(id)!!.isDone)
        assertTrue(repository.records.isEmpty())
        assertEquals(zdt(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)), gateway.scheduled[id])
    }

    // ----------------------------------------------------------------- يومي

    @Test
    fun `a daily reminder is DAILY and schedules tomorrow the moment today is done`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        assertEquals(ReminderKind.DAILY, repository.getById(id)!!.kind)

        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        assertNotNull(scheduler.complete(id, todayNine))

        val reminder = repository.getById(id)!!
        assertFalse("the series is untouched", reminder.isDone)
        assertTrue(reminder.enabled)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), reminder.nextTriggerAt)
    }

    @Test
    fun `skipping a daily reminder closes today only and keeps tomorrow`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        assertEquals(todayNine, scheduler.skipOccurrence(id, todayNine))

        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
        assertTrue(repository.getById(id)!!.enabled)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    @Test
    fun `pausing a daily reminder stops every ring, and resuming starts from the schedule`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(21, 0)))

        scheduler.setEnabled(id, false)
        assertNull(gateway.scheduled[id])
        assertNull(repository.getById(id)!!.nextTriggerAt)

        scheduler.setEnabled(id, true)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `deleting a daily reminder removes every future ring and its records`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(21, 0)))
        scheduler.complete(id, repository.getById(id)!!.nextTriggerAt!!)

        val deleted = scheduler.delete(id)

        assertNotNull(deleted)
        assertNull(repository.getById(id))
        assertNull(gateway.scheduled[id])
        assertTrue(repository.records.isEmpty())
    }

    @Test
    fun `seven weekdays is a daily reminder, not a weekly one`() = runTest {
        val id = save(Schedule.Weekly(DayOfWeek.entries.toSet(), LocalTime.of(9, 0)))

        assertEquals(ReminderKind.DAILY, repository.getById(id)!!.kind)
    }

    // ---------------------------------------------------------------- متكرر

    @Test
    fun `a weekly reminder is RECURRING and jumps to its next valid day`() = runTest {
        // Sundays and Tuesdays; today is Wednesday 15 July 2026.
        val id = save(Schedule.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY), LocalTime.of(20, 0)))
        assertEquals(ReminderKind.RECURRING, repository.getById(id)!!.kind)

        assertEquals(zdt(LocalDate.of(2026, 7, 19), LocalTime.of(20, 0)), gateway.scheduled[id])
    }

    @Test
    fun `completing one occurrence of a weekly reminder keeps the rest`() = runTest {
        val id = save(Schedule.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY), LocalTime.of(20, 0)))
        val sunday = zdt(LocalDate.of(2026, 7, 19), LocalTime.of(20, 0))

        scheduler.complete(id, sunday)

        assertFalse(repository.getById(id)!!.isDone)
        assertEquals(zdt(LocalDate.of(2026, 7, 21), LocalTime.of(20, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a monthly reminder clamps to the last day of a shorter month`() = runTest {
        val id = save(Schedule.Monthly(31, LocalTime.of(9, 0)))

        // July has 31 days, so the next ring is this month...
        assertEquals(zdt(LocalDate.of(2026, 7, 31), LocalTime.of(9, 0)), gateway.scheduled[id])

        // ...and the one after lands on the last day of September, not the 31st.
        clock.now = zdt(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0))
        scheduler.scheduleNext(id)
        assertEquals(zdt(LocalDate.of(2026, 9, 30), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a yearly reminder returns the following year`() = runTest {
        val id = save(Schedule.Yearly(8, 2, LocalTime.of(16, 0)))
        val thisYear = zdt(LocalDate.of(2026, 8, 2), LocalTime.of(16, 0))
        assertEquals(thisYear, gateway.scheduled[id])

        scheduler.complete(id, thisYear)

        assertEquals(zdt(LocalDate.of(2027, 8, 2), LocalTime.of(16, 0)), gateway.scheduled[id])
    }

    // ----------------------------------------- one answer per occurrence

    @Test
    fun `an occurrence that was completed cannot then be skipped`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        assertNotNull(scheduler.complete(id, todayNine))
        assertNull("the occurrence already has its one answer", scheduler.skipOccurrence(id, todayNine))

        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
    }

    @Test
    fun `an occurrence that was skipped cannot then be completed`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        assertNotNull(scheduler.skipOccurrence(id, todayNine))
        assertNull(scheduler.complete(id, todayNine))

        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
    }

    @Test
    fun `undoing an answer frees the occurrence for the other one`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        scheduler.complete(id, todayNine)
        scheduler.undoComplete(id, todayNine)

        assertNotNull("after an undo the occurrence is open again", scheduler.skipOccurrence(id, todayNine))
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
    }

    // ------------------------------------------- an unanswered occurrence

    @Test
    fun `an alarm that rings out is recorded as missed, not lost at midnight`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))

        scheduler.markMissed(id, todayNine)

        val record = repository.records.single()
        assertEquals(OccurrenceStatus.MISSED, record.status)
        assertEquals(todayNine, record.occurrenceAt)
        // It is a note, not an answer: the occurrence is still open.
        assertFalse(record.status.resolvesOccurrence)
    }

    @Test
    fun `a later answer supersedes the missed note for the same occurrence`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        scheduler.markMissed(id, todayNine)
        scheduler.complete(id, todayNine)

        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
    }

    @Test
    fun `an occurrence already answered is never recorded as missed`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.complete(id, todayNine)

        scheduler.markMissed(id, todayNine)

        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
    }

    @Test
    fun `a ring already answered elsewhere does not ring again on reschedule`() = runTest {
        val id = save(Schedule.Daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.complete(id, todayNine)
        repository.setNextTrigger(id, todayNine) // as if a stale trigger survived

        scheduler.rescheduleAll(fireMissed = true)

        assertTrue("an answered occurrence must not be re-rung", notifications.alarms.isEmpty())
    }
}
