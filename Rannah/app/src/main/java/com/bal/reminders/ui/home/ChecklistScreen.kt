package com.bal.reminders.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.ChecklistRow
import com.bal.reminders.ui.components.ClosedRow
import com.bal.reminders.ui.components.EmptyState
import com.bal.reminders.ui.components.RowTone
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.permissions.Permissions
import com.bal.reminders.ui.permissions.ReadinessIssue
import com.bal.reminders.ui.permissions.issues
import com.bal.reminders.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * The home screen answers one question — «ما الذي عليّ اليوم؟» — and it answers
 * it with one list.
 *
 * Everything due today sits under «اليوم» in clock order, with what is waiting
 * for an answer first; each row says its own state, so the screen needs three
 * headings instead of six. «قادم» is the days after, «انتهت اليوم» closes over
 * what is already answered, and «متوقفة مؤقتًا» keeps paused reminders reachable
 * without letting them crowd the day.
 */
@Composable
fun ChecklistScreen(
    onOpenDetails: (Long) -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    // The one undo surface. Completing happens here; deleting happens in the
    // details screen, which closes straight after — both arrive through the same
    // channel, so there is exactly one place «تراجع» ever appears. Each offer is
    // taken before the snackbar shows, so it is never replayed.
    LaunchedEffect(Unit) {
        viewModel.undoOffers.collect { offer ->
            if (offer == null) return@collect
            val request = viewModel.takeUndo() ?: return@collect
            val result = snackbarHostState.showSnackbar(
                message = context.getString(request.messageRes, request.subject),
                actionLabel = undoLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.runUndo(request)
        }
    }

    // Permission health, refreshed on resume: the worst blocking issue rides at
    // the top, because "reminders cannot reach you" outranks any single task.
    var permissions by remember { mutableStateOf(Permissions.status(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = Permissions.status(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val blockingIssue = permissions.issues().firstOrNull { it.blocking }

    val nowTick by produceState(viewModel.clockNow()) {
        while (true) {
            delay(30_000)
            value = viewModel.clockNow()
        }
    }

    var closedExpanded by remember { mutableStateOf(false) }

    // Section titles are resolved here: the list scope below is not composable.
    val overdueTitle = stringResource(R.string.home_overdue)
    val todayTitle = stringResource(R.string.home_today)
    val upcomingTitle = stringResource(R.string.home_upcoming)
    val pausedTitle = stringResource(R.string.home_paused)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenEditor,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_reminder)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    top = Space.md,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item { Header(nowTick, onOpenSettings) }

                if (blockingIssue != null) {
                    item { ReadinessBanner(blockingIssue, onOpenPermissions) }
                }

                // Late and unanswered: above the day, dated, and never dropped.
                if (state.overdue.isNotEmpty()) {
                    section(overdueTitle, state.overdue.size)
                    items(
                        state.overdue,
                        key = { "overdue-${it.reminderId}-${it.displayAt?.toEpochMilli() ?: 0}" },
                    ) { item ->
                        OccurrenceRow(
                            item = item,
                            context = context,
                            zone = zone,
                            now = nowTick,
                            onOpenDetails = onOpenDetails,
                            onComplete = { viewModel.complete(item) },
                        )
                    }
                }

                if (state.today.isNotEmpty()) {
                    section(todayTitle, state.today.size)
                    items(
                        state.today,
                        key = { "today-${it.reminderId}-${it.displayAt?.toEpochMilli() ?: 0}" },
                    ) { item ->
                        OccurrenceRow(
                            item = item,
                            context = context,
                            zone = zone,
                            now = nowTick,
                            onOpenDetails = onOpenDetails,
                            onComplete = { viewModel.complete(item) },
                            // «تخطي اليوم» belongs to reminders that have a
                            // tomorrow. Its absence on a one-time row is itself
                            // the lesson: there is nothing left to keep.
                            onSkip = if (item.canSkip) ({ viewModel.skipToday(item) }) else null,
                        )
                    }
                } else if (state.nothingToday) {
                    item(key = "clear") { ClearDayNote() }
                }

                if (state.upcoming.isNotEmpty()) {
                    section(upcomingTitle, state.upcoming.size)
                    items(
                        state.upcoming,
                        key = { "next-${it.reminderId}-${it.displayAt?.toEpochMilli() ?: 0}" },
                    ) { item ->
                        OccurrenceRow(
                            item = item,
                            context = context,
                            zone = zone,
                            now = nowTick,
                            onOpenDetails = onOpenDetails,
                            onComplete = null,
                        )
                    }
                }

                if (state.closed.isNotEmpty()) {
                    item(key = "closed-header") {
                        ClosedHeader(
                            count = state.closed.size,
                            expanded = closedExpanded,
                            onToggle = { closedExpanded = !closedExpanded },
                        )
                    }
                    if (closedExpanded) {
                        items(
                            state.closed,
                            key = { "closed-${it.reminderId}-${it.occurrenceAt.toEpochMilli()}" },
                        ) { item ->
                            ClosedRow(
                                title = item.title,
                                meta = closedMeta(context, item, zone, nowTick),
                                completed = item.status == OccurrenceStatus.COMPLETED,
                                onUndo = { viewModel.undoClosed(item) },
                            )
                        }
                    }
                }

                // Paused reminders keep their place: quiet, at the end, showing
                // the schedule they will return to and a one-tap «استئناف».
                if (state.paused.isNotEmpty()) {
                    section(pausedTitle, state.paused.size)
                    items(state.paused, key = { "paused-${it.reminderId}" }) { item ->
                        ChecklistRow(
                            title = item.title,
                            meta = BalFormats.scheduleSummary(context, item.schedule),
                            kindLabel = BalFormats.kindLabel(context, item.schedule),
                            onClick = { onOpenDetails(item.reminderId) },
                            tone = RowTone.Muted,
                            trailing = {
                                ResumeButton(title = item.title, onClick = { viewModel.resume(item) })
                            },
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
            }
        }
    }
}

private fun LazyListScope.section(title: String, count: Int) {
    item(key = "header-$title") {
        Column {
            Spacer(Modifier.height(Space.sm))
            SectionTitle(title, count)
        }
    }
}

@Composable
private fun OccurrenceRow(
    item: ReminderOccurrence,
    context: android.content.Context,
    zone: ZoneId,
    now: Instant,
    onOpenDetails: (Long) -> Unit,
    onComplete: (() -> Unit)?,
    onSkip: (() -> Unit)? = null,
) {
    ChecklistRow(
        title = item.title,
        meta = metaFor(context, item, zone, now),
        // The kind is always on the row: «مرة واحدة», «يومي», «أيام العمل»,
        // «شهري». Nobody should have to remember what they built, or infer it
        // from an icon.
        kindLabel = BalFormats.kindLabel(context, item.schedule),
        onClick = { onOpenDetails(item.reminderId) },
        tone = when (item.phase) {
            ReminderPhase.NEEDS_CONFIRMATION -> RowTone.Waiting
            ReminderPhase.SNOOZED -> RowTone.Snoozed
            ReminderPhase.OVERDUE -> RowTone.Overdue
            else -> RowTone.Normal
        },
        onComplete = onComplete,
        onSkip = onSkip,
    )
}

/**
 * The one place a row's second line is written, so the list, the details screen
 * and the widget can never describe the same occurrence differently.
 */
private fun metaFor(
    context: android.content.Context,
    view: ReminderOccurrence,
    zone: ZoneId,
    now: Instant,
): String {
    val at = view.displayAt ?: return ""
    val today = now.atZone(zone).toLocalDate()
    val time = BalFormats.time(context, at, zone)
    return when (view.phase) {
        ReminderPhase.SNOOZED -> context.getString(R.string.state_snoozed_until, time)
        ReminderPhase.NEEDS_CONFIRMATION -> context.getString(R.string.state_waiting_at, time)
        // Overdue says the date it was actually due. A bare clock time on a row
        // that has been waiting three weeks reads as "today at 9:00", which it
        // is not.
        ReminderPhase.OVERDUE ->
            context.getString(R.string.state_overdue_at, BalFormats.dateTime(context, at, zone, now))
        else ->
            if (at.atZone(zone).toLocalDate() == today) {
                time
            } else {
                BalFormats.dateTime(context, at, zone, now)
            }
    }
}

/**
 * «مكتمل · ٩:٠٠ صباحًا» for a one-time reminder, «تم تخطيه · القادمة غدًا، ٦:٠٠
 * صباحًا» for a repeating one — the state first, then the only thing still worth
 * knowing: when it rings next. The next ring comes from the schedule, never from
 * a fixed phrase.
 */
private fun closedMeta(
    context: android.content.Context,
    item: ClosedItem,
    zone: ZoneId,
    now: Instant,
): String {
    val state = context.getString(
        if (item.status == OccurrenceStatus.COMPLETED) {
            R.string.status_completed
        } else {
            R.string.status_skipped
        },
    )
    val tail = item.returnsAt?.let {
        context.getString(R.string.next_short, BalFormats.dateTime(context, it, zone, now))
    } ?: BalFormats.time(context, item.occurrenceAt, zone)
    return "$state · $tail"
}

/** «استئناف» — the one action a paused row offers, and it is not destructive. */
@Composable
private fun ResumeButton(title: String, onClick: () -> Unit) {
    val label = stringResource(R.string.action_resume)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "$label: $title" },
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(4.dp))
        Text(label)
    }
}

@Composable
private fun ClearDayNote() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        AppMark(
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(R.string.home_nothing_today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClosedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val title = stringResource(R.string.home_closed_today)
    val action = stringResource(
        if (expanded) R.string.home_closed_collapse else R.string.home_closed_expand,
    )
    Column {
        Spacer(Modifier.height(Space.sm))
        Surface(
            onClick = onToggle,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = action; role = Role.Button },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(title, count)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(now: Instant, onOpenSettings: () -> Unit) {
    val zoned = remember(now) { now.atZone(ZoneId.systemDefault()) }
    val date = remember(zoned.toLocalDate()) { BalFormats.headerDate(zoned.toLocalDate()) }
    val hijri = remember(zoned.toLocalDate()) { BalFormats.hijriFull(zoned.toLocalDate()) }
    Column(Modifier.fillMaxWidth().padding(bottom = Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(Space.sm))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.tab_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        // The date is the header: the reliable Gregorian line as the hero, the
        // full Hijri date as a quiet companion beneath it.
        Text(
            text = date,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (hijri != null) {
            Text(
                text = hijri,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadinessBanner(issue: ReadinessIssue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            val content = MaterialTheme.colorScheme.onErrorContainer
            Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = content)
            Column(Modifier.weight(1f)) {
                Text(stringResource(issue.titleRes), style = MaterialTheme.typography.titleSmall, color = content)
                Text(stringResource(issue.bodyRes), style = MaterialTheme.typography.bodySmall, color = content)
            }
            Text(stringResource(R.string.readiness_fix), style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
