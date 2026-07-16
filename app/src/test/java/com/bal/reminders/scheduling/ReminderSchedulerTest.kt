package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Clock
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

private class FakeRepository : ReminderRepository {
    private val store = MutableStateFlow<Map<Long, Reminder>>(emptyMap())
    val records = mutableListOf<OccurrenceRecord>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Reminder>> = store.map { it.values.toList() }
    override fun observeById(id: Long): Flow<Reminder?> = store.map { it[id] }
    override suspend fun getById(id: Long): Reminder? = store.value[id]
    override suspend fun getActive(): List<Reminder> =
        store.value.values.filter { it.enabled && !it.isDone }

    override suspend fun upsert(reminder: Reminder): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        store.value = store.value + (id to reminder.copy(id = id))
        return id
    }

    override suspend fun delete(id: Long) {
        store.value = store.value - id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = mutate(id) { it.copy(enabled = enabled) }
    override suspend fun setSnoozedUntil(id: Long, until: Instant?) = mutate(id) { it.copy(snoozedUntil = until) }
    override suspend fun setNextTrigger(id: Long, at: Instant?) = mutate(id) { it.copy(nextTriggerAt = at) }
    override suspend fun markCompleted(id: Long, at: Instant) =
        mutate(id) { it.copy(completedAt = at, snoozedUntil = null, nextTriggerAt = null) }

    override suspend fun clearCompleted(id: Long) = mutate(id) { it.copy(completedAt = null) }

    override fun observeRecords(): Flow<List<OccurrenceRecord>> = MutableStateFlow(records.toList())
    override fun observeRecordsFor(reminderId: Long): Flow<List<OccurrenceRecord>> =
        MutableStateFlow(records.filter { it.reminderId == reminderId })

    override suspend fun addRecord(record: OccurrenceRecord): Boolean {
        // Mirrors the DB's unique (reminderId, occurrenceAt, status) index.
        if (records.any {
                it.reminderId == record.reminderId &&
                    it.occurrenceAt == record.occurrenceAt &&
                    it.status == record.status
            }
        ) {
            return false
        }
        records += record.copy(id = (records.maxOfOrNull { it.id } ?: 0L) + 1)
        return true
    }

    override suspend fun removeRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus) {
        records.removeAll {
            it.reminderId == reminderId && it.occurrenceAt == occurrenceAt && it.status == status
        }
    }

    override suspend fun hasRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus): Boolean =
        records.any {
            it.reminderId == reminderId && it.occurrenceAt == occurrenceAt && it.status == status
        }

    override suspend fun clearRecords() = records.clear()

    private fun mutate(id: Long, transform: (Reminder) -> Reminder) {
        store.value[id]?.let { store.value = store.value + (id to transform(it)) }
    }
}

private class FakeAlarmGateway : AlarmGateway {
    val scheduled = mutableMapOf<Long, Instant>()
    val alarmClockFlags = mutableMapOf<Long, Boolean>()
    val reAlerts = mutableMapOf<Long, Pair<Instant, Instant>>() // id → (occurrence, at)
    var exactAllowed = true

    override fun schedule(reminderId: Long, at: Instant, alarmClock: Boolean) {
        scheduled[reminderId] = at
        alarmClockFlags[reminderId] = alarmClock
    }

    override fun scheduleReAlert(reminderId: Long, occurrenceAt: Instant, at: Instant) {
        reAlerts[reminderId] = occurrenceAt to at
    }

    override fun cancel(reminderId: Long) {
        scheduled.remove(reminderId)
        reAlerts.remove(reminderId)
    }

    override fun cancelReAlert(reminderId: Long) {
        reAlerts.remove(reminderId)
    }

    override fun canScheduleExact(): Boolean = exactAllowed
}

private class FakeNotifications : ReminderNotifications {
    val shown = mutableListOf<Pair<Long, Instant>>()
    val alarms = mutableListOf<Triple<Long, Instant, Int>>()
    val missed = mutableListOf<Pair<Long, Instant>>()
    val followUps = mutableListOf<Pair<Long, Instant>>()
    val undos = mutableListOf<Pair<Long, Instant>>()
    val dismissed = mutableListOf<Long>()

    override fun show(reminder: Reminder, occurrenceAt: Instant) {
        shown += reminder.id to occurrenceAt
    }

    override fun startAlarm(reminder: Reminder, occurrenceAt: Instant, attempt: Int) {
        alarms += Triple(reminder.id, occurrenceAt, attempt)
    }

    override fun showMissed(reminder: Reminder, occurrenceAt: Instant) {
        missed += reminder.id to occurrenceAt
    }

    override fun showStopFollowUp(reminder: Reminder, occurrenceAt: Instant) {
        followUps += reminder.id to occurrenceAt
    }

    override fun showCompletedUndo(reminder: Reminder, occurrenceAt: Instant) {
        undos += reminder.id to occurrenceAt
    }

    override fun dismiss(reminderId: Long) {
        dismissed += reminderId
    }
}

class ReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone)

    private val repository = FakeRepository()
    private val gateway = FakeAlarmGateway()
    private val notifications = FakeNotifications()
    private val clock: Clock = Clock.fixed(now.toInstant(), zone)
    private val scheduler = ReminderScheduler(
        repository, gateway, notifications, clock, HijriAdjustmentProvider { 0 },
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

    private fun alarmDaily(time: LocalTime = LocalTime.of(21, 0)) = Reminder(
        title = "الدواء",
        schedule = Schedule.Daily(time),
        alertMode = AlertMode.ALARM,
        createdAt = clock.instant(),
    )

    private fun zdt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    // ------------------------------------------------------------ scheduling

    @Test
    fun `save schedules the next occurrence and persists it`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val expected = zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0))
        assertEquals(expected, gateway.scheduled[id])
        assertEquals(expected, repository.getById(id)!!.nextTriggerAt)
        assertEquals(false, gateway.alarmClockFlags[id])
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
    fun `alarm mode reminders register as real alarm clocks`() = runTest {
        val id = scheduler.save(alarmDaily())
        assertEquals(true, gateway.alarmClockFlags[id])
    }

    @Test
    fun `a hijri monthly reminder schedules on the announced date`() = runTest {
        val adjusted = ReminderScheduler(
            repository, gateway, notifications, clock, HijriAdjustmentProvider { 1 },
        )
        val id = adjusted.save(
            Reminder(
                title = "قسط",
                schedule = Schedule.HijriMonthly(15, LocalTime.of(9, 0)),
                createdAt = clock.instant(),
            ),
        )
        val fireDay = gateway.scheduled[id]!!.atZone(zone).toLocalDate()
        val announced = com.bal.reminders.domain.HijriDates.fromGregorian(fireDay, 1)!!
        assertEquals(15, announced.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
    }

    // ------------------------------------------------------------------ fire

    @Test
    fun `standard fire shows a notification and chains the next occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.onAlarmFired(id)
        assertEquals(1, notifications.shown.size)
        assertEquals(0, notifications.alarms.size)
        assertTrue(gateway.scheduled.containsKey(id))
    }

    @Test
    fun `alarm mode fire starts the full alarm, not a notification`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)))
        scheduler.onAlarmFired(id)
        assertEquals(0, notifications.shown.size)
        assertEquals(1, notifications.alarms.size)
        assertEquals(1, notifications.alarms.first().third) // first attempt
    }

    @Test
    fun `duplicate alarm delivery is ignored after the first advances the schedule`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        scheduler.onAlarmFired(id, occurrence)
        scheduler.onAlarmFired(id, occurrence)

        assertEquals(1, notifications.shown.size)
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

        assertEquals(0, notifications.shown.size)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(6, 0)),
            repository.getById(id)!!.nextTriggerAt,
        )
    }

    // ----------------------------------------------------------- completion

    @Test
    fun `completing a one-time reminder finishes and cancels it`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)))
        scheduler.complete(id)
        val reminder = repository.getById(id)!!
        assertTrue(reminder.isDone)
        assertNull(gateway.scheduled[id])
        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
        assertEquals(listOf(id), notifications.dismissed)
    }

    @Test
    fun `completing a recurring reminder early skips today's occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.complete(id) // at 10:00, before today's 21:00
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
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)),
            gateway.scheduled[id],
        )
    }

    @Test
    fun `completion from a notification offers a brief undo`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.complete(id, fromNotification = true)
        assertEquals(1, notifications.undos.size)
    }

    @Test
    fun `undo complete restores a one-time reminder and its alarm`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        val occurrence = scheduler.complete(id)!!
        assertTrue(repository.getById(id)!!.isDone)

        scheduler.undoComplete(id, occurrence)
        val reminder = repository.getById(id)!!
        assertFalse(reminder.isDone)
        assertEquals(0, repository.records.size)
        assertEquals(zdt(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)), gateway.scheduled[id])
    }

    // ----------------------------------------------------------------- skip

    @Test
    fun `skipping one occurrence keeps the series and records it`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.skipOccurrence(id)
        val reminder = repository.getById(id)!!
        assertFalse(reminder.isDone)
        assertTrue(reminder.enabled)
        assertEquals(OccurrenceStatus.SKIPPED, repository.records.single().status)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(21, 0)),
            gateway.scheduled[id],
        )
    }

    @Test
    fun `skip is idempotent and never touches one-time reminders`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        assertNotNull(scheduler.skipOccurrence(id, occurrence))
        assertNull(scheduler.skipOccurrence(id, occurrence))
        assertEquals(1, repository.records.size)

        val onceId = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        assertNull(scheduler.skipOccurrence(onceId))
        assertEquals(1, repository.records.size)
    }

    @Test
    fun `undo skip restores the skipped occurrence`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = scheduler.skipOccurrence(id)!!
        scheduler.undoSkip(id, occurrence)
        assertEquals(0, repository.records.size)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)),
            gateway.scheduled[id],
        )
    }

    // ----------------------------------------------------------- end series

    @Test
    fun `ending a series stops future occurrences but keeps the reminder`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.endSeries(id)
        val reminder = repository.getById(id)!!
        assertTrue(reminder.isDone)
        assertNull(gateway.scheduled[id])
        assertNull(reminder.nextTriggerAt)
        // Ending a series is not completing an occurrence: no completion log.
        assertEquals(0, repository.records.size)
    }

    @Test
    fun `end series ignores one-time reminders`() = runTest {
        val id = scheduler.save(once(LocalDate.of(2026, 7, 20), LocalTime.of(8, 0)))
        scheduler.endSeries(id)
        assertFalse(repository.getById(id)!!.isDone)
    }

    @Test
    fun `reactivate reverses end series`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.endSeries(id)
        scheduler.reactivate(id)
        assertFalse(repository.getById(id)!!.isDone)
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    // ---------------------------------------------------------------- snooze

    @Test
    fun `snooze moves the alarm and repeated snoozes move it again`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        scheduler.snooze(id, 10)
        assertEquals(clock.instant().plusSeconds(600), gateway.scheduled[id])
        scheduler.snooze(id, 30)
        assertEquals(clock.instant().plusSeconds(1800), gateway.scheduled[id])
        assertEquals(clock.instant().plusSeconds(1800), repository.getById(id)!!.snoozedUntil)
    }

    @Test
    fun `duplicate snooze notification intent is idempotent`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!

        scheduler.snooze(id, minutes = 10, occurrenceAt = occurrence)
        val first = repository.getById(id)!!.snoozedUntil
        scheduler.snooze(id, minutes = 30, occurrenceAt = occurrence)

        assertEquals(clock.instant().plusSeconds(600), first)
        assertEquals(first, repository.getById(id)!!.snoozedUntil)
        assertEquals(first, gateway.scheduled[id])
    }

    @Test
    fun `firing a snoozed reminder clears the snooze and restores the base schedule`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(9, 0))) // 09:00 passed → tomorrow
        scheduler.snooze(id, 10)
        scheduler.onAlarmFired(id)
        assertNull(repository.getById(id)!!.snoozedUntil)
        assertEquals(
            zdt(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)),
            gateway.scheduled[id],
        )
    }

    @Test
    fun `snoozing an alarm-mode reminder keeps the alarm-clock flag`() = runTest {
        val id = scheduler.save(alarmDaily())
        scheduler.snooze(id, 10)
        assertEquals(true, gateway.alarmClockFlags[id])
    }

    // ------------------------------------------------- stop vs complete

    @Test
    fun `stopping an alarm is not completing the obligation`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)
        scheduler.stopAlarm(id, occurrence, askFollowUp = true)
        // No completion happened, and the quiet follow-up asks about it.
        assertTrue(repository.records.none { it.status == OccurrenceStatus.COMPLETED })
        assertEquals(1, notifications.followUps.size)
    }

    @Test
    fun `stopMarksCompleted makes stop complete explicitly`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)).copy(stopMarksCompleted = true))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)
        scheduler.stopAlarm(id, occurrence, askFollowUp = true)
        assertEquals(OccurrenceStatus.COMPLETED, repository.records.single().status)
        assertEquals(0, notifications.followUps.size)
    }

    @Test
    fun `stop after the occurrence was completed asks nothing`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.complete(id, occurrence)
        scheduler.stopAlarm(id, occurrence, askFollowUp = true)
        assertEquals(0, notifications.followUps.size)
    }

    // ----------------------------------------------------- timeout, re-alert

    @Test
    fun `alarm timeout records the occurrence as missed with a fallback notification`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)
        scheduler.onAlarmTimeout(id, occurrence, attempt = 1)
        assertEquals(OccurrenceStatus.MISSED, repository.records.single().status)
        assertEquals(1, notifications.missed.size)
    }

    @Test
    fun `repeatIfIgnored re-alerts once before giving up as missed`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)).copy(alarmRepeatIfIgnored = true))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)

        scheduler.onAlarmTimeout(id, occurrence, attempt = 1)
        assertEquals(0, notifications.missed.size)
        assertEquals(occurrence, gateway.reAlerts[id]!!.first)

        scheduler.onReAlertFired(id, occurrence)
        assertEquals(2, notifications.alarms.last().third)

        scheduler.onAlarmTimeout(id, occurrence, attempt = 2)
        assertEquals(1, notifications.missed.size)
        assertEquals(OccurrenceStatus.MISSED, repository.records.single().status)
    }

    @Test
    fun `a re-alert never fires for a resolved occurrence`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)).copy(alarmRepeatIfIgnored = true))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)
        scheduler.onAlarmTimeout(id, occurrence, attempt = 1)
        scheduler.complete(id, occurrence)
        notifications.alarms.clear()

        scheduler.onReAlertFired(id, occurrence)
        assertEquals(0, notifications.alarms.size)
    }

    @Test
    fun `completing cancels a pending re-alert`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)).copy(alarmRepeatIfIgnored = true))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onAlarmFired(id)
        scheduler.onAlarmTimeout(id, occurrence, attempt = 1)
        assertTrue(gateway.reAlerts.containsKey(id))
        scheduler.complete(id, occurrence)
        assertFalse(gateway.reAlerts.containsKey(id))
    }

    // ------------------------------------------------------------- lifecycle

    @Test
    fun `swiping a notification away logs a missed occurrence once`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        val occurrence = repository.getById(id)!!.nextTriggerAt!!
        scheduler.onNotificationDismissed(id, occurrence)
        scheduler.onNotificationDismissed(id, occurrence)
        assertEquals(1, repository.records.size)
        assertEquals(OccurrenceStatus.MISSED, repository.records.single().status)
    }

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
    fun `delete cancels alarm and dismisses notification`() = runTest {
        val id = scheduler.save(daily())
        scheduler.delete(id)
        assertNull(gateway.scheduled[id])
        assertTrue(notifications.dismissed.contains(id))
        assertNull(repository.getById(id))
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
    fun `a trigger missed within the grace window fires late once`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        // Simulate that the persisted trigger is 10 minutes in the past.
        repository.setNextTrigger(id, clock.instant().minusSeconds(600))
        gateway.scheduled.clear()
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(1, notifications.shown.size)
        // And the future alarm is re-registered.
        assertEquals(zdt(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), gateway.scheduled[id])
    }

    @Test
    fun `an alarm-mode trigger missed within grace rings for real after boot`() = runTest {
        val id = scheduler.save(alarmDaily(LocalTime.of(21, 0)))
        repository.setNextTrigger(id, clock.instant().minusSeconds(600))
        gateway.scheduled.clear()
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(1, notifications.alarms.size)
    }

    @Test
    fun `a recurring trigger missed beyond the grace window is logged, not shown`() = runTest {
        val id = scheduler.save(daily(LocalTime.of(21, 0)))
        repository.setNextTrigger(id, clock.instant().minusSeconds(3600 * 5))
        gateway.scheduled.clear()
        scheduler.rescheduleAll(fireMissed = true)
        assertEquals(0, notifications.shown.size)
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
        scheduler.snooze(id, 30)
        gateway.scheduled.clear()
        scheduler.rescheduleAll()
        assertEquals(clock.instant().plusSeconds(1800), gateway.scheduled[id])
    }
}
