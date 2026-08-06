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

/**
 * Either رَنّة understood a whole reminder, or it did not offer one.
 *
 * There used to be a third answer: a partial draft with the missing part named
 * built on every keystroke and read by nobody: the editor handles [Success]
 * and discards everything else, and the pickers it would have pre-filled are
 * already on screen. A half-understood sentence now simply produces no
 * suggestion, which is what the user saw all along.
 */
sealed interface ParseResult {

    /** Everything needed was understood: a title and a complete schedule. */
    data class Success(val title: String, val schedule: Schedule) : ParseResult

    /** Nothing worth offering: blank input, or not enough to build a schedule. */
    data object NoMatch : ParseResult
}
