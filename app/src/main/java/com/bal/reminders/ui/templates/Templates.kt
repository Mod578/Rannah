package com.bal.reminders.ui.templates

import com.bal.reminders.R
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A starting point for a real Arabic obligation, not just a title.
 *
 * A template carries the whole shape of the reminder: how it alerts, whether
 * losing track of it actually matters, and what finishing it is called. Those
 * are exactly the decisions a person setting up «بصمة الدوام» would otherwise
 * have to assemble by hand out of six separate controls.
 *
 * Every value stays editable afterwards. A template is a suggestion رَنّة is
 * willing to defend, never a lock.
 */
data class ReminderTemplate(
    val id: String,
    val titleRes: Int,
    /** One line telling the user what picking this will set up for them. */
    val summaryRes: Int,
    val category: Category,
    val schedule: (today: LocalDate) -> Schedule,
    val alertMode: AlertMode = AlertMode.STANDARD,
    /**
     * Opt-in follow-up, chosen per template because it fits the task, never
     * because it is on by default. It belongs to obligations where hearing the
     * alert and doing the thing genuinely come apart.
     */
    val followUntilComplete: Boolean = false,
    /** The verb of finishing this task, e.g. «سجلت البصمة». */
    val completionLabelRes: Int? = null,
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val followUpIntervalMinutes: Int = Reminder.DEFAULT_FOLLOW_UP_INTERVAL_MINUTES,
    val followUpMaxRepeats: Int = Reminder.DEFAULT_FOLLOW_UP_MAX_REPEATS,
    val alarmRepeatIfIgnored: Boolean = false,
)

/** The Saudi working week, offered as an editable default and nothing more. */
val SaudiWorkdays: Set<DayOfWeek> = setOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
)

val Templates: List<ReminderTemplate> = listOf(
    // Stopping the alarm is not clocking in. This is the case the follow-up
    // exists for, so the template turns it on and says so.
    ReminderTemplate(
        id = "work",
        titleRes = R.string.template_work,
        summaryRes = R.string.template_work_summary,
        category = Category.WORK,
        schedule = { Schedule.Weekly(SaudiWorkdays, LocalTime.of(7, 45)) },
        alertMode = AlertMode.ALARM,
        followUntilComplete = true,
        completionLabelRes = R.string.completion_label_work,
        snoozeMinutes = 5,
        followUpIntervalMinutes = 5,
        followUpMaxRepeats = 3,
        alarmRepeatIfIgnored = true,
    ),
    ReminderTemplate(
        id = "medicine",
        titleRes = R.string.template_medicine,
        summaryRes = R.string.template_medicine_summary,
        category = Category.HEALTH,
        schedule = { Schedule.Daily(LocalTime.of(8, 0)) },
        alertMode = AlertMode.ALARM,
        followUntilComplete = true,
        completionLabelRes = R.string.completion_label_medicine,
        snoozeMinutes = 10,
        followUpIntervalMinutes = 10,
        followUpMaxRepeats = 3,
        alarmRepeatIfIgnored = true,
    ),
    // A bill is a deadline, not an interruption: a normal notification, and the
    // date is the user's to state in whichever calendar they think in.
    ReminderTemplate(
        id = "bill",
        titleRes = R.string.template_bill,
        summaryRes = R.string.template_bill_summary,
        category = Category.BILLS,
        schedule = { Schedule.Monthly(27, LocalTime.of(9, 0)) },
        followUntilComplete = true,
        completionLabelRes = R.string.completion_label_bill,
        followUpIntervalMinutes = 30,
        followUpMaxRepeats = 2,
    ),
    // Scheduled in Hijri and kept in Hijri, because that is how the occasion
    // itself is dated.
    ReminderTemplate(
        id = "hijri_event",
        titleRes = R.string.template_hijri_event,
        summaryRes = R.string.template_hijri_event_summary,
        category = Category.PERSONAL,
        schedule = { Schedule.HijriYearly(9, 1, LocalTime.of(9, 0)) },
        completionLabelRes = null,
    ),
    ReminderTemplate(
        id = "call",
        titleRes = R.string.template_call,
        summaryRes = R.string.template_call_summary,
        category = Category.FAMILY,
        schedule = { today -> Schedule.Once(today, LocalTime.of(19, 0)) },
        completionLabelRes = R.string.completion_label_call,
    ),
    ReminderTemplate(
        id = "water",
        titleRes = R.string.template_water,
        summaryRes = R.string.template_water_summary,
        category = Category.HEALTH,
        schedule = { Schedule.Daily(LocalTime.of(10, 0)) },
        completionLabelRes = R.string.completion_label_water,
    ),
    ReminderTemplate(
        id = "meeting",
        titleRes = R.string.template_meeting,
        summaryRes = R.string.template_meeting_summary,
        category = Category.WORK,
        schedule = { today -> Schedule.Once(today.plusDays(1), LocalTime.of(10, 0)) },
        completionLabelRes = R.string.completion_label_meeting,
    ),
    ReminderTemplate(
        id = "study",
        titleRes = R.string.template_study,
        summaryRes = R.string.template_study_summary,
        category = Category.STUDY,
        schedule = { Schedule.Daily(LocalTime.of(20, 0)) },
    ),
)

/**
 * The completion phrases رَنّة is willing to put in the user's mouth. They are
 * fixed, reviewed strings tied to a known task, which is what makes it safe to
 * turn them into a question («هل سجلت البصمة؟») and to record the answer as a
 * claim that the task was done. Nothing here is inferred from free text.
 */
val CompletionLabelChoices: List<Int> = listOf(
    R.string.completion_label_work,
    R.string.completion_label_medicine,
    R.string.completion_label_bill,
    R.string.completion_label_call,
    R.string.completion_label_water,
    R.string.completion_label_meeting,
)
