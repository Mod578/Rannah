package com.bal.reminders.domain

import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.PendingConfirmation
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
    suspend fun delete(id: Long)

    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun setSnoozedUntil(id: Long, until: Instant?)
    suspend fun setNextTrigger(id: Long, at: Instant?)

    /** Completes a one-time reminder, or ends a recurring series. */
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

    suspend fun clearRecords()

    // ------------------------------------------------- بانتظار تأكيدك

    fun observePending(): Flow<List<PendingConfirmation>>

    suspend fun getPending(): List<PendingConfirmation>

    suspend fun getPending(reminderId: Long, occurrenceAt: Instant): PendingConfirmation?

    /**
     * Opens «بانتظار تأكيدك» for an occurrence. Returns false when it was
     * already pending, so a replayed intent cannot start a second follow-up.
     */
    suspend fun addPending(pending: PendingConfirmation): Boolean

    suspend fun setPendingNudges(reminderId: Long, occurrenceAt: Instant, nudgesSent: Int)

    suspend fun removePending(reminderId: Long, occurrenceAt: Instant)

    /** Drops every pending confirmation of a reminder (edit, delete, disable). */
    suspend fun removePendingFor(reminderId: Long)
}
