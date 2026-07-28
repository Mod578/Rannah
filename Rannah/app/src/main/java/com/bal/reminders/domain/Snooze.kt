package com.bal.reminders.domain

import java.time.Duration
import java.time.Instant

/**
 * The user's global «مدة التأجيل الافتراضية», read at snooze time rather than
 * copied onto each reminder when it is created. That is the whole difference
 * between a setting that means something and a setting that lies: changing it
 * now changes what «تأجيل» does for every reminder, old and new.
 */
fun interface SnoozeDefaultProvider {
    suspend fun defaultMinutes(): Int
}

/** What the user asked «تأجيل» to do for this one occurrence. */
sealed interface SnoozeRequest {

    /** The plain «تأجيل» button: whatever «مدة التأجيل الافتراضية» currently says. */
    data object Default : SnoozeRequest

    /** A duration picked from «مدة أخرى», for this occurrence only. */
    data class Minutes(val minutes: Int) : SnoozeRequest

    /** «حتى وقت محدد»: an absolute instant the user saw spelled out before confirming. */
    data class Until(val instant: Instant) : SnoozeRequest
}

/**
 * The outcome of a «تأجيل». [TooLate] is a real answer, not a silent clamp: a
 * postponement that would run past the reminder's own next occurrence would
 * swallow it (one alarm, one trigger per reminder), so رَنّة says so and asks
 * for another time instead of quietly choosing one.
 */
sealed interface SnoozeResult {

    data class Scheduled(val until: Instant, val occurrenceAt: Instant) : SnoozeResult

    /** Refused: [latest] is the last instant this occurrence may be postponed to. */
    data class TooLate(val latest: Instant) : SnoozeResult

    /** Nothing to postpone (already gone, already postponed, or paused). */
    data object Unavailable : SnoozeResult
}

object SnoozeLimits {
    /** Beyond this, «تأجيل» stops meaning "postpone" and starts meaning "reschedule". */
    val MAXIMUM: Duration = Duration.ofHours(12)

    /** Never let a postponement land on top of the next natural occurrence. */
    val SAFETY_MARGIN: Duration = Duration.ofMinutes(1)

    val MINIMUM: Duration = Duration.ofMinutes(1)
}
