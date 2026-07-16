package com.bal.reminders.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.format.BalFormats
import java.time.ZoneId

/**
 * «بانتظار تأكيدك».
 *
 * Deliberately the loudest thing on the home screen. Everything else there is
 * a plan; this is a task that already alerted and still has not been answered,
 * which makes it the only item the user can still lose today.
 *
 * The primary button says what was actually supposed to happen («نعم، سجلت
 * البصمة») rather than a bare «تم», so tapping it is a specific claim and not
 * a reflex.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingCard(
    item: PendingItem,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = item.reminder.completionLabel?.takeIf { it.isNotBlank() }
    val question = if (label != null) {
        stringResource(R.string.notification_followup_question, label)
    } else {
        stringResource(R.string.notification_followup_question_generic, item.reminder.title)
    }
    val confirmText = label?.let { stringResource(R.string.home_pending_confirm, it) }
        ?: stringResource(R.string.notification_complete)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_pending_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Text(
                question,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(
                    R.string.home_pending_since,
                    BalFormats.time(
                        context,
                        item.occurrenceAt.atZone(ZoneId.systemDefault()).toLocalTime(),
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            // Wraps rather than clipping: these labels are sentences, and they
            // get longer at bigger font scales.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(confirmText)
                }
                TextButton(onClick = onSnooze, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        context.resources.getQuantityString(
                            R.plurals.notification_followup_snooze,
                            item.reminder.followUpIntervalMinutes,
                            item.reminder.followUpIntervalMinutes,
                        ),
                    )
                }
                if (item.reminder.schedule.isRecurring) {
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.notification_skip))
                    }
                }
            }
        }
    }
}
