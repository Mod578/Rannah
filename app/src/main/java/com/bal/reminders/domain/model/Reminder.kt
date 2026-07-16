package com.bal.reminders.domain.model

import java.time.Instant

enum class Category(val id: String) {
    WORK("work"),
    HEALTH("health"),
    BILLS("bills"),
    STUDY("study"),
    FAMILY("family"),
    PERSONAL("personal");

    companion object {
        fun fromId(id: String): Category = entries.firstOrNull { it.id == id } ?: PERSONAL
    }
}

enum class Priority { NORMAL, HIGH }

/**
 * How the reminder alerts when it fires.
 *
 * [STANDARD] («تنبيه عادي»): a high-importance notification with complete and
 * snooze actions. [ALARM] («منبّه مهم»): a real clock-alarm experience with a
 * looping ringtone, a full-screen stop/snooze UI, and a timeout policy.
 * Stopping an alarm's sound is not the same as completing the obligation.
 */
enum class AlertMode(val id: String) {
    STANDARD("standard"),
    ALARM("alarm");

    companion object {
        fun fromId(id: String?): AlertMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

data class Reminder(
    val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val category: Category = Category.PERSONAL,
    val priority: Priority = Priority.NORMAL,
    val schedule: Schedule,
    val enabled: Boolean = true,
    val alertMode: AlertMode = AlertMode.STANDARD,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Alarm mode: system alarm ringtone uri; null means the device default. */
    val ringtoneUri: String? = null,
    /** Alarm mode: how long the alarm rings before it gives up as missed. */
    val alarmTimeoutMinutes: Int = DEFAULT_ALARM_TIMEOUT_MINUTES,
    /** Alarm mode: ramp the volume up over the first seconds of ringing. */
    val alarmGradualVolume: Boolean = true,
    /** Alarm mode: after an ignored timeout, ring one more time. */
    val alarmRepeatIfIgnored: Boolean = false,
    /**
     * Alarm mode: the user explicitly opted that stopping the sound also
     * completes the occurrence. Off by default: إيقاف is never تم unless asked.
     */
    val stopMarksCompleted: Boolean = false,
    /** Set while a fired occurrence is snoozed; overrides the natural next occurrence. */
    val snoozedUntil: Instant? = null,
    /** Cached next trigger, persisted so reboots can detect missed occurrences. */
    val nextTriggerAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    /**
     * For one-time schedules: set when the user completes it.
     * For recurring schedules: set when the user ends the series
     * («إنهاء التكرار»); the reminder stays in the list but never fires again.
     */
    val completedAt: Instant? = null,
) {
    /** Done for a one-time reminder; series ended for a recurring one. */
    val isDone: Boolean get() = completedAt != null

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_ALARM_TIMEOUT_MINUTES = 3
    }
}
