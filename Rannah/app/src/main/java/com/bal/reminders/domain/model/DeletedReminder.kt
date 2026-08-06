package com.bal.reminders.domain.model

/**
 * A reminder that was just deleted, with the occurrence records that went with
 * it. رَنّة never keeps a deleted row in the database waiting to be revived, 
 * deletion is a real delete, so the only thing standing between «حذف» and
 * «تراجع» is this snapshot, held in memory for as long as the undo is offered.
 * If the process dies first, the deletion simply stands.
 */
data class DeletedReminder(
    val reminder: Reminder,
    val records: List<OccurrenceRecord>,
)
