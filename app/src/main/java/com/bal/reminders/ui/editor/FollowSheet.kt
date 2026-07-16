package com.bal.reminders.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.templates.CompletionLabelChoices

/**
 * «ماذا يحدث بعد التنبيه؟»
 *
 * The one sheet in the editor that is worth reading rather than skimming: it
 * is where the user decides whether hearing the alert is the end of the story.
 * It states the whole bargain up front, including the part where رَنّة gives
 * up, because a follow-up the user does not understand is a follow-up they
 * will turn off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowSheet(state: EditorState, viewModel: EditorViewModel) {
    val context = LocalContext.current

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (state.followUntilComplete) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            // The whole card toggles, so the switch is never a lone unlabelled
            // target the user has to aim at.
            .toggleable(
                value = state.followUntilComplete,
                role = Role.Switch,
                onValueChange = viewModel::setFollowUntilComplete,
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.editor_follow_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentFor(state.followUntilComplete),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.followUntilComplete, onCheckedChange = null)
            }
            Text(
                stringResource(R.string.editor_follow_body),
                style = MaterialTheme.typography.bodyMedium,
                color = contentFor(state.followUntilComplete),
            )
        }
    }

    AnimatedVisibility(visible = state.followUntilComplete) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.editor_follow_interval))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reminder.FOLLOW_UP_INTERVAL_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = state.followUpIntervalMinutes == minutes,
                        onClick = { viewModel.setFollowUpInterval(minutes) },
                        label = {
                            Text(
                                context.resources.getQuantityString(
                                    R.plurals.duration_minutes,
                                    minutes,
                                    BalFormats.arabicDigits(minutes.toString()),
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            SectionTitle(stringResource(R.string.editor_follow_repeats))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reminder.FOLLOW_UP_REPEAT_CHOICES.forEach { times ->
                    FilterChip(
                        selected = state.followUpMaxRepeats == times,
                        onClick = { viewModel.setFollowUpMaxRepeats(times) },
                        label = {
                            Text(
                                context.resources.getQuantityString(
                                    R.plurals.editor_follow_times,
                                    times,
                                    times,
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            // The promise, in full: how often, for how long, and what happens
            // at the end. No follow-up runs longer than this sentence says.
            Text(
                stringResource(
                    R.string.editor_follow_window,
                    context.resources.getQuantityString(
                        R.plurals.editor_follow_times,
                        state.followUpMaxRepeats,
                        state.followUpMaxRepeats,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.duration_minutes,
                        state.followUpWindowMinutes,
                        BalFormats.arabicDigits(state.followUpWindowMinutes.toString()),
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CompletionLabelField(state, viewModel)
        }
    }
}

/**
 * The completion phrase. Offered as a set of vetted choices, and editable in
 * the user's own words, but never guessed at from the title: رَنّة will put
 * this sentence in the user's mouth («هل سجلت البصمة؟») and record their yes
 * as a claim that a real thing happened, so it has to be a phrase they chose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletionLabelField(state: EditorState, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val label = state.completionLabel?.takeIf { it.isNotBlank() }

    SectionTitle(stringResource(R.string.editor_completion_label))
    Text(
        stringResource(R.string.editor_completion_label_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompletionLabelChoices.forEach { res ->
            val text = stringResource(res)
            FilterChip(
                selected = label == text,
                onClick = { viewModel.setCompletionLabel(if (label == text) null else text) },
                label = { Text(text) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    OutlinedTextField(
        value = state.completionLabel.orEmpty(),
        onValueChange = viewModel::setCompletionLabel,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.editor_completion_label_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
    )
    Text(
        stringResource(
            R.string.editor_completion_preview,
            label ?: context.getString(R.string.editor_completion_default),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun contentFor(active: Boolean) = if (active) {
    MaterialTheme.colorScheme.onPrimaryContainer
} else {
    MaterialTheme.colorScheme.onSurface
}
