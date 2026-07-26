package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.DeletedReminder
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of the reminder lifecycle: everything that fires, snoozes,
 * completes or reschedules goes through here. The database is the source of
 * truth; alarms are always derivable from it, which is what makes reboots,
 * process death and time changes safe.
 *
 * رَنّة has one alerting behaviour and two answers:
 * - تأجيل [snooze]: postpones this occurrence; repeatable. The occurrence keeps
 *   its identity across every postponement.
 * - تم [complete]: records this occurrence as done; never touches the series.
 *
 * Series-wide acts are separate and explicit: [setEnabled] pauses and resumes
 * («إيقاف مؤقت» / «استئناف»), and [delete] removes the reminder outright,
 * returning a snapshot [restore] can put back.
 *
 * Every action is idempotent: occurrence records are unique per
 * (reminder, occurrence, status), so replayed intents and double taps no-op.
 * An occurrence that rang and was neither postponed nor confirmed simply stays
 * unresolved — it surfaces on the checklist as «يحتاج تأكيدك», derived from the
 * schedule and the records, with no extra state to keep in step.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmGateway: AlarmGateway,
    private val notifications: ReminderNotifications,
    private val clock: Clock,
    private val hijriAdjustment: HijriAdjustmentProvider,
) {

    /** Computes and registers the next trigger for [reminderId]; cancels if none. */
    suspend fun scheduleNext(reminderId: Long) {
        val reminder = repository.getById(reminderId) ?: run {
            alarmGateway.cancel(reminderId)
            return
        }
        scheduleNext(reminder)
    }

    private suspend fun scheduleNext(reminder: Reminder, after: Instant? = null) {
        if (!reminder.enabled || reminder.isDone) {
            alarmGateway.cancel(reminder.id)
            repository.setNextTrigger(reminder.id, null)
            return
        }
        val now = ZonedDateTime.now(clock)
        val base = after?.takeIf { it.isAfter(now.toInstant()) }
            ?.atZone(now.zone) ?: now
        val snoozed = reminder.snoozedUntil?.takeIf { it.isAfter(now.toInstant()) }
        val next = snoozed
            ?: RecurrenceCalculator.nextOccurrence(
                reminder.schedule, base, hijriAdjustment.adjustmentDays(),
            )?.toInstant()
        if (next == null) {
            // A one-time reminder whose moment has passed: no alarm, it shows
            // as «يحتاج تأكيدك» in the app until completed or deleted.
            alarmGateway.cancel(reminder.id)
            repository.setNextTrigger(reminder.id, null)
        } else {
            repository.setNextTrigger(reminder.id, next)
            alarmGateway.schedule(reminder.id, next)
        }
    }

    /**
     * Called from [AlarmReceiver] when an alarm fires. [expectedOccurrence]
     * rejects a broadcast left over from an edit/cancel and makes duplicate
     * AlarmManager deliveries harmless.
     */
    suspend fun onAlarmFired(reminderId: Long, expectedOccurrence: Instant? = null) {
        val reminder = repository.getById(reminderId) ?: return
        if (!reminder.enabled || reminder.isDone) return
        val trigger = reminder.nextTriggerAt ?: return
        if (expectedOccurrence != null && trigger != expectedOccurrence) return
        // What rings is the trigger; what the user answers about is the
        // occurrence. After «تأجيل» those differ, and every surface downstream
        // (alarm screen, notification action, completion record) uses the latter.
        val occurrence = reminder.snoozedOccurrenceAt ?: trigger
        notifications.startAlarm(reminder, occurrence)
        if (reminder.snoozedUntil != null || reminder.snoozedOccurrenceAt != null) {
            repository.setSnooze(reminderId, null, null)
        }
        if (reminder.schedule.isRecurring) {
            // Advance strictly beyond the trigger that just fired. Using only
            // the wall clock could re-register the same occurrence when
            // AlarmManager delivers a few milliseconds early.
            scheduleNext(reminder.copy(snoozedUntil = null, snoozedOccurrenceAt = null), after = trigger)
        } else {
            repository.setNextTrigger(reminderId, null)
        }
    }

    /**
     * Completes one occurrence («تم»). Idempotent: a duplicate intent finds the
     * record already present and does nothing. Returns the occurrence instant
     * recorded, or null when nothing changed (already completed / not found).
     */
    suspend fun complete(reminderId: Long, occurrenceAt: Instant? = null): Instant? {
        val reminder = repository.getById(reminderId) ?: return null
        val now = clock.instant()
        val occurrence = occurrenceAt
            ?: reminder.snoozedOccurrenceAt
            ?: reminder.nextTriggerAt
            ?: now
        val inserted = repository.addRecord(record(reminder, occurrence, OccurrenceStatus.COMPLETED))
        if (!inserted) return null
        if (!reminder.schedule.isRecurring) {
            repository.markCompleted(reminderId, now)
            alarmGateway.cancel(reminderId)
        } else {
            repository.setSnooze(reminderId, null, null)
            // Completing early ("done already") skips the completed occurrence.
            scheduleNext(
                reminder.copy(snoozedUntil = null, snoozedOccurrenceAt = null),
                after = occurrence,
            )
        }
        notifications.dismiss(reminderId)
        return occurrence
    }

    /**
     * «تخطي اليوم»: closes today's occurrence without claiming the task was
     * done, and moves on to the next one. This is the answer to "not today, but
     * keep the rest" — it touches one occurrence, never the reminder, so it can
     * never be confused with pausing or deleting the series.
     *
     * Recurring only: a one-time reminder has no next occurrence to keep, so
     * skipping it would just be a deletion wearing another word.
     */
    suspend fun skipOccurrence(reminderId: Long, occurrenceAt: Instant): Instant? {
        val reminder = repository.getById(reminderId) ?: return null
        if (!reminder.schedule.isRecurring) return null
        val inserted = repository.addRecord(record(reminder, occurrenceAt, OccurrenceStatus.SKIPPED))
        if (!inserted) return null
        repository.setSnooze(reminderId, null, null)
        scheduleNext(
            reminder.copy(snoozedUntil = null, snoozedOccurrenceAt = null),
            after = occurrenceAt,
        )
        notifications.dismiss(reminderId)
        return occurrenceAt
    }

    /**
     * Reverses a «تم» («تراجع»). The record goes first, so an undo still cleans
     * up after a reminder that was deleted in between, and the reminder returns
     * to the state it had before the completion — alarm included.
     */
    suspend fun undoComplete(reminderId: Long, occurrenceAt: Instant) =
        undoResolution(reminderId, occurrenceAt, OccurrenceStatus.COMPLETED)

    /** Reverses a «تخطي اليوم»: the occurrence comes back exactly as it was. */
    suspend fun undoSkip(reminderId: Long, occurrenceAt: Instant) =
        undoResolution(reminderId, occurrenceAt, OccurrenceStatus.SKIPPED)

    private suspend fun undoResolution(
        reminderId: Long,
        occurrenceAt: Instant,
        status: OccurrenceStatus,
    ) {
        repository.removeRecord(reminderId, occurrenceAt, status)
        val reminder = repository.getById(reminderId) ?: return
        if (status == OccurrenceStatus.COMPLETED && !reminder.schedule.isRecurring) {
            repository.clearCompleted(reminderId)
        }
        scheduleNext(reminderId)
    }

    /**
     * The daily tidy-up: one-time reminders completed on a previous local day
     * (shown for their day under «انتهت اليوم», then gone), and occurrence
     * records older than [RECORD_RETENTION] that no longer answer anything.
     * Idempotent; safe on every launch, on the daily reconcile, and at midnight.
     */
    suspend fun pruneFinished() {
        val startOfToday = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
        repository.pruneCompletedOnceBefore(startOfToday)
        repository.pruneRecordsBefore(startOfToday.minus(RECORD_RETENTION))
    }

    /**
     * Postpones the current occurrence («تأجيل»); repeated snoozes after it rings
     * again move it again. [occurrenceAt] identifies notification actions so
     * replaying the same PendingIntent after the first snooze is a no-op.
     */
    suspend fun snooze(
        reminderId: Long,
        minutes: Int? = null,
        occurrenceAt: Instant? = null,
    ) {
        val reminder = repository.getById(reminderId) ?: return
        if (occurrenceAt != null && reminder.snoozedUntil != null) return
        val delay = (minutes ?: reminder.snoozeMinutes).coerceAtLeast(1)
        val until = clock.instant().plus(Duration.ofMinutes(delay.toLong()))
        // The occurrence being postponed, kept through repeated «تأجيل» taps.
        val occurrence = reminder.snoozedOccurrenceAt
            ?: occurrenceAt
            ?: reminder.nextTriggerAt
        repository.setSnooze(reminderId, until, occurrence)
        repository.setNextTrigger(reminderId, until)
        alarmGateway.schedule(reminderId, until)
        notifications.dismiss(reminderId)
    }

    /** Saves a reminder and (re)schedules it. Returns the id. */
    suspend fun save(reminder: Reminder): Long {
        val id = repository.upsert(reminder.copy(snoozedUntil = null, snoozedOccurrenceAt = null))
        scheduleNext(id)
        return id
    }

    /**
     * «حذف التذكير»: cancels the alarm, clears any live surface, and removes the
     * reminder with its records in one transaction. Returns what was removed so
     * the caller can offer «تراجع»; null when it was already gone, which makes a
     * repeated delete harmless.
     */
    suspend fun delete(reminderId: Long): DeletedReminder? {
        alarmGateway.cancel(reminderId)
        notifications.dismiss(reminderId)
        return repository.deleteWithRecords(reminderId)
    }

    /**
     * «تراجع» after a deletion: puts the reminder back exactly as it stood. The
     * alarm is restored from the trigger the reminder carried, not recomputed —
     * recomputing would re-arm an occurrence the user had already completed,
     * skipped or postponed past.
     */
    suspend fun restore(deleted: DeletedReminder) {
        repository.restore(deleted)
        val reminder = deleted.reminder
        val trigger = reminder.nextTriggerAt
        if (reminder.enabled && !reminder.isDone && trigger != null && trigger.isAfter(clock.instant())) {
            alarmGateway.schedule(reminder.id, trigger)
        } else {
            scheduleNext(reminder.id)
        }
    }

    /** «إيقاف مؤقت» / «استئناف». Idempotent: setting the state it already has re-derives the alarm. */
    suspend fun setEnabled(reminderId: Long, enabled: Boolean) {
        repository.setEnabled(reminderId, enabled)
        // A paused reminder holds no postponed occurrence: resuming must start
        // from the schedule, never from a snooze instant that has long passed.
        repository.setSnooze(reminderId, null, null)
        if (!enabled) notifications.dismiss(reminderId)
        scheduleNext(reminderId)
    }

    /**
     * Rebuilds every alarm from the database — after boot, app update, process
     * restart, and time or timezone changes. A trigger missed while the device
     * was off rings late within a grace window; beyond the window a recurring
     * occurrence is recorded as missed and the schedule moves forward.
     * Idempotent.
     */
    suspend fun rescheduleAll(fireMissed: Boolean = true) {
        val now = clock.instant()
        repository.getActive().forEach { reminder ->
            val missedTrigger = reminder.nextTriggerAt
            if (fireMissed && missedTrigger != null && !missedTrigger.isAfter(now)) {
                // The postponed occurrence, not the postponement, is what was missed.
                val occurrence = reminder.snoozedOccurrenceAt ?: missedTrigger
                val grace = if (reminder.schedule.isRecurring) RECURRING_GRACE else ONCE_GRACE
                if (Duration.between(missedTrigger, now) <= grace) {
                    notifications.startAlarm(reminder, occurrence)
                } else if (reminder.schedule.isRecurring && !isResolved(reminder.id, occurrence)) {
                    repository.addRecord(record(reminder, occurrence, OccurrenceStatus.MISSED))
                }
            }
            // Clear a snooze that expired while the device was off; keep future ones.
            val staleSnooze = reminder.snoozedUntil?.isAfter(now) == false
            if (staleSnooze) {
                repository.setSnooze(reminder.id, null, null)
            }
            scheduleNext(
                if (staleSnooze) {
                    reminder.copy(snoozedUntil = null, snoozedOccurrenceAt = null)
                } else {
                    reminder
                },
            )
        }
    }

    /** An occurrence is resolved once the user completed it. */
    private suspend fun isResolved(reminderId: Long, occurrenceAt: Instant): Boolean =
        repository.hasRecord(reminderId, occurrenceAt, OccurrenceStatus.COMPLETED)

    private fun record(reminder: Reminder, occurrenceAt: Instant, status: OccurrenceStatus) =
        OccurrenceRecord(
            reminderId = reminder.id,
            reminderTitle = reminder.title,
            occurrenceAt = occurrenceAt,
            status = status,
            recordedAt = clock.instant(),
        )

    private companion object {
        val RECURRING_GRACE: Duration = Duration.ofMinutes(30)
        val ONCE_GRACE: Duration = Duration.ofHours(24)

        /**
         * How long occurrence records are kept. Long enough for the details
         * history to be a real record of habit, short enough that the log cannot
         * grow without bound on a device that never reinstalls.
         */
        val RECORD_RETENTION: Duration = Duration.ofDays(180)
    }
}
