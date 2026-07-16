package com.bal.reminders.scheduling

import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.PendingConfirmation
import com.bal.reminders.domain.model.Reminder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of the reminder lifecycle: everything that fires, snoozes,
 * completes, skips or reschedules goes through here. The database is the
 * source of truth; alarms are always derivable from it, which is what makes
 * reboots, process death and time changes safe.
 *
 * Action semantics (labels are canonical across app, notifications and alarm):
 * - تم الإنجاز [complete]: records this occurrence as done; never touches the series.
 * - تأجيل [snooze]: moves this occurrence forward; repeatable.
 * - تخطي هذه المرة [skipOccurrence]: records this occurrence as skipped.
 * - إيقاف الصوت [stopAlarm]: silences an alarm. Never completion, ever.
 * - إنهاء التكرار [endSeries]: stops all future occurrences; series-wide.
 *
 * Every action is idempotent: occurrence records are unique per
 * (reminder, occurrence, status), so replayed intents and double taps no-op.
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
            // as overdue in the app until completed or deleted.
            alarmGateway.cancel(reminder.id)
            repository.setNextTrigger(reminder.id, null)
        } else {
            repository.setNextTrigger(reminder.id, next)
            alarmGateway.schedule(reminder.id, next, alarmClock = reminder.alertMode == AlertMode.ALARM)
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
        val occurrence = reminder.nextTriggerAt ?: return
        if (expectedOccurrence != null && occurrence != expectedOccurrence) return
        alert(reminder, occurrence, attempt = 1)
        if (reminder.snoozedUntil != null) {
            repository.setSnoozedUntil(reminderId, null)
        }
        if (reminder.schedule.isRecurring) {
            // Advance strictly beyond the occurrence that just fired. Using
            // only the wall clock could re-register the same occurrence when
            // AlarmManager delivers a few milliseconds early.
            scheduleNext(reminder.copy(snoozedUntil = null), after = occurrence)
        } else {
            repository.setNextTrigger(reminderId, null)
        }
    }

    /**
     * The single re-alert of an ignored alarm. Fires the full alarm again only
     * while the occurrence is still unresolved (not completed and not skipped).
     */
    suspend fun onReAlertFired(reminderId: Long, occurrenceAt: Instant) {
        val reminder = repository.getById(reminderId) ?: return
        if (!reminder.enabled || reminder.isDone) return
        if (isResolved(reminderId, occurrenceAt)) return
        alert(reminder, occurrenceAt, attempt = 2)
    }

    private fun alert(reminder: Reminder, occurrenceAt: Instant, attempt: Int) {
        if (reminder.alertMode == AlertMode.ALARM) {
            notifications.startAlarm(reminder, occurrenceAt, attempt)
        } else {
            notifications.show(reminder, occurrenceAt)
        }
    }

    /**
     * The alarm rang for its whole timeout with no response. Either re-alert
     * once (when the reminder asked for it) or record the occurrence as missed
     * and fall back to a high-priority notification.
     */
    suspend fun onAlarmTimeout(reminderId: Long, occurrenceAt: Instant, attempt: Int) {
        val reminder = repository.getById(reminderId) ?: return
        if (isResolved(reminderId, occurrenceAt)) return
        if (reminder.alarmRepeatIfIgnored && attempt < 2) {
            alarmGateway.scheduleReAlert(
                reminderId,
                occurrenceAt,
                clock.instant().plus(RE_ALERT_DELAY),
            )
            return
        }
        repository.addRecord(record(reminder, occurrenceAt, OccurrenceStatus.MISSED))
        notifications.showMissed(reminder, occurrenceAt)
    }

    /**
     * Completes one occurrence («تم»). Idempotent: a duplicate intent finds the
     * record already present and does nothing. Returns the occurrence instant
     * recorded, or null when nothing changed (already completed / not found).
     */
    suspend fun complete(
        reminderId: Long,
        occurrenceAt: Instant? = null,
        fromNotification: Boolean = false,
    ): Instant? {
        val reminder = repository.getById(reminderId) ?: return null
        val now = clock.instant()
        val occurrence = occurrenceAt ?: reminder.nextTriggerAt ?: now
        val inserted = repository.addRecord(record(reminder, occurrence, OccurrenceStatus.COMPLETED))
        if (!inserted) return null
        alarmGateway.cancelReAlert(reminderId)
        closeFollowUp(reminderId, occurrence)
        if (!reminder.schedule.isRecurring) {
            repository.markCompleted(reminderId, now)
            alarmGateway.cancel(reminderId)
        } else {
            repository.setSnoozedUntil(reminderId, null)
            // Completing early ("done already") skips the completed occurrence.
            scheduleNext(reminder.copy(snoozedUntil = null), after = occurrence)
        }
        notifications.dismiss(reminderId)
        if (fromNotification) {
            notifications.showCompletedUndo(reminder, occurrence)
        }
        return occurrence
    }

    /** Reverses an accidental «تم» (the undo affordance). */
    suspend fun undoComplete(reminderId: Long, occurrenceAt: Instant) {
        val reminder = repository.getById(reminderId) ?: return
        repository.removeRecord(reminderId, occurrenceAt, OccurrenceStatus.COMPLETED)
        if (!reminder.schedule.isRecurring) {
            repository.clearCompleted(reminderId)
        }
        scheduleNext(reminderId)
    }

    /**
     * Skips only the current occurrence of a recurring reminder
     * («تخطي هذه المرة»). The series is untouched. Idempotent. Returns the
     * occurrence instant recorded, or null when nothing changed.
     */
    suspend fun skipOccurrence(reminderId: Long, occurrenceAt: Instant? = null): Instant? {
        val reminder = repository.getById(reminderId) ?: return null
        if (!reminder.schedule.isRecurring) return null
        val occurrence = occurrenceAt ?: reminder.nextTriggerAt ?: clock.instant()
        val inserted = repository.addRecord(record(reminder, occurrence, OccurrenceStatus.SKIPPED))
        if (!inserted) return null
        alarmGateway.cancelReAlert(reminderId)
        closeFollowUp(reminderId, occurrence)
        repository.setSnoozedUntil(reminderId, null)
        scheduleNext(reminder.copy(snoozedUntil = null), after = occurrence)
        notifications.dismiss(reminderId)
        return occurrence
    }

    /** Reverses an accidental «تخطي هذه المرة». */
    suspend fun undoSkip(reminderId: Long, occurrenceAt: Instant) {
        repository.removeRecord(reminderId, occurrenceAt, OccurrenceStatus.SKIPPED)
        scheduleNext(reminderId)
    }

    /**
     * Ends a recurring series («إنهاء التكرار»): no future occurrences, the
     * reminder itself stays. Callers must confirm with the user first; this is
     * a series-wide action.
     */
    suspend fun endSeries(reminderId: Long) {
        val reminder = repository.getById(reminderId) ?: return
        if (!reminder.schedule.isRecurring) return
        repository.markCompleted(reminderId, clock.instant())
        alarmGateway.cancel(reminderId)
        notifications.dismiss(reminderId)
        clearFollowUps(reminderId)
    }

    /** Reverses [endSeries] (undo) and also revives a completed one-time reminder. */
    suspend fun reactivate(reminderId: Long) {
        repository.clearCompleted(reminderId)
        scheduleNext(reminderId)
    }

    /**
     * Silences a ringing alarm («إيقاف الصوت»). Stopping a sound stops a sound —
     * it never records that the obligation was met, whatever the reminder is
     * configured to do. What follows is decided by the reminder alone:
     *  - followUntilComplete: the occurrence moves to «بانتظار تأكيدك».
     *  - otherwise: a single quiet ask, and the occurrence stays unresolved.
     */
    suspend fun stopAlarm(
        reminderId: Long,
        occurrenceAt: Instant,
        askFollowUp: Boolean,
    ) {
        val reminder = repository.getById(reminderId) ?: return
        alarmGateway.cancelReAlert(reminderId)
        notifications.dismiss(reminderId)
        when {
            reminder.followUntilComplete -> openFollowUp(reminder, occurrenceAt)
            askFollowUp && !isResolved(reminderId, occurrenceAt) ->
                notifications.showStopFollowUp(reminder, occurrenceAt)
        }
    }

    /**
     * The user swiped a standard notification away. Pushing the alert away is
     * not doing the task either: a reminder that follows until completed starts
     * its follow-up here, and everything else records that the alert was seen
     * and ignored rather than pretending it never arrived.
     */
    suspend fun onNotificationDismissed(reminderId: Long, occurrenceAt: Instant) {
        val reminder = repository.getById(reminderId) ?: return
        if (isResolved(reminderId, occurrenceAt)) return
        if (reminder.followUntilComplete) {
            openFollowUp(reminder, occurrenceAt)
        } else {
            repository.addRecord(record(reminder, occurrenceAt, OccurrenceStatus.IGNORED))
        }
    }

    // -------------------------------------------------- المتابعة حتى الإنجاز

    /**
     * Moves an occurrence into «بانتظار تأكيدك» and asks the first time.
     * Idempotent: the unique (reminder, occurrence) row means a replayed stop
     * intent joins the existing follow-up instead of starting a second one.
     *
     * The deadline is computed once, from the reminder as it is now, so a later
     * settings change cannot stretch a follow-up that is already running.
     */
    private suspend fun openFollowUp(reminder: Reminder, occurrenceAt: Instant) {
        if (!reminder.followUntilComplete) return
        if (isResolved(reminder.id, occurrenceAt)) return
        val now = clock.instant()
        val opened = repository.addPending(
            PendingConfirmation(
                reminderId = reminder.id,
                occurrenceAt = occurrenceAt,
                since = now,
                nudgesSent = 0,
                deadlineAt = now.plus(Duration.ofMinutes(reminder.followUpWindowMinutes.toLong())),
            ),
        )
        if (!opened) return
        askFollowUp(reminder, occurrenceAt, nudgesSent = 0, now = now)
    }

    /**
     * A follow-up nudge came due. Asks again while the reminder's own budget
     * allows it, and otherwise stops for good: an unanswered follow-up is
     * recorded as missed, never as complete.
     */
    suspend fun onFollowUpDue(reminderId: Long, occurrenceAt: Instant) {
        val reminder = repository.getById(reminderId) ?: return
        val pending = repository.getPending(reminderId, occurrenceAt) ?: return
        if (isResolved(reminderId, occurrenceAt) || !reminder.followUntilComplete) {
            closeFollowUp(reminderId, occurrenceAt)
            return
        }
        val now = clock.instant()
        val budgetSpent = pending.nudgesSent >= reminder.followUpMaxRepeats
        if (budgetSpent || !now.isBefore(pending.deadlineAt)) {
            giveUpFollowUp(reminder, occurrenceAt)
            return
        }
        askFollowUp(reminder, occurrenceAt, nudgesSent = pending.nudgesSent, now = now)
    }

    private suspend fun askFollowUp(
        reminder: Reminder,
        occurrenceAt: Instant,
        nudgesSent: Int,
        now: Instant,
    ) {
        val sent = nudgesSent + 1
        repository.setPendingNudges(reminder.id, occurrenceAt, sent)
        notifications.showFollowUp(
            reminder,
            occurrenceAt,
            nudge = nudgesSent,
            remaining = (reminder.followUpMaxRepeats - sent).coerceAtLeast(0),
        )
        alarmGateway.scheduleFollowUp(
            reminder.id,
            occurrenceAt,
            now.plus(Duration.ofMinutes(reminder.followUpIntervalMinutes.toLong())),
        )
    }

    /** The follow-up ran out of asks: record the truth and stop bothering the user. */
    private suspend fun giveUpFollowUp(reminder: Reminder, occurrenceAt: Instant) {
        closeFollowUp(reminder.id, occurrenceAt)
        if (repository.addRecord(record(reminder, occurrenceAt, OccurrenceStatus.MISSED))) {
            notifications.showMissed(reminder, occurrenceAt)
        }
    }

    /**
     * «ذكّرني بعد ٥ دقائق» from the follow-up. This is an answer, not silence,
     * so it does not burn the ask budget: the follow-up restarts, bounded as
     * before, from the moment the user asked to be reminded. The occurrence
     * stays unresolved throughout, and the schedule is untouched, because the
     * task itself has not moved.
     */
    suspend fun snoozeFollowUp(reminderId: Long, occurrenceAt: Instant, minutes: Int? = null) {
        val reminder = repository.getById(reminderId) ?: return
        repository.getPending(reminderId, occurrenceAt) ?: return
        if (isResolved(reminderId, occurrenceAt)) {
            closeFollowUp(reminderId, occurrenceAt)
            return
        }
        val now = clock.instant()
        val delay = (minutes ?: reminder.followUpIntervalMinutes).coerceAtLeast(1)
        val resumeAt = now.plus(Duration.ofMinutes(delay.toLong()))
        repository.removePending(reminderId, occurrenceAt)
        repository.addPending(
            PendingConfirmation(
                reminderId = reminderId,
                occurrenceAt = occurrenceAt,
                since = resumeAt,
                nudgesSent = 0,
                deadlineAt = resumeAt.plus(
                    Duration.ofMinutes(reminder.followUpWindowMinutes.toLong()),
                ),
            ),
        )
        notifications.dismissFollowUp(reminderId)
        alarmGateway.scheduleFollowUp(reminderId, occurrenceAt, resumeAt)
    }

    /** Clears the pending state and everything that was driving it. */
    private suspend fun closeFollowUp(reminderId: Long, occurrenceAt: Instant) {
        repository.removePending(reminderId, occurrenceAt)
        alarmGateway.cancelFollowUp(reminderId)
        notifications.dismissFollowUp(reminderId)
    }

    /** Drops every follow-up of a reminder (edit, delete, disable, end series). */
    private suspend fun clearFollowUps(reminderId: Long) {
        repository.removePendingFor(reminderId)
        alarmGateway.cancelFollowUp(reminderId)
        notifications.dismissFollowUp(reminderId)
    }

    /**
     * Postpones the current occurrence; repeated snoozes after it rings again
     * move it again. [occurrenceAt] identifies notification actions so replaying
     * the same PendingIntent after the first snooze is a no-op.
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
        alarmGateway.cancelReAlert(reminderId)
        repository.setSnoozedUntil(reminderId, until)
        repository.setNextTrigger(reminderId, until)
        alarmGateway.schedule(reminderId, until, alarmClock = reminder.alertMode == AlertMode.ALARM)
        notifications.dismiss(reminderId)
    }

    /** Saves a reminder and (re)schedules it. Returns the id. */
    suspend fun save(reminder: Reminder): Long {
        val id = repository.upsert(reminder.copy(snoozedUntil = null))
        // An edit invalidates any pending re-alert of the old occurrence, and
        // any follow-up asking about a schedule the user has just rewritten.
        alarmGateway.cancelReAlert(id)
        clearFollowUps(id)
        scheduleNext(id)
        return id
    }

    suspend fun delete(reminderId: Long) {
        alarmGateway.cancel(reminderId)
        notifications.dismiss(reminderId)
        clearFollowUps(reminderId)
        repository.delete(reminderId)
    }

    suspend fun setEnabled(reminderId: Long, enabled: Boolean) {
        repository.setEnabled(reminderId, enabled)
        if (!enabled) {
            repository.setSnoozedUntil(reminderId, null)
            alarmGateway.cancelReAlert(reminderId)
            notifications.dismiss(reminderId)
            clearFollowUps(reminderId)
        }
        scheduleNext(reminderId)
    }

    /**
     * Rebuilds every alarm from the database — after boot, app update, process
     * restart, time or timezone changes, and from the daily reconcile worker.
     * A trigger missed while the device was off fires late within a grace
     * window (alarm-mode reminders ring for real); beyond the window a
     * recurring occurrence is recorded as missed and the schedule moves
     * forward. Idempotent.
     */
    suspend fun rescheduleAll(fireMissed: Boolean = true) {
        val now = clock.instant()
        restoreFollowUps(now)
        repository.getActive().forEach { reminder ->
            val missed = reminder.nextTriggerAt
            if (fireMissed && missed != null && !missed.isAfter(now)) {
                val grace = if (reminder.schedule.isRecurring) RECURRING_GRACE else ONCE_GRACE
                if (Duration.between(missed, now) <= grace) {
                    alert(reminder, missed, attempt = 1)
                } else if (reminder.schedule.isRecurring && !isResolved(reminder.id, missed)) {
                    repository.addRecord(record(reminder, missed, OccurrenceStatus.MISSED))
                }
            }
            // Clear a snooze that expired while the device was off; keep future ones.
            val staleSnooze = reminder.snoozedUntil?.isAfter(now) == false
            if (staleSnooze) {
                repository.setSnoozedUntil(reminder.id, null)
            }
            scheduleNext(if (staleSnooze) reminder.copy(snoozedUntil = null) else reminder)
        }
    }

    /**
     * Rebuilds the follow-up side of the world after boot or process death.
     * Pending confirmations live in the database precisely so that a reboot,
     * a swiped-away notification or an OEM cleanup cannot quietly lose a task:
     * whatever the shade did, the occurrence is still awaiting its answer here.
     * Anything already past its deadline is settled honestly as missed.
     */
    private suspend fun restoreFollowUps(now: Instant) {
        repository.getPending().forEach { pending ->
            val reminder = repository.getById(pending.reminderId)
            if (reminder == null || !reminder.followUntilComplete ||
                isResolved(pending.reminderId, pending.occurrenceAt)
            ) {
                closeFollowUp(pending.reminderId, pending.occurrenceAt)
                return@forEach
            }
            if (!now.isBefore(pending.deadlineAt) ||
                pending.nudgesSent >= reminder.followUpMaxRepeats
            ) {
                giveUpFollowUp(reminder, pending.occurrenceAt)
            } else {
                // Re-post the ask and re-arm the nudge the reboot threw away.
                notifications.showFollowUp(
                    reminder,
                    pending.occurrenceAt,
                    nudge = pending.nudgesSent,
                    remaining = (reminder.followUpMaxRepeats - pending.nudgesSent).coerceAtLeast(0),
                )
                alarmGateway.scheduleFollowUp(
                    pending.reminderId,
                    pending.occurrenceAt,
                    minOf(
                        now.plus(Duration.ofMinutes(reminder.followUpIntervalMinutes.toLong())),
                        pending.deadlineAt,
                    ),
                )
            }
        }
    }

    /** An occurrence is resolved once the user completed or skipped it. */
    private suspend fun isResolved(reminderId: Long, occurrenceAt: Instant): Boolean =
        repository.hasRecord(reminderId, occurrenceAt, OccurrenceStatus.COMPLETED) ||
            repository.hasRecord(reminderId, occurrenceAt, OccurrenceStatus.SKIPPED)

    private fun record(reminder: Reminder, occurrenceAt: Instant, status: OccurrenceStatus) =
        OccurrenceRecord(
            reminderId = reminder.id,
            reminderTitle = reminder.title,
            category = reminder.category,
            occurrenceAt = occurrenceAt,
            status = status,
            recordedAt = clock.instant(),
        )

    private companion object {
        val RECURRING_GRACE: Duration = Duration.ofMinutes(30)
        val ONCE_GRACE: Duration = Duration.ofHours(24)
        val RE_ALERT_DELAY: Duration = Duration.ofMinutes(5)
    }
}
