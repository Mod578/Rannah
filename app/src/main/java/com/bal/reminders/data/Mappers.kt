package com.bal.reminders.data

import com.bal.reminders.data.db.OccurrenceRecordEntity
import com.bal.reminders.data.db.PendingConfirmationEntity
import com.bal.reminders.data.db.ReminderEntity
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.PendingConfirmation
import com.bal.reminders.domain.model.Priority
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private const val TYPE_ONCE = "once"
private const val TYPE_DAILY = "daily"
private const val TYPE_WEEKLY = "weekly"
private const val TYPE_MONTHLY = "monthly"
private const val TYPE_YEARLY = "yearly"

/** v1 encoding, kept readable in case a row predates the 1→2 migration. */
private const val TYPE_HIJRI_MONTHLY_LEGACY = "hijri_monthly"

fun ReminderEntity.toDomain(): Reminder {
    val time = LocalTime.ofSecondOfDay(timeMinutes * 60L)
    val hijri = CalendarSystem.fromId(calendar) == CalendarSystem.HIJRI
    val schedule = when (recurrenceType) {
        TYPE_DAILY -> Schedule.Daily(time)
        TYPE_WEEKLY -> Schedule.Weekly(daysOfWeekFromMask(daysOfWeek), time)
        TYPE_MONTHLY ->
            if (hijri) {
                Schedule.HijriMonthly((dayOfMonth ?: 1).coerceIn(1, 30), time)
            } else {
                Schedule.Monthly(dayOfMonth ?: 1, time)
            }
        TYPE_HIJRI_MONTHLY_LEGACY -> Schedule.HijriMonthly((dayOfMonth ?: 1).coerceIn(1, 30), time)
        TYPE_YEARLY ->
            if (hijri) {
                Schedule.HijriYearly((month ?: 1).coerceIn(1, 12), (dayOfMonth ?: 1).coerceIn(1, 30), time)
            } else {
                Schedule.Yearly((month ?: 1).coerceIn(1, 12), (dayOfMonth ?: 1).coerceIn(1, 31), time)
            }
        else ->
            if (hijri && year != null) {
                Schedule.OnceHijri(
                    year = year,
                    month = (month ?: 1).coerceIn(1, 12),
                    day = (dayOfMonth ?: 1).coerceIn(1, 30),
                    time = time,
                )
            } else {
                Schedule.Once(date?.let(LocalDate::parse) ?: LocalDate.now(), time)
            }
    }
    return Reminder(
        id = id,
        title = title,
        notes = notes,
        category = Category.fromId(categoryId),
        priority = if (priority == 1) Priority.HIGH else Priority.NORMAL,
        schedule = schedule,
        enabled = enabled,
        alertMode = AlertMode.fromId(alertMode),
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        snoozeMinutes = snoozeMinutes,
        ringtoneUri = ringtoneUri,
        alarmTimeoutMinutes = alarmTimeoutMinutes,
        alarmGradualVolume = alarmGradualVolume,
        alarmRepeatIfIgnored = alarmRepeatIfIgnored,
        stopMarksCompleted = stopMarksCompleted,
        followUntilComplete = followUntilComplete,
        followUpIntervalMinutes = followUpIntervalMinutes,
        followUpMaxRepeats = followUpMaxRepeats,
        completionLabel = completionLabel?.takeIf { it.isNotBlank() },
        snoozedUntil = snoozedUntilMillis?.let(Instant::ofEpochMilli),
        nextTriggerAt = nextTriggerAtMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        completedAt = completedAtMillis?.let(Instant::ofEpochMilli),
    )
}

fun Reminder.toEntity(): ReminderEntity {
    val s = schedule
    return ReminderEntity(
        id = id,
        title = title,
        notes = notes,
        categoryId = category.id,
        priority = if (priority == Priority.HIGH) 1 else 0,
        recurrenceType = when (s) {
            is Schedule.Once, is Schedule.OnceHijri -> TYPE_ONCE
            is Schedule.Daily -> TYPE_DAILY
            is Schedule.Weekly -> TYPE_WEEKLY
            is Schedule.Monthly, is Schedule.HijriMonthly -> TYPE_MONTHLY
            is Schedule.Yearly, is Schedule.HijriYearly -> TYPE_YEARLY
        },
        calendar = s.calendar.id,
        timeMinutes = s.time.hour * 60 + s.time.minute,
        date = (s as? Schedule.Once)?.date?.toString(),
        year = (s as? Schedule.OnceHijri)?.year,
        month = when (s) {
            is Schedule.OnceHijri -> s.month
            is Schedule.Yearly -> s.month
            is Schedule.HijriYearly -> s.month
            else -> null
        },
        daysOfWeek = (s as? Schedule.Weekly)?.days?.toMask() ?: 0,
        dayOfMonth = when (s) {
            is Schedule.Monthly -> s.dayOfMonth
            is Schedule.HijriMonthly -> s.dayOfMonth
            is Schedule.OnceHijri -> s.day
            is Schedule.Yearly -> s.day
            is Schedule.HijriYearly -> s.day
            else -> null
        },
        enabled = enabled,
        alertMode = alertMode.id,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        snoozeMinutes = snoozeMinutes,
        ringtoneUri = ringtoneUri,
        alarmTimeoutMinutes = alarmTimeoutMinutes,
        alarmGradualVolume = alarmGradualVolume,
        alarmRepeatIfIgnored = alarmRepeatIfIgnored,
        stopMarksCompleted = stopMarksCompleted,
        followUntilComplete = followUntilComplete,
        followUpIntervalMinutes = followUpIntervalMinutes,
        followUpMaxRepeats = followUpMaxRepeats,
        completionLabel = completionLabel?.takeIf { it.isNotBlank() },
        snoozedUntilMillis = snoozedUntil?.toEpochMilli(),
        nextTriggerAtMillis = nextTriggerAt?.toEpochMilli(),
        createdAtMillis = createdAt.toEpochMilli(),
        completedAtMillis = completedAt?.toEpochMilli(),
    )
}

fun OccurrenceRecordEntity.toDomain() = OccurrenceRecord(
    id = id,
    reminderId = reminderId,
    reminderTitle = reminderTitle,
    category = Category.fromId(categoryId),
    occurrenceAt = Instant.ofEpochMilli(occurrenceAtMillis),
    status = OccurrenceStatus.fromId(status),
    recordedAt = Instant.ofEpochMilli(completedAtMillis),
)

fun OccurrenceRecord.toEntity() = OccurrenceRecordEntity(
    id = id,
    reminderId = reminderId,
    reminderTitle = reminderTitle,
    categoryId = category.id,
    occurrenceAtMillis = occurrenceAt.toEpochMilli(),
    completedAtMillis = recordedAt.toEpochMilli(),
    status = status.id,
)

fun PendingConfirmationEntity.toDomain() = PendingConfirmation(
    reminderId = reminderId,
    occurrenceAt = Instant.ofEpochMilli(occurrenceAtMillis),
    since = Instant.ofEpochMilli(sinceMillis),
    nudgesSent = nudgesSent,
    deadlineAt = Instant.ofEpochMilli(deadlineAtMillis),
)

fun PendingConfirmation.toEntity() = PendingConfirmationEntity(
    reminderId = reminderId,
    occurrenceAtMillis = occurrenceAt.toEpochMilli(),
    sinceMillis = since.toEpochMilli(),
    nudgesSent = nudgesSent,
    deadlineAtMillis = deadlineAt.toEpochMilli(),
)

fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

fun daysOfWeekFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
