package com.bal.reminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE reminders SET snoozedUntilMillis = :until WHERE id = :id")
    suspend fun setSnoozedUntil(id: Long, until: Long?)

    @Query("UPDATE reminders SET nextTriggerAtMillis = :at WHERE id = :id")
    suspend fun setNextTrigger(id: Long, at: Long?)

    @Query("UPDATE reminders SET completedAtMillis = :at, snoozedUntilMillis = NULL, nextTriggerAtMillis = NULL WHERE id = :id")
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

    @Query("DELETE FROM completions")
    suspend fun clearRecords()
}
