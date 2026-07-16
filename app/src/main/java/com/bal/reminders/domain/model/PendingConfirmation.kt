package com.bal.reminders.domain.model

import java.time.Instant

/**
 * One occurrence sitting in «بانتظار تأكيدك»: the alert was delivered and
 * silenced, but nobody has said whether the real task actually happened.
 *
 * This is live state, not history, which is why it is its own row rather than
 * an [OccurrenceRecord]: records are terminal outcomes and are immutable once
 * written, while a pending confirmation is expected to change (another nudge
 * goes out) and then disappear (into COMPLETED, SKIPPED or MISSED).
 *
 * It lives in the database so the state is the app's own, not the notification
 * shade's. If Android or an OEM removes the notification, or the phone reboots,
 * the occurrence is still awaiting confirmation and رَنّة picks up where the
 * policy left off instead of silently forgetting the task.
 */
data class PendingConfirmation(
    val reminderId: Long,
    /** The scheduled occurrence this is asking about. */
    val occurrenceAt: Instant,
    /** When the occurrence entered «بانتظار تأكيدك». */
    val since: Instant,
    /** How many times رَنّة has asked so far. */
    val nudgesSent: Int,
    /**
     * The moment the policy gives up and records the occurrence as missed.
     * Derived from the reminder's own interval and repeat budget when the
     * pending state is created, so a later settings change cannot silently
     * extend an already-running follow-up.
     */
    val deadlineAt: Instant,
)
