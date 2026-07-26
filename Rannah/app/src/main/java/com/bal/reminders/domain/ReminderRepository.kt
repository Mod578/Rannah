package com.bal.reminders.domain

import com.bal.reminders.domain.model.DeletedReminder
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    fun observeById(id: Long): Flow<Reminder?>
    suspend fun getById(id: Long): Reminder?

    /** Enabled, not-completed reminders — the set that needs alarms. */
    suspend fun getActive(): List<Reminder>

    /** Inserts when id == 0, updates otherwise. Returns the reminder id. */
    suspend fun upsert(reminder: Reminder): Long

    /**
     * Deletes the reminder and its records in one transaction, returning what was
     * removed so the deletion can be undone. Null when the id was already gone.
     */
    suspend fun deleteWithRecords(id: Long): DeletedReminder?

    /** Puts a [deleted] reminder back under its original id, with its records. */
    suspend fun restore(deleted: DeletedReminder)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Records that [occurrence] is postponed until [until]; both null clears it. */
    suspend fun setSnooze(id: Long, until: Instant?, occurrence: Instant?)
    suspend fun setNextTrigger(id: Long, at: Instant?)

    /** Completes a one-time reminder. */
    suspend fun markCompleted(id: Long, at: Instant)

    /** Reverses [markCompleted] (undo). */
    suspend fun clearCompleted(id: Long)

    fun observeRecords(): Flow<List<OccurrenceRecord>>
    fun observeRecordsFor(reminderId: Long): Flow<List<OccurrenceRecord>>

    /**
     * Adds an occurrence record. Returns false when an identical
     * (reminder, occurrence, status) record already exists, which is what
     * makes duplicate intents harmless.
     */
    suspend fun addRecord(record: OccurrenceRecord): Boolean

    /** Removes a record again (undo). */
    suspend fun removeRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus)

    suspend fun hasRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus): Boolean

    /**
     * Deletes one-time reminders (and their records) completed before [before]
     * (the start of the current local day). Transactional and idempotent.
     * Returns the number removed.
     */
    suspend fun pruneCompletedOnceBefore(before: Instant): Int

    /** Drops occurrence records whose record time and occurrence are both before [before]. */
    suspend fun pruneRecordsBefore(before: Instant)
}
