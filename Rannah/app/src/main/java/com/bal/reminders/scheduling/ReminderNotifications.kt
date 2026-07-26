package com.bal.reminders.scheduling

import com.bal.reminders.domain.model.Reminder
import java.time.Instant

/** Seam over the alarm-UI system so [ReminderScheduler] stays unit-testable. */
interface ReminderNotifications {

    /**
     * Rings the reminder: starts the foreground ringer and shows the full-screen
     * تأجيل/تم alarm surface (heads-up when full-screen is not permitted).
     */
    fun startAlarm(reminder: Reminder, occurrenceAt: Instant)

    /** Removes this reminder's alarm surface and stops its ringer if it is sounding. */
    fun dismiss(reminderId: Long)
}
