package com.bal.reminders.scheduling

import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.DeletedReminder
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the three seams [ReminderScheduler] talks through.
 * [FakeRepository] mirrors the DAO's real contracts, including the transactional
 * "one answer per occurrence" rule, so a test that passes here is testing the
 * behaviour the database actually enforces, not a looser version of it.
 */
internal class FakeRepository : ReminderRepository {
    private val store = MutableStateFlow<Map<Long, Reminder>>(emptyMap())
    val records = mutableListOf<OccurrenceRecord>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Reminder>> = store.map { it.values.toList() }
    override fun observeById(id: Long): Flow<Reminder?> = store.map { it[id] }
    override suspend fun getById(id: Long): Reminder? = store.value[id]
    override suspend fun getActive(): List<Reminder> =
        store.value.values.filter { it.enabled && !it.isDone }

    override suspend fun upsert(reminder: Reminder): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
        store.value = store.value + (id to reminder.copy(id = id))
        return id
    }

    override suspend fun deleteWithRecords(id: Long): DeletedReminder? {
        val reminder = store.value[id] ?: return null
        val removed = records.filter { it.reminderId == id }
        records.removeAll { it.reminderId == id }
        store.value = store.value - id
        return DeletedReminder(reminder, removed)
    }

    override suspend fun restore(deleted: DeletedReminder) {
        store.value = store.value + (deleted.reminder.id to deleted.reminder)
        deleted.records.forEach { records += it }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = mutate(id) { it.copy(enabled = enabled) }
    override suspend fun setSnooze(id: Long, until: Instant?, occurrence: Instant?) =
        mutate(id) { it.copy(snoozedUntil = until, snoozedOccurrenceAt = occurrence) }
    override suspend fun setNextTrigger(id: Long, at: Instant?) = mutate(id) { it.copy(nextTriggerAt = at) }
    override suspend fun markCompleted(id: Long, at: Instant) = mutate(id) {
        it.copy(
            completedAt = at,
            snoozedUntil = null,
            snoozedOccurrenceAt = null,
            nextTriggerAt = null,
        )
    }

    override suspend fun clearCompleted(id: Long) = mutate(id) { it.copy(completedAt = null) }

    override fun observeRecords(): Flow<List<OccurrenceRecord>> = MutableStateFlow(records.toList())
    override fun observeRecordsFor(reminderId: Long): Flow<List<OccurrenceRecord>> =
        MutableStateFlow(records.filter { it.reminderId == reminderId })

    override suspend fun addRecord(record: OccurrenceRecord): Boolean {
        // Mirrors the DB's unique (reminderId, occurrenceAt, status) index.
        if (records.any {
                it.reminderId == record.reminderId &&
                    it.occurrenceAt == record.occurrenceAt &&
                    it.status == record.status
            }
        ) {
            return false
        }
        records += record.copy(id = (records.maxOfOrNull { it.id } ?: 0L) + 1)
        return true
    }

    override suspend fun addTerminalRecord(record: OccurrenceRecord): Boolean {
        // Mirrors the DAO transaction: one answer per occurrence, and a later
        // answer supersedes a MISSED note for the same occurrence.
        val answered = records.any {
            it.reminderId == record.reminderId &&
                it.occurrenceAt == record.occurrenceAt &&
                it.status.resolvesOccurrence
        }
        if (answered) return false
        if (!addRecord(record)) return false
        records.removeAll {
            it.reminderId == record.reminderId &&
                it.occurrenceAt == record.occurrenceAt &&
                it.status == OccurrenceStatus.MISSED
        }
        return true
    }

    override suspend fun removeRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus) {
        records.removeAll {
            it.reminderId == reminderId && it.occurrenceAt == occurrenceAt && it.status == status
        }
    }

    override suspend fun hasRecord(reminderId: Long, occurrenceAt: Instant, status: OccurrenceStatus): Boolean =
        records.any {
            it.reminderId == reminderId && it.occurrenceAt == occurrenceAt && it.status == status
        }

    override suspend fun pruneRecordsBefore(before: Instant) {
        records.removeAll { it.recordedAt.isBefore(before) && it.occurrenceAt.isBefore(before) }
    }

    override suspend fun pruneCompletedOnceBefore(before: Instant): Int {
        val ids = store.value.values.filter {
            it.completedAt != null && !it.schedule.isRecurring && it.completedAt!!.isBefore(before)
        }.map { it.id }
        ids.forEach { id ->
            store.value = store.value - id
            records.removeAll { it.reminderId == id }
        }
        return ids.size
    }

    private fun mutate(id: Long, transform: (Reminder) -> Reminder) {
        store.value[id]?.let { store.value = store.value + (id to transform(it)) }
    }
}

internal class FakeAlarmGateway : AlarmGateway {
    val scheduled = mutableMapOf<Long, Instant>()
    var exactAllowed = true

    override fun schedule(reminderId: Long, at: Instant) {
        scheduled[reminderId] = at
    }

    override fun cancel(reminderId: Long) {
        scheduled.remove(reminderId)
    }

    override fun canScheduleExact(): Boolean = exactAllowed
}

internal class FakeNotifications : ReminderNotifications {
    val alarms = mutableListOf<Pair<Long, Instant>>()
    val dismissed = mutableListOf<Long>()

    override fun startAlarm(reminder: Reminder, occurrenceAt: Instant) {
        alarms += reminder.id to occurrenceAt
    }

    override fun dismiss(reminderId: Long) {
        dismissed += reminderId
    }
}
