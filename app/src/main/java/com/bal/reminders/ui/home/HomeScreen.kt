package com.bal.reminders.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bal.reminders.R
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.EmptyState
import com.bal.reminders.ui.components.ReminderCard
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.permissions.Permissions
import com.bal.reminders.ui.templates.Templates
import java.time.ZoneId
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenDetails: (Long) -> Unit,
    onOpenEditorDraft: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateState by viewModel.dateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenEditor -> onOpenEditorDraft(event.route)
                HomeEvent.Saved -> Unit
                is HomeEvent.UndoableComplete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.undo_completed_message, event.title),
                        actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.undo)
                    }
                }

                is HomeEvent.UndoableSkip -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.undo_skipped_message, event.title),
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

    // Permission health check, refreshed whenever the screen resumes.
    var permissions by remember { mutableStateOf(Permissions.status(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = Permissions.status(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Minute tick so relative times stay honest while the screen is open.
    val nowTick by produceState(viewModel.clockNow()) {
        while (true) {
            delay(30_000)
            value = viewModel.clockNow()
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Greeting(dateState, nowTick)
        }

        item {
            QuickInput(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                onSubmit = viewModel::onSubmitInput,
                parseFailed = state.parseFailed,
            )
        }

        state.preview?.let { preview ->
            item {
                PreviewCard(
                    text = BalFormats.interpretation(context, preview.title, preview.schedule),
                    saving = state.saving,
                    onConfirm = viewModel::onConfirmPreview,
                    onEdit = viewModel::onEditPreview,
                    onDismiss = viewModel::onDismissPreview,
                )
            }
        }

        if (!permissions.essentialsGranted) {
            item {
                PermissionsBanner(onClick = onOpenPermissions)
            }
        }

        // Above everything: the tasks that already alerted and are still
        // unanswered. A plan can wait; a half-missed obligation cannot.
        if (state.pending.isNotEmpty()) {
            items(state.pending, key = { "pending-${it.reminder.id}-${it.occurrenceAt.toEpochMilli()}" }) { item ->
                PendingCard(
                    item = item,
                    onConfirm = { viewModel.confirmPending(item) },
                    onSnooze = { viewModel.snoozePending(item) },
                    onSkip = { viewModel.skipPending(item) },
                )
            }
        }

        if (state.missedToday.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_missed_today)) }
            items(state.missedToday, key = { "missed-${it.reminder.id}-${it.occurrenceAt.toEpochMilli()}" }) { item ->
                ReminderCard(
                    reminder = item.reminder,
                    subtitle = stringResource(
                        R.string.home_missed_at,
                        BalFormats.time(
                            context,
                            item.occurrenceAt.atZone(ZoneId.systemDefault()).toLocalTime(),
                        ),
                    ),
                    subtitleEmphasis = true,
                    onClick = { onOpenDetails(item.reminder.id) },
                    onComplete = { viewModel.complete(item.reminder) },
                )
            }
        }

        state.next?.let { next ->
            item {
                SectionTitle(stringResource(R.string.home_next_reminder))
                Card(
                    onClick = { onOpenDetails(next.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = next.nextTriggerAt?.let {
                                BalFormats.relative(context, it, nowTick)
                            }.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = next.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = BalFormats.scheduleSummary(context, next.schedule),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        if (state.overdue.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_overdue)) }
            items(state.overdue, key = { "overdue-${it.id}" }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    subtitle = stringResource(
                        R.string.home_overdue_since,
                        BalFormats.scheduleSummary(context, reminder.schedule),
                    ),
                    subtitleEmphasis = true,
                    onClick = { onOpenDetails(reminder.id) },
                    onComplete = { viewModel.complete(reminder) },
                )
            }
        }

        if (state.today.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_today)) }
            items(state.today, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    subtitle = reminder.nextTriggerAt?.let {
                        BalFormats.time(
                            context,
                            it.atZone(ZoneId.systemDefault()).toLocalTime(),
                        )
                    }.orEmpty(),
                    onClick = { onOpenDetails(reminder.id) },
                    onComplete = { viewModel.complete(reminder) },
                )
            }
        }

        if (!state.hasAnyReminder) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.home_templates))
            // Wrapping, not a horizontal scroll: the last template used to sit
            // half under the floating button where nobody would find it.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Templates.forEach { template ->
                    AssistChip(
                        onClick = { onOpenTemplate(template.id) },
                        label = { Text(stringResource(template.titleRes)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp),
    )
    }
}

@Composable
private fun Greeting(dateState: DateState, now: java.time.Instant) {
    val zoned = remember(now) { now.atZone(ZoneId.systemDefault()) }
    val greeting = stringResource(
        if (zoned.hour in 4..16) R.string.greeting_morning else R.string.greeting_evening,
    )
    val (primaryDate, secondaryDate) = remember(zoned.toLocalDate(), dateState) {
        BalFormats.dateLines(
            zoned.toLocalDate(),
            dateState.display,
            dateState.hijriAdjustmentDays,
        )
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppMark(
                stroke = MaterialTheme.colorScheme.primary,
                dot = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = primaryDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (secondaryDate != null) {
            Text(
                text = secondaryDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    parseFailed: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    stringResource(R.string.home_input_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                IconButton(onClick = onSubmit, enabled = value.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.home_input_submit),
                        tint = if (value.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            singleLine = true,
        )
        if (parseFailed) {
            Text(
                text = stringResource(R.string.home_parse_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun PreviewCard(
    text: String,
    saving: Boolean,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onConfirm, enabled = !saving) {
                    Text(stringResource(R.string.parse_confirm))
                }
                TextButton(onClick = onEdit, enabled = !saving) {
                    Text(stringResource(R.string.parse_edit))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun PermissionsBanner(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    stringResource(R.string.home_permissions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.home_permissions_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
