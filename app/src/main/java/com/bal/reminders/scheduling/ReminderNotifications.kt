package com.bal.reminders.scheduling

import com.bal.reminders.domain.model.Reminder
import java.time.Instant

/** Seam over the notification/alarm-UI system so [ReminderScheduler] stays unit-testable. */
interface ReminderNotifications {

    /** Standard notification («تنبيه عادي»): [تم] [تأجيل] (+ skip when recurring). */
    fun show(reminder: Reminder, occurrenceAt: Instant)

    /**
     * Full alarm experience («منبّه مهم»): foreground ringer + full-screen
     * إيقاف/تأجيل UI. [attempt] is 1 for the first ring, 2 for the single
     * re-alert after an ignored timeout.
     */
    fun startAlarm(reminder: Reminder, occurrenceAt: Instant, attempt: Int)

    /** High-priority fallback after an alarm rang out unanswered. */
    fun showMissed(reminder: Reminder, occurrenceAt: Instant)

    /**
     * After stopping an alarm from outside the alarm screen: a quiet follow-up
     * asking whether the obligation is actually done (stop is not completion).
     */
    fun showStopFollowUp(reminder: Reminder, occurrenceAt: Instant)

    /**
     * The «المتابعة حتى الإنجاز» ask: «هل سجلت بصمة الدوام؟» with the
     * reminder's own completion phrase as the primary action and a secondary
     * snooze. [nudge] is 0 for the first ask and counts up with each repeat;
     * [remaining] is how many asks are left, so the notification can tell the
     * user when رَنّة will stop by itself.
     *
     * Ongoing while the ask is live so it is not swiped away by accident, but
     * never beyond the reminder's own repeat budget, and always removable via
     * the completion action, the app, or the channel's own settings.
     */
    fun showFollowUp(reminder: Reminder, occurrenceAt: Instant, nudge: Int, remaining: Int)

    /** Clears the follow-up surface alone, leaving other notifications alone. */
    fun dismissFollowUp(reminderId: Long)

    /** Brief undo affordance after completing from a notification action. */
    fun showCompletedUndo(reminder: Reminder, occurrenceAt: Instant)

    /** Removes this reminder's notification and stops its ringer if it is sounding. */
    fun dismiss(reminderId: Long)
}
