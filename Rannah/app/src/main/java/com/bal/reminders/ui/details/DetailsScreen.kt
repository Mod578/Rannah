package com.bal.reminders.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.components.SnoozeSheet
import com.bal.reminders.ui.components.color
import com.bal.reminders.ui.components.icon
import com.bal.reminders.ui.components.labelRes
import com.bal.reminders.ui.theme.BalTheme
import com.bal.reminders.ui.theme.Space

/**
 * One reminder, split into the two things a person actually means.
 *
 * **«اليوم»** acts on today's occurrence and nothing else: «تم» closes it as
 * done, «تخطي اليوم» closes it without pretending it was, and both leave every
 * future day exactly where it was. **«التذكير»** acts on the whole thing: edit
 * it, pause it, delete it. Two headings, two scopes, which is the entire
 * difference between a repeating reminder and today's ring, taught by layout
 * rather than by explanation.
 *
 * Deletion sits alone at the bottom, past the history, behind its own divider
 * and named for exactly what it removes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DetailsEvent.Closed -> onBack()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        val reminder = state.reminder
        if (!state.loaded) return@Scaffold
        if (reminder == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.details_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val occurrence = state.occurrence
        val recurring = state.recurring
        val completedOnce = occurrence?.phase == ReminderPhase.COMPLETED
        val paused = occurrence?.phase == ReminderPhase.PAUSED

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Space.screen,
                end = Space.screen,
                bottom = Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "summary") {
                SummaryCard(
                    title = reminder.title,
                    schedule = BalFormats.scheduleSummary(context, reminder.schedule),
                    kind = BalFormats.kindLabel(context, reminder.schedule),
                    notes = reminder.notes,
                    status = occurrence?.let { statusLine(context, it) },
                    statusColor = statusColor(occurrence?.phase),
                )
            }

            // ---------------------------------------------------------- اليوم
            val todayOccurrence = state.todayOccurrence
            if (todayOccurrence != null) {
                item(key = "today-title") {
                    ZoneTitle(stringResource(R.string.details_zone_today))
                }
                val answer = state.todayAnswer
                if (answer != null) {
                    item(key = "today-answered") {
                        AnsweredToday(
                            completed = answer == OccurrenceStatus.COMPLETED,
                            onUndo = { viewModel.undoToday(todayOccurrence, answer) },
                        )
                    }
                } else {
                    item(key = "today-actions") {
                        // Stacked, not side by side: at 200% type «تخطي اليوم»
                        // could not fit half a phone's width, and a Material
                        // button clips rather than wraps. Two full-width rows
                        // cost one line of screen and never truncate an action.
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Button(
                                onClick = { viewModel.complete(todayOccurrence) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                                Spacer(Modifier.width(Space.xs))
                                Text(stringResource(R.string.action_done))
                            }
                            // «تخطي اليوم» exists only where it can mean
                            // something: a reminder that has a tomorrow.
                            if (state.canSkip) {
                                OutlinedButton(
                                    onClick = { viewModel.skipToday(todayOccurrence) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                                    Spacer(Modifier.width(Space.xs))
                                    Text(stringResource(R.string.action_skip_today))
                                }
                            }
                            // A live postponement can be moved or taken back
                            // here: the two things the ringing screen has no
                            // room for, offered where there is daylight and space.
                            if (state.snoozed) {
                                OutlinedButton(
                                    onClick = viewModel::openSnoozeOptions,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.Rounded.Snooze, contentDescription = null)
                                    Spacer(Modifier.width(Space.xs))
                                    Text(stringResource(R.string.action_change_snooze))
                                }
                                OutlinedButton(
                                    onClick = viewModel::cancelSnooze,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.Rounded.AlarmOff, contentDescription = null)
                                    Spacer(Modifier.width(Space.xs))
                                    Text(stringResource(R.string.action_cancel_snooze))
                                }
                            }
                        }
                    }
                    item(key = "today-note") {
                        Hint(
                            stringResource(
                                when {
                                    state.snoozed -> R.string.details_snooze_note
                                    recurring -> R.string.details_today_note
                                    else -> R.string.details_once_note
                                },
                            ),
                        )
                    }
                }
            }

            // -------------------------------------------------------- التذكير
            if (!completedOnce) {
                item(key = "reminder-title") {
                    ZoneTitle(stringResource(R.string.details_zone_reminder))
                }
                item(key = "manage") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        OutlinedButton(
                            onClick = { onEdit(reminder.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                            Spacer(Modifier.width(Space.xs))
                            Text(stringResource(R.string.action_edit))
                        }
                        // Pausing a one-time reminder is a worse answer than the
                        // obvious one: it has a single date, and moving that date
                        // is «تعديل». Offering "pause" there invited a state whose
                        // only exit was a second, unrelated action.
                        if (recurring) {
                            OutlinedButton(
                                onClick = { viewModel.setEnabled(paused) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(
                                    if (paused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(Space.xs))
                                Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause))
                            }
                        }
                    }
                }
                if (recurring && !paused) {
                    item(key = "pause-note") {
                        Hint(stringResource(R.string.details_pause_note))
                    }
                }
            }

            if (state.records.isNotEmpty()) {
                item(key = "history-title") { SectionTitle(stringResource(R.string.details_history)) }
                items(state.records.take(HISTORY_LIMIT), key = { it.id }) { record ->
                    RecordRow(
                        status = record.status,
                        text = BalFormats.dateTime(context, record.occurrenceAt),
                    )
                }
            }

            item(key = "delete") {
                Column {
                    Spacer(Modifier.height(Space.lg))
                    TextButton(
                        // A repeating reminder is asked about; a single one is
                        // deleted at once, with «تراجع» waiting on the list.
                        onClick = { if (recurring) confirmDelete = true else viewModel.delete() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            stringResource(
                                if (recurring) R.string.action_delete_recurring else R.string.action_delete_reminder,
                            ),
                        )
                    }
                    // The scope, stated where it is easiest to get wrong: this
                    // is the one action on the screen that takes the future with
                    // it, and it sits one line below «تخطي اليوم», which does not.
                    if (recurring) Hint(stringResource(R.string.details_delete_note))
                }
            }
        }
    }

    state.snoozeOptions?.let { options ->
        SnoozeSheet(
            limit = options.limit,
            rejected = options.rejected,
            onPick = viewModel::changeSnooze,
            onDismiss = viewModel::dismissSnoozeOptions,
        )
    }

    if (confirmDelete) {
        val title = state.reminder?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private const val HISTORY_LIMIT = 8

/** Title, kind, cadence and state, the whole reminder in one card. */
@Composable
private fun SummaryCard(
    title: String,
    schedule: String,
    kind: String,
    notes: String?,
    status: String?,
    statusColor: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md + Space.xs), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            // The kind, said plainly and first: «مرة واحدة», «يومي», «شهري».
            Text(
                kind,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                schedule,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status != null) {
                Text(status, style = MaterialTheme.typography.titleSmall, color = statusColor)
            }
            if (notes != null) {
                Text(
                    notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ZoneTitle(text: String) {
    Column {
        Spacer(Modifier.height(Space.sm))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Space.xs),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.xs),
    )
}

/** Today is already answered: say which answer, and offer to take it back. */
@Composable
private fun AnsweredToday(completed: Boolean, onUndo: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Space.md, end = Space.sm, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                if (completed) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Redo,
                contentDescription = null,
                tint = if (completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                stringResource(
                    if (completed) R.string.details_today_done else R.string.details_today_skipped,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    stringResource(
                        if (completed) R.string.action_undo_complete else R.string.action_undo_skip,
                    ),
                )
            }
        }
    }
}

/** The one sentence that says where this reminder stands right now. */
private fun statusLine(context: android.content.Context, occurrence: ReminderOccurrence): String? {
    val at = occurrence.displayAt
    return when (occurrence.phase) {
        ReminderPhase.SNOOZED ->
            at?.let { context.getString(R.string.details_snoozed_until, BalFormats.dateTime(context, it)) }
        ReminderPhase.NEEDS_CONFIRMATION ->
            at?.let { context.getString(R.string.state_waiting_at, BalFormats.time(context, it)) }
                ?: context.getString(R.string.state_waiting)
        ReminderPhase.OVERDUE ->
            at?.let { context.getString(R.string.details_overdue_at, BalFormats.dateTime(context, it)) }
                ?: context.getString(R.string.state_waiting)
        ReminderPhase.UPCOMING ->
            at?.let { context.getString(R.string.details_next_at, BalFormats.dateTime(context, it)) }
        ReminderPhase.COMPLETED -> {
            val state = context.getString(R.string.status_completed)
            at?.let { "$state · ${BalFormats.dateTime(context, it)}" } ?: state
        }
        ReminderPhase.PAUSED -> {
            val next = at?.let { BalFormats.dateTime(context, it) }
            if (next != null) {
                context.getString(R.string.details_paused_resumes, next)
            } else {
                context.getString(R.string.state_paused)
            }
        }
    }
}

/**
 * The phase line's colour, from the status roles: the same green for «أُنجزت»
 * and the same red for «متأخرة» that the history rows and the list use.
 */
@Composable
private fun statusColor(phase: ReminderPhase?): Color = when (phase) {
    ReminderPhase.NEEDS_CONFIRMATION, ReminderPhase.UPCOMING -> MaterialTheme.colorScheme.primary
    ReminderPhase.OVERDUE -> BalTheme.status.overdue
    ReminderPhase.SNOOZED -> BalTheme.status.snoozed
    ReminderPhase.COMPLETED -> BalTheme.status.done
    ReminderPhase.PAUSED -> BalTheme.status.paused
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun RecordRow(status: OccurrenceStatus, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(status.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = status.color,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
