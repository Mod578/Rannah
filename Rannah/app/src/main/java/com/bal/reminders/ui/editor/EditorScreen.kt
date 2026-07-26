package com.bal.reminders.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.ChoiceChips
import com.bal.reminders.ui.components.ChoiceChipsMulti
import com.bal.reminders.ui.theme.Space
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Adding a reminder is three questions in order — what, when, at what time —
 * and one sentence confirming what رَنّة understood before the reminder is
 * saved. That sentence is the point: it says «كل يوم، الساعة ٦:٠٠ صباحًا» or
 * «مرة واحدة، الأحد ١٢ يوليو»، so nobody has to infer from a chip whether they
 * just built a repeating reminder or a single one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNotes by remember(state.loaded) { mutableStateOf(state.notes.isNotBlank()) }

    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // ١ — ماذا
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.editor_what_hint)) },
                isError = state.titleError,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.titleMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            if (state.titleError) ErrorText(stringResource(R.string.editor_error_title))

            // A schedule understood from the words, offered as one tap.
            state.parsedSchedule?.let { parsed ->
                Surface(
                    onClick = viewModel::applyParsed,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = stringResource(
                                R.string.editor_apply_understood,
                                BalFormats.scheduleSummary(context, parsed),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // ٢ — متى يتكرر
            Field(stringResource(R.string.editor_repeat)) {
                ChoiceChips(
                    options = listOf(
                        ScheduleType.ONCE to stringResource(R.string.schedule_type_once),
                        ScheduleType.DAILY to stringResource(R.string.schedule_type_daily),
                        ScheduleType.WEEKLY to stringResource(R.string.schedule_type_weekly),
                        ScheduleType.MONTHLY to stringResource(R.string.schedule_type_monthly),
                        ScheduleType.YEARLY to stringResource(R.string.schedule_type_yearly),
                    ),
                    selected = state.type,
                    onSelect = viewModel::setType,
                )
            }

            WhenDetails(state, viewModel)

            // ٣ — الساعة
            Field(stringResource(R.string.editor_time)) {
                TimeRow(state.time) { viewModel.setTime(it) }
            }
            if (state.pastError) ErrorText(stringResource(R.string.editor_error_past))

            // ما الذي سيحدث فعلًا — بجملة واحدة قبل الحفظ.
            state.buildSchedule()?.let { schedule ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        AppMark(
                            body = MaterialTheme.colorScheme.primary,
                            ring = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = BalFormats.scheduleSummary(context, schedule),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ملاحظة اختيارية، مخفية حتى تُطلب.
            if (showNotes) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.editor_field_notes)) },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            } else {
                TextButton(
                    onClick = { showNotes = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(stringResource(R.string.editor_add_note))
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    stringResource(if (state.isNew) R.string.editor_save else R.string.editor_save_changes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(Space.md))
        }
    }
}

/** A labelled block: the label, then its control, with one consistent gap. */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun WhenDetails(state: EditorState, viewModel: EditorViewModel) {
    when (state.type) {
        ScheduleType.ONCE -> Field(stringResource(R.string.editor_date)) {
            DateRow(state.date) { viewModel.setDate(it) }
            HijriHint(state.date)
        }
        ScheduleType.DAILY -> Unit
        ScheduleType.WEEKLY -> Field(stringResource(R.string.editor_days)) {
            WeekdayChips(state.days) { viewModel.toggleDay(it) }
            if (state.daysError) ErrorText(stringResource(R.string.editor_error_days))
        }
        ScheduleType.MONTHLY -> Field(stringResource(R.string.editor_day_of_month)) {
            Stepper(
                value = BalFormats.arabicDigits(state.dayOfMonth.toString()),
                onDec = { viewModel.setDayOfMonth(state.dayOfMonth - 1) },
                onInc = { viewModel.setDayOfMonth(state.dayOfMonth + 1) },
            )
            if (state.dayOfMonth >= 29) {
                Hint(stringResource(R.string.editor_day_of_month_clamp_note))
            }
        }
        ScheduleType.YEARLY -> {
            Field(stringResource(R.string.editor_month)) {
                Stepper(
                    value = BalFormats.gregorianMonthName(state.month),
                    onDec = { viewModel.setMonth(if (state.month <= 1) 12 else state.month - 1) },
                    onInc = { viewModel.setMonth(if (state.month >= 12) 1 else state.month + 1) },
                )
            }
            Field(stringResource(R.string.editor_day_of_month)) {
                Stepper(
                    value = BalFormats.arabicDigits(state.dayOfMonth.toString()),
                    onDec = { viewModel.setDayOfMonth(state.dayOfMonth - 1) },
                    onInc = { viewModel.setDayOfMonth(state.dayOfMonth + 1) },
                )
            }
        }
    }
}

/** The full Hijri date shown as a companion line under a chosen date. */
@Composable
private fun HijriHint(date: LocalDate) {
    val hint = BalFormats.hijriFull(date) ?: return
    Hint(hint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(time: LocalTime, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    PickerButton(icon = Icons.Rounded.Schedule, text = BalFormats.time(context, time)) { open = true }
    if (open) {
        val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalTime.of(timeState.hour, timeState.minute))
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    PickerButton(icon = Icons.Rounded.CalendarMonth, text = BalFormats.date(context, date)) { open = true }
    if (open) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dateState) }
    }
}

@Composable
private fun WeekdayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val order = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    ChoiceChipsMulti(
        options = order.map { it to BalFormats.dayName(it) },
        selected = selected,
        onToggle = onToggle,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PickerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Stepper(value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(onClick = onDec, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.action_decrease))
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
            FilledTonalIconButton(onClick = onInc, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_increase))
            }
        }
    }
}
