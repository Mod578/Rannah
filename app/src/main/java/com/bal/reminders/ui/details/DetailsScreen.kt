package com.bal.reminders.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Priority
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.CategoryBadge
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.components.labelRes
import java.time.Instant

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
    var confirmEndSeries by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DetailsEvent.Closed -> onBack()
                is DetailsEvent.UndoableDone -> {
                    val message = when (event.kind) {
                        UndoKind.COMPLETED -> context.getString(R.string.undo_completed_message, event.title)
                        UndoKind.SKIPPED -> context.getString(R.string.undo_skipped_message, event.title)
                        UndoKind.SERIES_ENDED ->
                            context.getString(R.string.undo_series_ended_message, event.title)
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.undo)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
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
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryBadge(reminder.category, size = 48)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    reminder.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(reminder.category.labelRes) +
                                        if (reminder.priority == Priority.HIGH) {
                                            " · " + stringResource(R.string.details_high_priority)
                                        } else {
                                            ""
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = viewModel::setEnabled,
                            )
                        }
                        Text(
                            BalFormats.scheduleSummary(context, reminder.schedule),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // نمط التنبيه ظاهر دائمًا حتى يعرف المستخدم ماذا سيحدث.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.details_alert_mode,
                                    stringResource(
                                        if (reminder.alertMode == AlertMode.ALARM) {
                                            R.string.alert_mode_alarm
                                        } else {
                                            R.string.alert_mode_standard
                                        },
                                    ),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (reminder.schedule.calendar == CalendarSystem.HIJRI) {
                            Text(
                                stringResource(R.string.details_calendar_basis_hijri),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (reminder.isDone && reminder.schedule.isRecurring) {
                            Text(
                                stringResource(R.string.details_series_ended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        reminder.snoozedUntil?.takeIf { it.isAfter(Instant.now()) }?.let { until ->
                            Text(
                                stringResource(
                                    R.string.details_snoozed_until,
                                    BalFormats.dateTime(context, until),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        reminder.nextTriggerAt?.let { next ->
                            Text(
                                stringResource(
                                    R.string.details_next_at,
                                    BalFormats.dateTime(context, next),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        reminder.notes?.let { notes ->
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // «تم» أساسية وكبيرة؛ «تأجيل» متاحة دون أن تطغى.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::completeNow,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.action_complete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::snooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.small,
                        enabled = reminder.nextTriggerAt != null,
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_snooze))
                    }
                }
            }

            if (reminder.schedule.isRecurring && !reminder.isDone) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::skipOnce,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = MaterialTheme.shapes.small,
                            enabled = reminder.enabled,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_skip_once))
                        }
                        OutlinedButton(
                            onClick = { confirmEndSeries = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(
                                Icons.Rounded.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.action_end_series),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onEdit(reminder.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_edit))
                    }
                    OutlinedButton(
                        onClick = viewModel::duplicate,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_duplicate))
                    }
                }
            }

            if (state.records.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.details_history))
                }
                items(state.records, key = { it.id }) { record ->
                    RecordRow(
                        status = record.status,
                        text = BalFormats.dateTime(context, record.occurrenceAt),
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
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

    // إنهاء التكرار إجراء شامل للسلسلة: تأكيد صريح، ثم تراجع متاح مباشرة.
    if (confirmEndSeries) {
        AlertDialog(
            onDismissRequest = { confirmEndSeries = false },
            title = { Text(stringResource(R.string.end_series_confirm_title)) },
            text = { Text(stringResource(R.string.end_series_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEndSeries = false
                        viewModel.endSeries()
                    },
                ) {
                    Text(
                        stringResource(R.string.action_end_series),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndSeries = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun RecordRow(status: OccurrenceStatus, text: String) {
    val (icon, labelRes, tint) = when (status) {
        OccurrenceStatus.COMPLETED ->
            Triple(Icons.Rounded.Check, R.string.status_completed, MaterialTheme.colorScheme.secondary)
        OccurrenceStatus.SKIPPED ->
            Triple(Icons.AutoMirrored.Rounded.Redo, R.string.status_skipped, MaterialTheme.colorScheme.tertiary)
        OccurrenceStatus.MISSED ->
            Triple(Icons.Rounded.EventBusy, R.string.status_missed, MaterialTheme.colorScheme.error)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
