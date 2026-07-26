package com.bal.reminders.domain.model

import java.time.Instant

/**
 * Outcome of a single occurrence of a reminder. Occurrence-level state is what
 * lets one occurrence be completed or skipped without touching the series.
 */
enum class OccurrenceStatus(val id: String) {
    /** «تم الإنجاز»: the user confirmed the real task was done. */
    COMPLETED("completed"),

    /** «تخطي هذه المرة»: the user skipped this occurrence on purpose. */
    SKIPPED("skipped"),

    /**
     * «تم تجاهله»: the alert reached the user and was pushed away without an
     * answer. Nothing writes this any more — it belonged to the removed
     * follow-up feature — but databases from those versions still hold such
     * rows, so it stays readable rather than being silently re-read as
     * something it was not.
     */
    IGNORED("ignored"),

    /** «فات موعده»: the occurrence ran out of time with no answer at all. */
    MISSED("missed");

    /** Terminal states that stop رَنّة from asking about the occurrence again. */
    val resolvesOccurrence: Boolean get() = this == COMPLETED || this == SKIPPED

    companion object {
        fun fromId(id: String?): OccurrenceStatus =
            entries.firstOrNull { it.id == id } ?: COMPLETED
    }
}

/**
 * A log entry recording what happened to one occurrence of a reminder.
 * Uniqueness of (reminderId, occurrenceAt, status) makes recording idempotent:
 * duplicate notification taps or replayed intents cannot double-log.
 */
data class OccurrenceRecord(
    val id: Long = 0L,
    val reminderId: Long,
    /** The reminder title at record time, kept so the log survives deletion. */
    val reminderTitle: String,
    /** The occurrence this record belongs to (the scheduled trigger time). */
    val occurrenceAt: Instant,
    val status: OccurrenceStatus,
    /** When the record was made (completion tap, skip tap, or miss detection). */
    val recordedAt: Instant,
)
