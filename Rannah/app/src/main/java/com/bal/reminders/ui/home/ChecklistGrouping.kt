package com.bal.reminders.ui.home

import com.bal.reminders.domain.OccurrenceStateResolver
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Instant
import java.time.ZoneId

/**
 * Which section of the home list each reminder belongs in.
 *
 * This is pure on purpose. The one real defect the list ever had lived here —
 * a reminder was hidden from «اليوم» because it *had a completed record today*,
 * regardless of whether another occurrence was still coming — and a rule that
 * subtle deserves to be checkable without a device, a view model, or a clock
 * that only moves forwards.
 */
internal object ChecklistGrouping {

    fun group(
        reminders: List<Reminder>,
        records: List<OccurrenceRecord>,
        now: Instant,
        zone: ZoneId,
    ): ChecklistState {
        val today = now.atZone(zone).toLocalDate()

        // reminderId -> occurrences already answered (completed or skipped).
        val resolved = HashMap<Long, HashSet<Long>>()
        records.forEach { r ->
            if (r.status.resolvesOccurrence) {
                resolved.getOrPut(r.reminderId) { HashSet() }.add(r.occurrenceAt.toEpochMilli())
            }
        }
        val closedRecords = records.filter {
            it.status.resolvesOccurrence && it.recordedAt.atZone(zone).toLocalDate() == today
        }
        val closedTodayIds = closedRecords.map { it.reminderId }.toHashSet()
        val byId = reminders.associateBy { it.id }

        val overdue = ArrayList<ReminderOccurrence>()
        val todayList = ArrayList<ReminderOccurrence>()
        val upcoming = ArrayList<ReminderOccurrence>()
        val paused = ArrayList<ReminderOccurrence>()

        reminders.forEach { reminder ->
            val ids = resolved[reminder.id]
            val view = OccurrenceStateResolver.resolve(reminder, now, zone) { occ ->
                ids?.contains(occ.toEpochMilli()) == true
            }
            when (view.phase) {
                ReminderPhase.COMPLETED -> Unit // one-time done → listed from its record below
                ReminderPhase.PAUSED -> paused += view
                ReminderPhase.OVERDUE -> overdue += view
                ReminderPhase.NEEDS_CONFIRMATION, ReminderPhase.SNOOZED -> todayList += view
                ReminderPhase.UPCOMING -> {
                    val at = view.displayAt
                    when {
                        // An occurrence that still rings *today* is always listed,
                        // whatever was answered earlier. Suppressing it because the
                        // reminder had a completed record today is how a row came to
                        // read «مكتمل · القادمة اليوم» while the alarm it was hiding
                        // went on to ring.
                        at != null && at.atZone(zone).toLocalDate() == today -> todayList += view
                        // Otherwise the reminder is finished for today, and its
                        // closed row already says when it returns; listing tomorrow's
                        // occurrence again under «قادم» would say it twice.
                        reminder.id in closedTodayIds -> Unit
                        else -> upcoming += view
                    }
                }
            }
        }

        val closed = closedRecords
            .sortedByDescending { it.recordedAt }
            .map { record ->
                val reminder = byId[record.reminderId]
                ClosedItem(
                    reminderId = record.reminderId,
                    title = record.reminderTitle,
                    occurrenceAt = record.occurrenceAt,
                    status = record.status,
                    returnsAt = reminder?.takeIf { it.schedule.isRecurring && it.enabled }
                        ?.let { nextAfter(it.schedule, now, zone) },
                )
            }

        return ChecklistState(
            // Oldest first: the thing that has waited longest is the thing to answer.
            overdue = overdue.sortedBy { it.displayAt },
            // What is waiting rides above what is merely scheduled; inside each
            // group, the clock decides.
            today = todayList.sortedWith(
                compareBy({ it.phase != ReminderPhase.NEEDS_CONFIRMATION }, { it.displayAt }),
            ),
            upcoming = upcoming.sortedBy { it.displayAt },
            closed = closed,
            paused = paused.sortedBy { it.title },
            hasAnyReminder = reminders.isNotEmpty(),
        )
    }

    /** The real next occurrence — never a fixed phrase that assumes "tomorrow". */
    private fun nextAfter(schedule: Schedule, now: Instant, zone: ZoneId): Instant? =
        RecurrenceCalculator.nextOccurrence(schedule, now.atZone(zone))?.toInstant()
}
