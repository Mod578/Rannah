package com.bal.reminders.domain

import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.ReminderKind
import com.bal.reminders.domain.model.Schedule
import java.time.Instant
import java.time.ZoneId

/**
 * The single user-facing state of a reminder's current occurrence. Every surface
 * home, details, and (through the same instants) notifications and alarm
 * restoration: reads this instead of re-deriving state from raw fields, so they
 * can never disagree.
 */
enum class ReminderPhase {
    /** قادم: enabled, next occurrence is still ahead (today-later or a future day). */
    UPCOMING,

    /** مؤجل: postponed; waiting until a known instant. */
    SNOOZED,

    /** يحتاج تأكيدك: today's occurrence has passed and is still unresolved. */
    NEEDS_CONFIRMATION,

    /**
     * متأخر: a one-time reminder whose day is behind us and which was never
     * answered. It is deliberately *not* [NEEDS_CONFIRMATION]: that state belongs
     * to today, and filing a three-week-old errand under «اليوم» with nothing but
     * a clock time told the user something untrue. An overdue reminder is shown
     * with its real date, above the day, until it is completed or deleted, it is
     * never silently discarded.
     */
    OVERDUE,

    /** مكتمل: a one-time reminder that has been completed. */
    COMPLETED,

    /** متوقف مؤقتًا: paused (disabled), or a legacy ended recurring series. */
    PAUSED,
}

/**
 * The resolved current occurrence of one reminder. [occurrenceAt] is the identity
 * an action (complete/undo) must use: the same identity the alarm and the home
 * use, and [displayAt] is the instant to show (the snooze time when SNOOZED, the
 * occurrence/next time otherwise).
 */
data class ReminderOccurrence(
    val reminderId: Long,
    val title: String,
    val schedule: Schedule,
    val recurring: Boolean,
    val phase: ReminderPhase,
    val occurrenceAt: Instant?,
    val displayAt: Instant?,
) {
    /** Which of the three user-facing kinds this reminder is. */
    val kind: ReminderKind get() = schedule.kind

    /** «تخطي اليوم» only means something where there is a next occurrence to keep. */
    val canSkip: Boolean get() = recurring &&
        (phase == ReminderPhase.NEEDS_CONFIRMATION ||
            phase == ReminderPhase.SNOOZED ||
            phase == ReminderPhase.UPCOMING)
}

/**
 * The one place a reminder's display state is computed. Scheduling stays
 * Gregorian, so no Hijri adjustment is applied here.
 */
object OccurrenceStateResolver {

    /**
     * Resolves [reminder]'s current occurrence. [isResolved] answers whether a
     * given occurrence already has a terminal (completed/skipped) record, using
     * the same occurrence identity everywhere.
     *
     * Priority: paused/ended → snoozed → today's fired-unresolved → today's
     * later → (today's already resolved → next occurrence) → future → one-time
     * passed (today: needs confirmation; an earlier day: overdue).
     */
    fun resolve(
        reminder: Reminder,
        now: Instant,
        zone: ZoneId,
        isResolved: (occurrenceAt: Instant) -> Boolean,
    ): ReminderOccurrence {
        fun view(phase: ReminderPhase, occ: Instant?, display: Instant?) = ReminderOccurrence(
            reminderId = reminder.id,
            title = reminder.title,
            schedule = reminder.schedule,
            recurring = reminder.schedule.isRecurring,
            phase = phase,
            occurrenceAt = occ,
            displayAt = display,
        )

        // Completed: a one-time reminder the user confirmed. A recurring reminder
        // carrying completedAt can only be pre-v5 data (the removed «إنهاء
        // التكرار»); MIGRATION_4_5 rewrites those to paused, and a database
        // restored from an older backup is read the same way here rather than
        // being shown as active while it silently never fires.
        if (reminder.isDone) {
            return if (reminder.schedule.isRecurring) {
                view(ReminderPhase.PAUSED, null, nextFrom(reminder, now, zone))
            } else {
                view(ReminderPhase.COMPLETED, onceInstant(reminder.schedule, zone), reminder.completedAt)
            }
        }
        // Paused: silent, subdued, reachable, and [displayAt] is the occurrence
        // it would return to, so «استئناف» is never a leap in the dark.
        if (!reminder.enabled) {
            return view(ReminderPhase.PAUSED, null, nextFrom(reminder, now, zone))
        }

        // Snoozed: waiting until a known instant. The occurrence identity is the
        // one that rang, so completing from here resolves *that* occurrence.
        reminder.snoozedUntil?.takeIf { it.isAfter(now) }?.let { until ->
            return view(ReminderPhase.SNOOZED, reminder.snoozedOccurrenceAt ?: until, until)
        }

        val today = now.atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).minusSeconds(1)
        val occToday = RecurrenceCalculator.nextOccurrence(reminder.schedule, startOfToday)?.toInstant()

        if (occToday != null && occToday.atZone(zone).toLocalDate() == today) {
            if (isResolved(occToday)) {
                // Already done/skipped today → what is upcoming is the next one.
                val next = RecurrenceCalculator
                    .nextOccurrence(reminder.schedule, occToday.atZone(zone))?.toInstant()
                return if (next != null) view(ReminderPhase.UPCOMING, next, next)
                else view(ReminderPhase.COMPLETED, occToday, occToday)
            }
            return if (occToday.isAfter(now)) view(ReminderPhase.UPCOMING, occToday, occToday)
            else view(ReminderPhase.NEEDS_CONFIRMATION, occToday, occToday)
        }
        if (occToday != null) return view(ReminderPhase.UPCOMING, occToday, occToday) // a future day

        // A one-time reminder whose moment has passed and was never resolved. Its
        // own day makes it today's business; any earlier day makes it overdue, and
        // it says so with the date it was actually due.
        val passed = onceInstant(reminder.schedule, zone)?.takeIf { !it.isAfter(now) }
        if (passed != null && !isResolved(passed)) {
            val phase = if (passed.atZone(zone).toLocalDate() < today) {
                ReminderPhase.OVERDUE
            } else {
                ReminderPhase.NEEDS_CONFIRMATION
            }
            return view(phase, passed, passed)
        }
        return view(ReminderPhase.UPCOMING, reminder.nextTriggerAt, reminder.nextTriggerAt)
    }

    /** The occurrence a paused reminder would return to, or null when it has none left. */
    private fun nextFrom(reminder: Reminder, now: Instant, zone: ZoneId): Instant? =
        RecurrenceCalculator.nextOccurrence(reminder.schedule, now.atZone(zone))?.toInstant()

    /** The civil instant of a one-time (Gregorian or legacy Hijri) schedule. */
    fun onceInstant(schedule: Schedule, zone: ZoneId): Instant? = when (schedule) {
        is Schedule.Once -> schedule.date.atTime(schedule.time).atZone(zone).toInstant()
        is Schedule.OnceHijri ->
            HijriDates.toGregorian(schedule.year, schedule.month, schedule.day)
                ?.atTime(schedule.time)?.atZone(zone)?.toInstant()
        else -> null
    }
}
