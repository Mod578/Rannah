package com.bal.reminders.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.format.BalFormats

/** The four questions the editor asks, each behind one focused sheet. */
enum class EditorSheet { WHAT, WHEN, ALERT, FOLLOW, ADVANCED }

/**
 * One decision, stated as a sentence the user can read at a glance.
 *
 * The editor is a list of four of these rather than a wall of controls: the
 * screen answers "what have I set up?" without being touched, and the controls
 * for changing any one answer live in that answer's own sheet.
 */
@Composable
fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            // Comfortably past the 48dp minimum, and it grows with the text
            // instead of clipping it at large font scales.
            .heightIn(min = 64.dp)
            // One target, read as one sentence, instead of three fragments.
            .clearAndSetSemantics {
                contentDescription = "$label: $value"
                role = Role.Button
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (placeholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

// ------------------------------------------------- ما الذي يقوله كل سطر

@Composable
fun whatSummary(state: EditorState): String =
    state.title.trim().ifBlank { stringResource(R.string.editor_row_what_empty) }

/** «أيام العمل، الساعة ٩:٠٠ صباحًا», straight from the schedule that will be saved. */
@Composable
fun whenSummary(state: EditorState): String {
    val context = LocalContext.current
    val schedule = state.buildSchedule()
        ?: return stringResource(R.string.editor_row_when_incomplete)
    return BalFormats.scheduleSummary(context, schedule)
}

@Composable
fun alertSummary(state: EditorState): String = stringResource(
    if (state.alertMode == AlertMode.ALARM) {
        R.string.alert_mode_alarm
    } else {
        R.string.alert_mode_standard
    },
)

@Composable
fun followSummary(state: EditorState): String = stringResource(
    if (state.followUntilComplete) R.string.editor_follow_on else R.string.editor_follow_off,
)

@Composable
fun sheetIcon(sheet: EditorSheet): ImageVector = when (sheet) {
    EditorSheet.WHAT -> Icons.Rounded.EditNote
    EditorSheet.WHEN -> Icons.Rounded.CalendarMonth
    EditorSheet.ALERT -> Icons.Rounded.NotificationsNone
    EditorSheet.FOLLOW -> Icons.Rounded.TaskAlt
    EditorSheet.ADVANCED -> Icons.Rounded.Alarm
}
