package com.bal.reminders.ui.templates

import com.bal.reminders.R
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Schedule
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ready-made shortcuts for the most common Arabic reminders. Each opens the
 * editor pre-filled — one tap away from saving.
 */
data class ReminderTemplate(
    val id: String,
    val titleRes: Int,
    val category: Category,
    val schedule: (today: LocalDate) -> Schedule,
)

val Templates: List<ReminderTemplate> = listOf(
    ReminderTemplate("work", R.string.template_work, Category.WORK) {
        Schedule.Daily(LocalTime.of(7, 45))
    },
    ReminderTemplate("medicine", R.string.template_medicine, Category.HEALTH) {
        Schedule.Daily(LocalTime.of(8, 0))
    },
    ReminderTemplate("bill", R.string.template_bill, Category.BILLS) {
        Schedule.Monthly(27, LocalTime.of(9, 0))
    },
    ReminderTemplate("water", R.string.template_water, Category.HEALTH) {
        Schedule.Daily(LocalTime.of(10, 0))
    },
    ReminderTemplate("meeting", R.string.template_meeting, Category.WORK) { today ->
        Schedule.Once(today.plusDays(1), LocalTime.of(10, 0))
    },
    ReminderTemplate("study", R.string.template_study, Category.STUDY) {
        Schedule.Daily(LocalTime.of(20, 0))
    },
    ReminderTemplate("call", R.string.template_call, Category.FAMILY) { today ->
        Schedule.Once(today, LocalTime.of(19, 0))
    },
)
