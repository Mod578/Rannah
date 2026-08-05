package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.SnoozeDefaultProvider
import com.bal.reminders.domain.SnoozeLimits
import com.bal.reminders.domain.SnoozeRequest
import com.bal.reminders.domain.SnoozeResult
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «مدة التأجيل الافتراضية» is one setting, read when «تأجيل» is pressed.
 *
 * The model it replaced copied a number onto each reminder at creation and never
 * read the setting again, so a user who changed it watched every reminder they
 * already had go on postponing by the old duration, with nothing on screen
 * admitting why.
 */
class SnoozeModelTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone)

    private val repository = FakeRepository()
    private val gateway = FakeAlarmGateway()
    private val notifications = FakeNotifications()
    private val clock = TestClock(zone, now.toInstant())
    private var defaultMinutes = Reminder.DEFAULT_SNOOZE_MINUTES
    private val scheduler = ReminderScheduler(
        repository, gateway, notifications, clock,
        HijriAdjustmentProvider { 0 },
        SnoozeDefaultProvider { defaultMinutes },
    )

    private fun zdt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private suspend fun dailyAt(time: LocalTime): Pair<Long, Instant> {
        val id = scheduler.save(
            Reminder(title = "الدواء", schedule = Schedule.Daily(time), createdAt = clock.instant()),
        )
        return id to repository.getById(id)!!.nextTriggerAt!!
    }

    // ------------------------------------------------- the setting is global

    @Test
    fun `changing the default changes what an existing reminder does`() = runTest {
        val (id, occurrence) = dailyAt(LocalTime.of(21, 0))

        // Created while the default was 10 minutes...
        assertEquals(10, defaultMinutes)
        // ...and then the user changes the setting.
        defaultMinutes = 30

        scheduler.snooze(id, occurrence, SnoozeRequest.Default)

        assertEquals(clock.instant().plus(Duration.ofMinutes(30)), repository.getById(id)!!.snoozedUntil)
    }

    @Test
    fun `the default applies to a reminder created before the setting existed`() = runTest {
        val (id, occurrence) = dailyAt(LocalTime.of(21, 0))
        defaultMinutes = 5

        scheduler.snooze(id, occurrence, SnoozeRequest.Default)

        assertEquals(clock.instant().plus(Duration.ofMinutes(5)), gateway.scheduled[id])
    }

    // ----------------------------------------------------- temporary override

    @Test
    fun `an override moves this occurrence only and is never remembered`() = runTest {
        val (id, occurrence) = dailyAt(LocalTime.of(9, 0))
        repository.setNextTrigger(id, occurrence)

        scheduler.snooze(id, occurrence, SnoozeRequest.Minutes(60))
        assertEquals(clock.instant().plus(Duration.ofMinutes(60)), repository.getById(id)!!.snoozedUntil)

        // It rings again, and the plain button goes back to the global default.
        clock.now = clock.now.plus(Duration.ofMinutes(60))
        scheduler.onAlarmFired(id, repository.getById(id)!!.nextTriggerAt!!)
        scheduler.snooze(id, occurrence, SnoozeRequest.Default)

        assertEquals(clock.instant().plus(Duration.ofMinutes(10)), repository.getById(id)!!.snoozedUntil)
    }

    @Test
    fun `snooze until a specific time lands on exactly that instant`() = runTest {
        val (id, occurrence) = dailyAt(LocalTime.of(9, 0))
        val target = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(16, 30))

        val result = scheduler.snooze(id, occurrence, SnoozeRequest.Until(target))

        assertEquals(SnoozeResult.Scheduled(target, occurrence), result)
        assertEquals(target, gateway.scheduled[id])
    }

    // ---------------------------------------------------------- the safety cap

    @Test
    fun `the cap is the next natural occurrence, not just twelve hours`() = runTest {
        // Daily 09:00. Answering at 22:00, the next ring is 11 hours away, so the
        // 12-hour ceiling is not what binds, the reminder's own next occurrence is.
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        clock.now = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(22, 0))

        val limit = scheduler.snoozeLimit(id, todayNine)!!
        val tomorrowNine = zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0))

        assertEquals(tomorrowNine.minus(SnoozeLimits.SAFETY_MARGIN), limit)
        assertTrue("the cap must bite before the ceiling", limit.isBefore(clock.instant().plus(SnoozeLimits.MAXIMUM)))
    }

    @Test
    fun `an explicit choice past the cap is refused, not silently clamped`() = runTest {
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        clock.now = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(22, 0))

        // 10:00 tomorrow is past tomorrow's own 09:00 ring.
        val tooFar = zdt(LocalDate.of(2026, 7, 16), LocalTime.of(10, 0))
        val result = scheduler.snooze(id, todayNine, SnoozeRequest.Until(tooFar))

        assertTrue(result is SnoozeResult.TooLate)
        // Nothing was scheduled, and the reminder is untouched.
        assertNull(repository.getById(id)!!.snoozedUntil)
        assertEquals(todayNine, repository.getById(id)!!.nextTriggerAt)
    }

    @Test
    fun `a snooze can never swallow the next natural occurrence`() = runTest {
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        clock.now = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(23, 0))

        // The plain button is clamped rather than refused: it promised "10
        // minutes", and safety outranks the promise by at most a moment.
        scheduler.snooze(id, todayNine, SnoozeRequest.Default)

        val until = repository.getById(id)!!.snoozedUntil!!
        val tomorrowNine = zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0))
        assertTrue("postponement must land before the next ring", until.isBefore(tomorrowNine))
    }

    @Test
    fun `a one-time reminder is capped only by the twelve hour ceiling`() = runTest {
        val id = scheduler.save(
            Reminder(
                title = "اجتماع",
                schedule = Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(11, 0)),
                createdAt = clock.instant(),
            ),
        )
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        val limit = scheduler.snoozeLimit(id, occurrence)!!

        assertEquals(clock.instant().plus(SnoozeLimits.MAXIMUM), limit)
    }

    // ------------------------------------------------------ cancel and change

    @Test
    fun `cancelling a snooze returns the occurrence unresolved`() = runTest {
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(30))

        scheduler.cancelSnooze(id)

        val reminder = repository.getById(id)!!
        assertNull(reminder.snoozedUntil)
        assertNull(reminder.snoozedOccurrenceAt)
        // Neither completed nor skipped: nothing was answered.
        assertTrue(repository.records.none { it.status.resolvesOccurrence })
        assertTrue(reminder.enabled)
        // The alarm is re-derived from the schedule, not left on the snooze time.
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    @Test
    fun `completing after a snooze resolves the occurrence that rang`() = runTest {
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(15))

        assertEquals(todayNine, scheduler.complete(id, todayNine))
        assertEquals(todayNine, repository.records.single().occurrenceAt)
    }

    @Test
    fun `skipping after a snooze clears it and keeps tomorrow`() = runTest {
        val (id, _) = dailyAt(LocalTime.of(9, 0))
        val todayNine = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        repository.setNextTrigger(id, todayNine)
        scheduler.snooze(id, todayNine, SnoozeRequest.Minutes(15))

        assertNotNull(scheduler.skipOccurrence(id, todayNine))

        assertNull(repository.getById(id)!!.snoozedUntil)
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
        assertEquals(zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), gateway.scheduled[id])
    }

    @Test
    fun `a paused reminder cannot be postponed`() = runTest {
        val (id, occurrence) = dailyAt(LocalTime.of(21, 0))
        scheduler.setEnabled(id, false)

        assertEquals(SnoozeResult.Unavailable, scheduler.snooze(id, occurrence, SnoozeRequest.Default))
    }
}

/** A clock the test can move, so behaviour over time is observable. */
internal class TestClock(private val zone: ZoneId, var now: Instant) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(zone, now)
    override fun instant(): Instant = now
}
