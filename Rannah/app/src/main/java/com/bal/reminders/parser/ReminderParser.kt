package com.bal.reminders.parser

import com.bal.reminders.domain.model.Schedule
import java.time.ZonedDateTime

/**
 * Turns free Arabic text into a reminder draft. Implementations must be pure:
 * same input + same [now] → same result. The rest of the app depends only on
 * this interface, so the rule-based parser can later be replaced (e.g. by an
 * on-device model) without touching the domain.
 */
interface ReminderParser {
    fun parse(input: String, now: ZonedDateTime): ParseResult
}

sealed interface ParseResult {

    /** Everything needed was understood. */
    data class Success(val title: String, val schedule: Schedule) : ParseResult

    /**
     * Part of the sentence was understood but something is missing;
     * [draft] carries the best guess so the editor can be pre-filled.
     */
    data class Incomplete(val draft: Draft, val missing: MissingPart) : ParseResult

    /** Blank input. */
    data object NoMatch : ParseResult

    data class Draft(val title: String?, val schedule: Schedule?)

    enum class MissingPart { TIME, TITLE }
}
