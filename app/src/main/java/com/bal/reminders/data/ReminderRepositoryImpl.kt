package com.bal.reminders.data

import com.bal.reminders.data.db.ReminderDao
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
) : ReminderRepository {

    override fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Reminder?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: Long): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun getActive(): List<Reminder> = dao.getActive().map { it.toDomain() }

    override suspend fun upsert(reminder: Reminder): Long {
        val entity = reminder.toEntity()
        return if (entity.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun setSnoozedUntil(id: Long, until: Instant?) =
        dao.setSnoozedUntil(id, until?.toEpochMilli())

    override suspend fun setNextTrigger(id: Long, at: Instant?) =
        dao.setNextTrigger(id, at?.toEpochMilli())

    override suspend fun markCompleted(id: Long, at: Instant) =
        dao.markCompleted(id, at.toEpochMilli())

    override suspend fun clearCompleted(id: Long) = dao.clearCompleted(id)

    override fun observeRecords(): Flow<List<OccurrenceRecord>> =
        dao.observeRecords().map { list -> list.map { it.toDomain() } }

    override fun observeRecordsFor(reminderId: Long): Flow<List<OccurrenceRecord>> =
        dao.observeRecordsFor(reminderId).map { list -> list.map { it.toDomain() } }

    override suspend fun addRecord(record: OccurrenceRecord): Boolean =
        dao.insertRecord(record.toEntity()) != -1L

    override suspend fun removeRecord(
        reminderId: Long,
        occurrenceAt: Instant,
        status: OccurrenceStatus,
    ) = dao.deleteRecord(reminderId, occurrenceAt.toEpochMilli(), status.id)

    override suspend fun hasRecord(
        reminderId: Long,
        occurrenceAt: Instant,
        status: OccurrenceStatus,
    ): Boolean = dao.countRecords(reminderId, occurrenceAt.toEpochMilli(), status.id) > 0

    override suspend fun clearRecords() = dao.clearRecords()
}
