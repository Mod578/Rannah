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

    /** Brief undo affordance after completing from a notification action. */
    fun showCompletedUndo(reminder: Reminder, occurrenceAt: Instant)

    /** Removes this reminder's notification and stops its ringer if it is sounding. */
    fun dismiss(reminderId: Long)
}
