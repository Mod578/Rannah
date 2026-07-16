package com.bal.reminders.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String?,
    val categoryId: String,
    val priority: Int,
    /** one of: once, daily, weekly, monthly, yearly */
    val recurrenceType: String,
    /** gregorian or hijri — the calendar the once/monthly/yearly dates live in */
    @ColumnInfo(defaultValue = "gregorian") val calendar: String = "gregorian",
    /** trigger time as minutes from midnight */
    val timeMinutes: Int,
    /** ISO local date — Gregorian one-time only */
    val date: String?,
    /** Hijri year — Hijri one-time only */
    val year: Int?,
    /** month 1..12 — yearly and Hijri one-time */
    val month: Int?,
    /** bitmask, bit (isoDayOfWeek - 1) — only for weekly */
    val daysOfWeek: Int,
    /** 1..31 — monthly, yearly and Hijri one-time */
    val dayOfMonth: Int?,
    val enabled: Boolean,
    /** standard or alarm */
    @ColumnInfo(defaultValue = "standard") val alertMode: String = "standard",
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
    val ringtoneUri: String?,
    @ColumnInfo(defaultValue = "3") val alarmTimeoutMinutes: Int = 3,
    @ColumnInfo(defaultValue = "1") val alarmGradualVolume: Boolean = true,
    @ColumnInfo(defaultValue = "0") val alarmRepeatIfIgnored: Boolean = false,
    @ColumnInfo(defaultValue = "0") val followUntilComplete: Boolean = false,
    @ColumnInfo(defaultValue = "5") val followUpIntervalMinutes: Int = 5,
    @ColumnInfo(defaultValue = "3") val followUpMaxRepeats: Int = 3,
    @ColumnInfo(defaultValue = "NULL") val completionLabel: String? = null,
    val snoozedUntilMillis: Long?,
    val nextTriggerAtMillis: Long?,
    val createdAtMillis: Long,
    val completedAtMillis: Long?,
)

/**
 * Occurrence-level log. The table keeps its v1 name ("completions") and column
 * names; [status] widens it to completed/skipped/missed records. The unique
 * index is what makes recording idempotent under duplicate intents.
 */
@Entity(
    tableName = "completions",
    indices = [
        Index("reminderId"),
        Index("completedAtMillis"),
        Index(value = ["reminderId", "occurrenceAtMillis", "status"], unique = true),
    ],
)
data class OccurrenceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reminderId: Long,
    val reminderTitle: String,
    val categoryId: String,
    val occurrenceAtMillis: Long,
    /** When the record was made (v1 name kept for a lossless migration). */
    val completedAtMillis: Long,
    @ColumnInfo(defaultValue = "completed") val status: String = "completed",
)

/**
 * Live «بانتظار تأكيدك» state, one row per unresolved occurrence. Unique on
 * (reminderId, occurrenceAtMillis) so a replayed intent cannot open two
 * follow-ups for the same occurrence.
 */
@Entity(
    tableName = "pending_confirmations",
    indices = [
        Index("reminderId"),
        Index(value = ["reminderId", "occurrenceAtMillis"], unique = true),
    ],
)
data class PendingConfirmationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reminderId: Long,
    val occurrenceAtMillis: Long,
    val sinceMillis: Long,
    val nudgesSent: Int,
    val deadlineAtMillis: Long,
)
