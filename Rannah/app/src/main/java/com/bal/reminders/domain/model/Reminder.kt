package com.bal.reminders.domain.model

import java.time.Instant

/**
 * A single reminder. رَنّة has one alerting behaviour: every reminder rings as a
 * full-screen alarm at its time, and is either postponed («تأجيل») or confirmed
 * done («تم»). There is no per-reminder alert style, follow-up policy or
 * ringtone to configure — the whole point of the app is that it just rings.
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val schedule: Schedule,
    val enabled: Boolean = true,
    /** Minutes «تأجيل» postpones the current occurrence. Seeded from settings. */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Set while a fired occurrence is snoozed; overrides the natural next occurrence. */
    val snoozedUntil: Instant? = null,
    /**
     * The occurrence a snooze is postponing — its identity, kept across any number
     * of «تأجيل» taps. Postponing moves *when* رَنّة asks again; it never turns the
     * 9:00 occurrence into a 9:10 one. Completing later therefore resolves the
     * occurrence that actually rang, so it cannot come back as «يحتاج تأكيدك».
     */
    val snoozedOccurrenceAt: Instant? = null,
    /** Cached next trigger, persisted so reboots can detect missed occurrences. */
    val nextTriggerAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    /**
     * Set when the user completes a one-time reminder. Recurring reminders never
     * carry it: pausing one is `enabled = false`, the single representation of
     * «متوقف مؤقتًا». (Databases from before v5 could hold an ended series here;
     * MIGRATION_4_5 rewrites those rows to paused.)
     */
    val completedAt: Instant? = null,
) {
    /** Done: a one-time reminder the user confirmed. */
    val isDone: Boolean get() = completedAt != null

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_ALARM_TIMEOUT_MINUTES = 3
        val SNOOZE_CHOICES = listOf(5, 10, 15, 30)
    }
}
