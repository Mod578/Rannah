package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.OccurrenceStateResolver
import com.bal.reminders.domain.SnoozeDefaultProvider
import com.bal.reminders.domain.SnoozeRequest
import com.bal.reminders.domain.SnoozeResult
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.DeletedReminder
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone)

    private val repository = FakeRepository()
    private val gateway = FakeAlarmGateway()
    private val notifications = FakeNotifications()
    private val clock = TestClock(zone, now.toInstant())
    private var defaultSnooze = Reminder.DEFAULT_SNOOZE_MINUTES
    private val scheduler = ReminderScheduler(
        repository, gateway, notifications, clock,
        HijriAdjustmentProvider { 0 },
        SnoozeDefaultProvider { defaultSnooze },
    )

    private fun daily(time: LocalTime = LocalTime.of(21, 0)) = Reminder(
        title = "بصمة الدوام",
        schedule = Schedule.Daily(time),
        createdAt = clock.instant(),
    )

    private fun once(date: LocalDate, time: LocalTime) = Reminder(
        title = "اجتماع",
        schedule = Schedule.Once(date, time),
        createdAt = clock.instant(),
    )

    private fun zdt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    /**
     * The occurrence a surface would act on — exactly what the resolver hands the
     * home and the details screens, so the tests answer the same occurrence the
     * app does rather than inventing one.
     */
    private suspend fun occurrenceOf(id: Long): Instant =
        OccurrenceStateResolver
            .resolve(repository.getById(id)!!, clock.instant(), zone) { occ ->
                repository.records.any { it.occurrenceAt == occ && it.status.resolvesOccurrence }
            }
            .occurrenceAt!!

    // ------------------------------------------------------------ scheduling

    @Test
    fun `save schedules the next occurrence and persists it`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val expected = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0))
        assertEquals(expected, gateway.scheduled[id])
        assertEquals(expected, repository.getById(id)!!.nextTriggerAt)
    }

    @Test
    fun `saving twice with the same id keeps a single alarm`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val edited = repository.getById(id)!!.copy(schedule = Schedule.Daily(LocalTime.of(6, 0)))
        scheduler.save(edited)
        assertEquals(1, gateway.scheduled.size)
        // 06:00 has passed today → tomorrow 06:00.
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(6, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a hijri monthly reminder schedules on the announced date`() = runTest {
        val id = scheduler.save(
            Reminder(
                title = "قسط",
                schedule = Schedule.HijriMonthly(15, LocalTime.of(9, 0)),
                createdAt = clock.instant(),
            ),
        )
        val fireDay = gateway.scheduled[id]!!.atZone(zone).toLocalDate()
        val announced = com.bal.reminders.domain.HijriDates.fromGregorian(fireDay, 0)!!
        assertEquals(15, announced.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
    }

    // ------------------------------------------------------------------ fire

    @Test
    fun `firing starts the alarm and chains the next occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.onAlarmFired(id, repository.getById(id)!!.nextTriggerAt!!)
        assertEquals(1, notifications.alarms.size)
        assertTrue(gateway.scheduled.containsKey(id))
    }

    @Test
    fun `duplicate alarm delivery is ignored after the first advances the schedule`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        scheduler.onAlarmFired(id, occurrence)
        scheduler.onAlarmFired(id, occurrence)

        assertEquals(1, notifications.alarms.size)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)),
            repository.getById(id)!!.nextTriggerAt,
        )
    }

    @Test
    fun `stale alarm delivery after an edit cannot fire the edited reminder early`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val staleOccurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.save(repository.getById(id)!!.copy(schedule = Schedule.Daily(LocalTime.of(6, 0))))

        scheduler.onAlarmFired(id, staleOccurrence)

        assertEquals(0, notifications.alarms.size)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(6, 0)),
            repository.getById(id)!!.nextTriggerAt,
        )
    }

    // ----------------------------------------------------------- completion

    @Test
    fun `completing a one-time reminder finishes and cancels it`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)))
        scheduler.complete(id, occurrenceOf(id))
        val reminder = repository.getById(id)!!
        assertTrue(reminder.isDone)
        assertNull(gateway.scheduled[id])
        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
        assertTrue(notifications.dismissed.contains(id))
    }

    @Test
    fun `completing a recurring reminder early skips today's occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.complete(id, occurrenceOf(id)) // at 10:00, before today's 21:00
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)),
            gateway.scheduled[id],
        )
        assertFalse(repository.getById(id)!!.isDone) // recurring stays active
        assertEquals(1, repository.records.size)
    }

    @Test
    fun `duplicate complete intents are idempotent`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        val first = scheduler.complete(id, occurrence)
        val second = scheduler.complete(id, occurrence)
        assertNotNull(first)
        assertNull(second) // replayed intent does nothing
        assertEquals(1, repository.records.size)
    }

    @Test
    fun `undo complete restores a one-time reminder and its alarm`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        val occurrence = scheduler.complete(id, occurrenceOf(id))!!
        assertTrue(repository.getById(id)!!.isDone)

        scheduler.undoComplete(id, occurrence)
        val reminder = repository.getById(id)!!
        assertFalse(reminder.isDone)
        assertEquals(0, repository.records.size)
        assertEquals(zdt(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)), gateway.scheduled[id])
    }

    @Test
    fun `undo complete works even after the reminder itself is gone`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val occurrence = scheduler.complete(id, occurrenceOf(id))!!
        repository.deleteWithRecords(id) // the record outlives nothing, but be sure
        repository.addRecord(
            OccurrenceRecord(
                reminderId = id,
                reminderTitle = "x",
                occurrenceAt = occurrence,
                status = OccurrenceStatus.COMPLETED,
                recordedAt = clock.instant(),
            ),
        )

        scheduler.undoComplete(id, occurrence)

        assertEquals(0, repository.records.size)
    }

    // ------------------------------------------------------------ skip today

    @Test
    fun `skipping today closes today and keeps every future day`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0))

        val skipped = scheduler.skipOccurrence(id, todayNine)

        assertEquals(todayNine, skipped)
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
        // The reminder itself is untouched: still active, still repeating.
        val reminder = repository.getById(id)!!
        assertTrue(reminder.enabled)
        assertFalse(reminder.isDone)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `skipping is idempotent and undo brings today back`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0))

        assertNotNull(scheduler.skipOccurrence(id, todayNine))
        assertNull(scheduler.skipOccurrence(id, todayNine)) // a replayed tap does nothing

        scheduler.undoSkip(id, todayNine)

        assertEquals(0, repository.records.size)
        assertEquals(todayNine, gateway.scheduled[id])
    }

    @Test
    fun `skipping stays available after the occurrence is due, and silences it`() = runTest {
        // Daily 09:00; now is 10:00, so today's ring already happened unanswered.
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        val skipped = scheduler.skipOccurrence(id, todayNine)

        assertEquals(todayNine, skipped)
        assertTrue(notifications.dismissed.contains(id)) // a live ringer is stopped
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a one-time reminder cannot be skipped`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        assertNull(scheduler.skipOccurrence(id, occurrence))
        assertEquals(0, repository.records.size)
        assertEquals(occurrence, gateway.scheduled[id])
    }

    @Test
    fun `skipping a snoozed occurrence clears the snooze`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(10))

        scheduler.skipOccurrence(id, todayNine)

        val reminder = repository.getById(id)!!
        assertNull(reminder.snoozedUntil)
        assertNull(reminder.snoozedOccurrenceAt)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    // ------------------------------------------------- completed-item cleanup

    @Test
    fun `completed one-time reminders are pruned once the day has passed`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0)))
        scheduler.complete(id, occurrenceOf(id))
        assertTrue(repository.getById(id)!!.isDone)

        // Same day: it is still present (shown under «أنجزته اليوم», undoable).
        scheduler.pruneFinished()
        assertNotNull(repository.getById(id))

        // Next day: the reminder and its records are gone; idempotent on re-run.
        clock.now = clock.now.plus(Duration.ofDays(1))
        scheduler.pruneFinished()
        assertNull(repository.getById(id))
        assertEquals(0, repository.records.size)
        scheduler.pruneFinished()
        assertNull(repository.getById(id))
    }

    @Test
    fun `prune never removes recurring or unresolved reminders`() = runTest {
        val recurring = scheduler.save(daily(LocalTime.of(21, 0)))
        val active = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        scheduler.complete(recurring, occurrenceOf(recurring)) // recurring stays active, not done

        clock.now = clock.now.plus(Duration.ofDays(3))
        scheduler.pruneFinished()

        assertNotNull(repository.getById(recurring))
        assertNotNull(repository.getById(active))
    }

    @Test
    fun `old occurrence records are pruned, recent and future ones are kept`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val old = clock.instant().minus(Duration.ofDays(200))
        repository.addRecord(
            OccurrenceRecord(
                reminderId = id,
                reminderTitle = "قديم",
                occurrenceAt = old,
                status = OccurrenceStatus.COMPLETED,
                recordedAt = old,
            ),
        )
        // Completing early: recorded now, for an occurrence still ahead.
        val future = scheduler.complete(id, occurrenceOf(id))!!

        scheduler.pruneFinished()

        assertEquals(1, repository.records.size)
        assertEquals(future, repository.records.single().occurrenceAt)
    }

    // ------------------------------------------------------ pause and resume

    @Test
    fun `resuming a paused reminder reschedules it exactly once`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.setEnabled(id, false)
        assertNull(gateway.scheduled[id])
        assertNull(repository.getById(id)!!.nextTriggerAt)

        scheduler.setEnabled(id, true)
        scheduler.setEnabled(id, true) // a second tap must not duplicate anything

        assertTrue(repository.getById(id)!!.enabled)
        assertEquals(1, gateway.scheduled.size)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `pausing drops a live snooze so resuming never restores a stale occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        scheduler.snooze(id, occurrenceOf(id), SnoozeRequest.Minutes(30))
        scheduler.setEnabled(id, false)

        val paused = repository.getById(id)!!
        assertNull(paused.snoozedUntil)
        assertNull(paused.snoozedOccurrenceAt)

        clock.now = clock.now.plus(Duration.ofDays(1)) // long past the snooze
        scheduler.setEnabled(id, true)
        assertEquals(zdt(LocalDate.of(2026, 7, 17), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    // ---------------------------------------------------------------- snooze

    @Test
    fun `snooze moves the alarm, and a second snooze while still postponed is refused`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = occurrenceOf(id)

        val first = scheduler.snooze(id, occurrence, SnoozeRequest.Minutes(10))

        assertTrue(first is SnoozeResult.Scheduled)
        assertEquals(clock.instant().plusSeconds(600), gateway.scheduled[id])

        // A replayed notification action, or a stale screen, must not stack a
        // second postponement on top of the live one.
        val again = scheduler.snooze(id, occurrence, SnoozeRequest.Minutes(30))

        assertEquals(SnoozeResult.Unavailable, again)
        assertEquals(clock.instant().plusSeconds(600), repository.getById(id)!!.snoozedUntil)
    }

    @Test
    fun `postponing again after it rings again moves it again, same occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(10))
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        scheduler.onAlarmFired(id, repository.getById(id)!!.nextTriggerAt!!)

        val second = scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(15))

        assertTrue(second is SnoozeResult.Scheduled)
        assertEquals(clock.instant().plusSeconds(900), repository.getById(id)!!.snoozedUntil)
        assertEquals(todayNine, repository.getById(id)!!.snoozedOccurrenceAt)
    }

    @Test
    fun `duplicate snooze notification intent is idempotent`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        scheduler.snooze(id, occurrence, SnoozeRequest.Minutes(10))
        val first = repository.getById(id)!!.snoozedUntil
        scheduler.snooze(id, occurrence, SnoozeRequest.Minutes(30))

        assertEquals(clock.instant().plusSeconds(600), first)
        assertEquals(first, repository.getById(id)!!.snoozedUntil)
        assertEquals(first, gateway.scheduled[id])
    }

    @Test
    fun `a snooze keeps the occurrence it postponed, so completing resolves that occurrence`() = runTest {
        // Daily 09:00; now is 10:00, so today's 09:00 has rung and is unresolved.
        val id = scheduler.save(daily(LocalTime.of(9, 0)))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)

        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(10))
        assertEquals(todayNine, repository.getById(id)!!.snoozedOccurrenceAt)

        // The alarm rings again and is postponed a second time: same occurrence.
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        scheduler.onAlarmFired(id, repository.getById(id)!!.nextTriggerAt!!)
        assertEquals(todayNine, notifications.alarms.last().second)
        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(10))
        assertEquals(todayNine, repository.getById(id)!!.snoozedOccurrenceAt)

        // «تم» resolves the occurrence that rang, not the postponement.
        val completed = scheduler.complete(id, occurrenceOf(id))
        assertEquals(todayNine, completed)
        assertEquals(todayNine, repository.records.single().occurrenceAt)
        assertNull(repository.getById(id)!!.snoozedUntil)
        assertNull(repository.getById(id)!!.snoozedOccurrenceAt)
    }

    @Test
    fun `firing a snoozed reminder clears the snooze and restores the base schedule`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0))) // 09:00 passed → tomorrow
        scheduler.snooze(id, occurrenceOf(id), SnoozeRequest.Minutes(10))
        scheduler.onAlarmFired(id, repository.getById(id)!!.nextTriggerAt!!)
        assertNull(repository.getById(id)!!.snoozedUntil)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)),
            gateway.scheduled[id],
        )
    }

    // ------------------------------------------------------------- lifecycle

    @Test
    fun `disabling cancels the alarm and enabling restores it`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.setEnabled(id, false)
        assertNull(gateway.scheduled[id])
        assertNull(repository.getById(id)!!.nextTriggerAt)
        scheduler.setEnabled(id, true)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `delete cancels the alarm and takes the reminder's records with it`() = runTest {
        val id = scheduler.save(daily())
        scheduler.complete(id, occurrenceOf(id))
        assertEquals(1, repository.records.size)

        val deleted = scheduler.delete(id)

        assertNotNull(deleted)
        assertNull(gateway.scheduled[id])
        assertTrue(notifications.dismissed.contains(id))
        assertNull(repository.getById(id))
        assertEquals(0, repository.records.size) // no orphan rows left behind
    }

    @Test
    fun `deleting twice is harmless and only the first delete can be undone`() = runTest {
        val id = scheduler.save(daily())
        assertNotNull(scheduler.delete(id))
        assertNull(scheduler.delete(id))
    }

    @Test
    fun `undo restores the deleted reminder, its records and its alarm`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.complete(id, occurrenceOf(id))
        val deleted = scheduler.delete(id)!!

        scheduler.restore(deleted)

        val restored = repository.getById(id)
        assertNotNull(restored)
        assertEquals("بصمة الدوام", restored!!.title)
        assertEquals(1, repository.records.size)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    // ----------------------------------------------------------------- boot

    @Test
    fun `reboot reschedules everything from the database`() = runTest {
        val id1 = scheduler.save(daily(LocalTime.of(21, 0)))
        val id2 = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        gateway.scheduled.clear() // simulate reboot wiping alarms
        scheduler.rescheduleAll()
        assertEquals(2, gateway.scheduled.size)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id1])
        assertEquals(zdt(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)), gateway.scheduled[id2])
    }

    @Test
    fun `a trigger missed within the grace window rings late once`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        // Simulate that the persisted trigger is 10 minutes in the past.
        repository.setNextTrigger(id, clock.instant().minusSeconds(600))
        gateway.scheduled.clear()
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(1, notifications.alarms.size)
        // And the future alarm is re-registered.
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a recurring trigger missed beyond the grace window is logged, not shown`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        repository.setNextTrigger(id, clock.instant().minusSeconds(3600 * 5))
        gateway.scheduled.clear()
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(0, notifications.alarms.size)
        assertEquals(OccurrenceStatus.MISSED, repository.records.single().status)
        assertTrue(gateway.scheduled.containsKey(id))

        // A second reconcile pass must not double-log the same miss.
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(1, repository.records.size)
    }

    @Test
    fun `a one-time reminder in the past gets no alarm but stays visible`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 10), LocalTime.of(9, 0)))
        assertNull(gateway.scheduled[id])
        val reminder = repository.getById(id)!!
        assertNull(reminder.nextTriggerAt)
        assertFalse(reminder.isDone)
    }

    @Test
    fun `a future snooze survives rescheduleAll`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.snooze(id, occurrenceOf(id), SnoozeRequest.Minutes(30))
        gateway.scheduled.clear()
        scheduler.rescheduleAll()
        assertEquals(clock.instant().plusSeconds(1800), gateway.scheduled[id])
    }
}
