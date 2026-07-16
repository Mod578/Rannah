package com.bal.reminders.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import java.time.Instant
import java.time.ZoneId

/**
 * What a reminder is doing right now, in words.
 *
 * A card used to say this with an unlabelled switch and a coloured dot, which
 * asks the user to remember a legend. Every state a reminder can be in now has
 * a name on the card itself.
 */
enum class ReminderState { ACTIVE, PAUSED, AWAITING, SNOOZED, OVERDUE, DONE, SERIES_ENDED }

fun reminderStateOf(
    reminder: Reminder,
    now: Instant,
    awaiting: Boolean = false,
): ReminderState = when {
    awaiting -> ReminderState.AWAITING
    !reminder.enabled -> ReminderState.PAUSED
    reminder.isDone && reminder.schedule.isRecurring -> ReminderState.SERIES_ENDED
    reminder.isDone -> ReminderState.DONE
    reminder.snoozedUntil?.isAfter(now) == true -> ReminderState.SNOOZED
    reminder.nextTriggerAt?.isBefore(now) == true -> ReminderState.OVERDUE
    else -> ReminderState.ACTIVE
}

/**
 * The status as a short sentence: «مؤجل حتى ٩:١٠» rather than a bare «مؤجل»,
 * because the useful half of that state is the time.
 */
@Composable
fun reminderStateLabel(reminder: Reminder, state: ReminderState): String {
    val context = LocalContext.current
    return when (state) {
        ReminderState.ACTIVE -> stringResource(R.string.reminder_state_active)
        ReminderState.PAUSED -> stringResource(R.string.reminder_state_paused)
        ReminderState.AWAITING -> stringResource(R.string.reminder_state_awaiting)
        ReminderState.SNOOZED -> {
            val until = reminder.snoozedUntil
            if (until == null) {
                stringResource(R.string.reminder_state_active)
            } else {
                stringResource(
                    R.string.reminder_state_snoozed_until,
                    BalFormats.time(context, until.atZone(ZoneId.systemDefault()).toLocalTime()),
                )
            }
        }
        ReminderState.OVERDUE -> stringResource(R.string.reminder_state_overdue)
        ReminderState.DONE -> stringResource(R.string.reminder_state_done)
        ReminderState.SERIES_ENDED -> stringResource(R.string.reminder_state_series_ended)
    }
}

@Composable
fun reminderStateColor(state: ReminderState): Color = when (state) {
    ReminderState.ACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
    ReminderState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    ReminderState.AWAITING -> MaterialTheme.colorScheme.primary
    ReminderState.SNOOZED -> MaterialTheme.colorScheme.tertiary
    ReminderState.OVERDUE -> MaterialTheme.colorScheme.error
    ReminderState.DONE -> MaterialTheme.colorScheme.secondary
    ReminderState.SERIES_ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A quiet pill; only the states that need attention carry any colour. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
