package com.bal.reminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY nextTriggerAtMillis IS NULL, nextTriggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND completedAtMillis IS NULL")
    suspend fun getActive(): List<ReminderEntity>

    @Insert
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Sets both halves of a snooze in one statement: the instant and its occurrence. */
    @Query(
        "UPDATE reminders SET snoozedUntilMillis = :until, snoozedOccurrenceAtMillis = :occurrence " +
            "WHERE id = :id",
    )
    suspend fun setSnooze(id: Long, until: Long?, occurrence: Long?)

    @Query("UPDATE reminders SET nextTriggerAtMillis = :at WHERE id = :id")
    suspend fun setNextTrigger(id: Long, at: Long?)

    @Query(
        "UPDATE reminders SET completedAtMillis = :at, snoozedUntilMillis = NULL, " +
            "snoozedOccurrenceAtMillis = NULL, nextTriggerAtMillis = NULL WHERE id = :id",
    )
    suspend fun markCompleted(id: Long, at: Long)

    @Query("UPDATE reminders SET completedAtMillis = NULL WHERE id = :id")
    suspend fun clearCompleted(id: Long)

    @Query("SELECT * FROM completions ORDER BY completedAtMillis DESC LIMIT 500")
    fun observeRecords(): Flow<List<OccurrenceRecordEntity>>

    @Query("SELECT * FROM completions WHERE reminderId = :reminderId ORDER BY completedAtMillis DESC LIMIT 30")
    fun observeRecordsFor(reminderId: Long): Flow<List<OccurrenceRecordEntity>>

    /** Returns -1 when the same (reminder, occurrence, status) record already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(entity: OccurrenceRecordEntity): Long

    @Query("DELETE FROM completions WHERE reminderId = :reminderId AND occurrenceAtMillis = :occurrenceAtMillis AND status = :status")
    suspend fun deleteRecord(reminderId: Long, occurrenceAtMillis: Long, status: String)

    @Query("SELECT COUNT(*) FROM completions WHERE reminderId = :reminderId AND occurrenceAtMillis = :occurrenceAtMillis AND status = :status")
    suspend fun countRecords(reminderId: Long, occurrenceAtMillis: Long, status: String): Int

    @Query("SELECT * FROM completions WHERE reminderId = :reminderId")
    suspend fun recordsFor(reminderId: Long): List<OccurrenceRecordEntity>

    // ------------------------------------------- one answer per occurrence

    /** Terminal answers already recorded for one occurrence: completed or skipped. */
    @Query(
        "SELECT COUNT(*) FROM completions WHERE reminderId = :reminderId " +
            "AND occurrenceAtMillis = :occurrenceAtMillis AND status IN ('completed', 'skipped')",
    )
    suspend fun countTerminalRecords(reminderId: Long, occurrenceAtMillis: Long): Int

    @Query(
        "DELETE FROM completions WHERE reminderId = :reminderId " +
            "AND occurrenceAtMillis = :occurrenceAtMillis AND status = 'missed'",
    )
    suspend fun deleteMissedRecord(reminderId: Long, occurrenceAtMillis: Long)

    /**
     * Records «تم» or «تخطي اليوم» for one occurrence, and refuses if that
     * occurrence already has *either* answer. The unique index alone cannot
     * express this — it is per (reminder, occurrence, status), so a COMPLETED and
     * a SKIPPED row for the same occurrence are both legal to SQLite — and a
     * plain unique index on (reminder, occurrence) would forbid the MISSED row
     * that legitimately precedes a late answer. So the invariant lives here, in
     * one transaction: read, decide, write.
     *
     * A MISSED record for the same occurrence is cleared on the way: the alarm
     * did ring out, but the user has now answered, and the history should say the
     * later, truer thing rather than both.
     */
    @Transaction
    suspend fun insertTerminalRecord(entity: OccurrenceRecordEntity): Boolean {
        if (countTerminalRecords(entity.reminderId, entity.occurrenceAtMillis) > 0) return false
        if (insertRecord(entity) == -1L) return false
        deleteMissedRecord(entity.reminderId, entity.occurrenceAtMillis)
        return true
    }

    // ------------------------------------------------------------ deletion

    /**
     * Deletes a reminder and everything that only described it, in one
     * transaction, and returns what was removed so «تراجع» can put it back
     * exactly. Records outlive nothing: they exist to answer "was this
     * occurrence resolved" and to fill this reminder's history, and both
     * audiences disappear with the reminder. Returns null when the id is
     * already gone, which makes a repeated delete a no-op.
     */
    @Transaction
    suspend fun deleteWithRecords(id: Long): DeletedReminderEntities? {
        val reminder = getById(id) ?: return null
        val records = recordsFor(id)
        deleteRecordsForReminders(listOf(id))
        delete(id)
        return DeletedReminderEntities(reminder, records)
    }

    /**
     * Puts a deleted reminder back under its original id, with its records.
     * Idempotent: re-running finds the row present and the records rejected by
     * the unique (reminder, occurrence, status) index.
     */
    @Transaction
    suspend fun restore(snapshot: DeletedReminderEntities) {
        insertOrReplace(snapshot.reminder)
        snapshot.records.forEach { insertRecord(it.copy(id = 0L)) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: ReminderEntity)

    // ------------------------------------------------- cleanup of finished one-timers

    @Query(
        "SELECT id FROM reminders WHERE completedAtMillis IS NOT NULL " +
            "AND recurrenceType = 'once' AND completedAtMillis < :before",
    )
    suspend fun completedOnceReminderIdsBefore(before: Long): List<Long>

    @Query("DELETE FROM reminders WHERE id IN (:ids)")
    suspend fun deleteReminders(ids: List<Long>)

    @Query("DELETE FROM completions WHERE reminderId IN (:ids)")
    suspend fun deleteRecordsForReminders(ids: List<Long>)

    /**
     * Deletes one-time reminders (and their occurrence records) that were
     * completed on a previous local day. Transactional and idempotent: a second
     * run finds nothing left. Never touches an active or unresolved reminder,
     * nor a recurring one. Returns how many reminders were removed.
     */
    @Transaction
    suspend fun pruneCompletedOnceBefore(before: Long): Int {
        val ids = completedOnceReminderIdsBefore(before)
        if (ids.isNotEmpty()) {
            deleteRecordsForReminders(ids)
            deleteReminders(ids)
        }
        return ids.size
    }

    /**
     * Drops occurrence records that are past on both axes: recorded before
     * [before] *and* for an occurrence before it. The second condition protects a
     * reminder completed ahead of time — its record is young but its occurrence
     * is still in the future, and it is the only thing marking that occurrence
     * resolved.
     */
    @Query(
        "DELETE FROM completions WHERE completedAtMillis < :before AND occurrenceAtMillis < :before",
    )
    suspend fun pruneRecordsBefore(before: Long)
}
