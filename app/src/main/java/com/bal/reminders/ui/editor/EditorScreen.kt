package com.bal.reminders.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.domain.HijriDates
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.domain.model.Category
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.SectionTitle
import com.bal.reminders.ui.components.labelRes
import com.bal.reminders.ui.permissions.Permissions
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.done.collect { onDone() }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showHijriPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }
    val permissions = remember { Permissions.status(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // أربعة أسئلة، كل واحد سطر مقروء يفتح ورقة مركّزة. الشاشة تُقرأ
            // كاملة قبل أي لمسة، بدل أن تُملأ من أعلى إلى أسفل.
            SummaryRow(
                icon = sheetIcon(EditorSheet.WHAT),
                label = stringResource(R.string.editor_row_what),
                value = whatSummary(state),
                placeholder = state.title.isBlank(),
                onClick = { sheet = EditorSheet.WHAT },
            )
            if (state.titleError) {
                ErrorText(stringResource(R.string.editor_error_title))
            }

            SummaryRow(
                icon = sheetIcon(EditorSheet.WHEN),
                label = stringResource(R.string.editor_row_when),
                value = whenSummary(state),
                placeholder = state.buildSchedule() == null,
                onClick = { sheet = EditorSheet.WHEN },
            )
            if (state.pastError) {
                ErrorText(stringResource(R.string.editor_error_past))
            }
            if (state.hijriRangeError) {
                ErrorText(stringResource(R.string.editor_error_hijri_range))
            }
            if (state.daysError) {
                ErrorText(stringResource(R.string.editor_error_days))
            }

            SummaryRow(
                icon = sheetIcon(EditorSheet.ALERT),
                label = stringResource(R.string.editor_row_alert),
                value = alertSummary(state),
                onClick = { sheet = EditorSheet.ALERT },
            )
            if (state.showSuggestion) {
                SuggestionRow(state = state, viewModel = viewModel)
            }
            if (state.alertMode == AlertMode.ALARM && permissions.alarmVolumeMuted) {
                NoteText(stringResource(R.string.editor_alarm_volume_muted))
            }
            if (!permissions.exactAlarmsGranted) {
                NoteText(stringResource(R.string.editor_exact_denied_note))
            }

            SummaryRow(
                icon = sheetIcon(EditorSheet.FOLLOW),
                label = stringResource(R.string.editor_row_follow),
                value = followSummary(state),
                onClick = { sheet = EditorSheet.FOLLOW },
            )

            TextButton(
                onClick = { sheet = EditorSheet.ADVANCED },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.editor_more_show))
            }

            // ملخص صريح قبل الحفظ: لا حفظ لجدولة غامضة.
            SummaryCard(state)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(if (state.isNew) R.string.editor_save else R.string.editor_save_changes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    EditorSheetHost(
        sheet = sheet,
        state = state,
        viewModel = viewModel,
        onDismiss = { sheet = null },
        onPickTime = { showTimePicker = true },
        onPickDate = { showDatePicker = true },
        onPickHijri = { showHijriPicker = true },
    )

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.editor_pick_time)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTime(java.time.LocalTime.of(timeState.hour, timeState.minute))
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            viewModel.setDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showHijriPicker) {
        HijriDatePickerDialog(
            initialYear = state.hijriYear,
            initialMonth = state.hijriMonth,
            initialDay = state.hijriDay,
            adjustmentDays = state.hijriAdjustmentDays,
            onConfirm = { y, m, d ->
                viewModel.setHijriDate(y, m, d)
                showHijriPicker = false
            },
            onDismiss = { showHijriPicker = false },
        )
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
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
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

// ---------------------------------------------------------------- calendar

@Composable
internal fun CalendarSelector(
    selected: CalendarSystem,
    onSelect: (CalendarSystem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.editor_section_calendar),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(
                CalendarSystem.GREGORIAN to R.string.editor_calendar_gregorian,
                CalendarSystem.HIJRI to R.string.editor_calendar_hijri,
            )
            options.forEachIndexed { index, (calendar, labelRes) ->
                SegmentedButton(
                    selected = selected == calendar,
                    onClick = { onSelect(calendar) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

/** Primary date in the chosen calendar plus its equivalent in the other one. */
@Composable
internal fun EquivalentDateLines(state: EditorState) {
    val hijri = state.calendar == CalendarSystem.HIJRI
    val primary: String
    val equivalent: String?
    if (hijri) {
        primary = BalFormats.hijriDateText(state.hijriYear, state.hijriMonth, state.hijriDay)
        equivalent = HijriDates.toGregorian(
            state.hijriYear, state.hijriMonth, state.hijriDay, state.hijriAdjustmentDays,
        )?.let(BalFormats::gregorianDate)
    } else {
        primary = BalFormats.gregorianDate(state.date)
        equivalent = BalFormats.hijriDate(state.date, state.hijriAdjustmentDays)
    }
    Column {
        Text(
            stringResource(R.string.editor_primary_date, primary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (equivalent != null) {
            Text(
                stringResource(R.string.editor_equivalent_approx, equivalent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -------------------------------------------------------------- alert mode

@Composable
internal fun AlertModeChooser(
    selected: AlertMode,
    onSelect: (AlertMode) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlertModeCard(
            mode = AlertMode.STANDARD,
            titleRes = R.string.alert_mode_standard,
            hintRes = R.string.alert_mode_standard_hint,
            icon = Icons.Rounded.NotificationsNone,
            selected = selected == AlertMode.STANDARD,
            onSelect = onSelect,
        )
        AlertModeCard(
            mode = AlertMode.ALARM,
            titleRes = R.string.alert_mode_alarm,
            hintRes = R.string.alert_mode_alarm_hint,
            icon = Icons.Rounded.Alarm,
            selected = selected == AlertMode.ALARM,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AlertModeCard(
    mode: AlertMode,
    titleRes: Int,
    hintRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: (AlertMode) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(mode) },
            ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(hintRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionRow(state: EditorState, viewModel: EditorViewModel) {
    val label = when (state.suggestionSource) {
        SuggestionSource.LEARNED -> stringResource(
            R.string.editor_alert_suggestion_learned,
            stringResource(
                if (state.suggestedAlertMode == AlertMode.ALARM) {
                    R.string.alert_mode_alarm
                } else {
                    R.string.alert_mode_standard
                },
            ),
        )
        else -> stringResource(R.string.editor_alert_suggestion_alarm)
    }
    // الاقتراح يُنسب إلى رَنّة صراحةً، ويُرفض بـ«ليس الآن»: «إلغاء» هنا تُقرأ
    // إلغاءً للتذكير كله. والصف يلتفّ بدل أن يعصر أزراره عند الخطوط الكبيرة.
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.editor_suggestion_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = viewModel::applySuggestion,
                label = { Text(stringResource(R.string.editor_alert_suggestion_apply)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            TextButton(
                onClick = viewModel::dismissSuggestion,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_not_now))
            }
        }
    }
}

// ------------------------------------------------------------ more options

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreOptions(state: EditorState, viewModel: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.editor_section_options))
        if (state.alertMode == AlertMode.STANDARD) {
            // «تذكير مهم» كان هنا مفتاحًا ثانيًا لما تقرره بطاقة «التنبيه» أصلًا.
            // مفهوم واحد لا يملك تحكمين: «منبّه مهم» هو المعتمد.
            OptionSwitch(
                label = stringResource(R.string.editor_option_sound),
                checked = state.soundEnabled,
                onChecked = viewModel::setSound,
            )
            OptionSwitch(
                label = stringResource(R.string.editor_option_vibration),
                checked = state.vibrationEnabled,
                onChecked = viewModel::setVibration,
            )
        } else {
            RingtoneRow(state = state, viewModel = viewModel)
            OptionSwitch(
                label = stringResource(R.string.editor_option_vibration),
                checked = state.vibrationEnabled,
                onChecked = viewModel::setVibration,
            )
            OptionSwitch(
                label = stringResource(R.string.editor_option_gradual_volume),
                checked = state.alarmGradualVolume,
                onChecked = viewModel::setGradualVolume,
            )
            OptionSwitch(
                label = stringResource(R.string.editor_option_repeat_ignored),
                checked = state.alarmRepeatIfIgnored,
                onChecked = viewModel::setRepeatIfIgnored,
            )
            // «عند إيقاف المنبّه اعتبره منجزًا» حُذف: «إيقاف الصوت» يوقف الصوت،
            // ولا يحكم على مهمة في العالم الحقيقي. الدلالة واحدة ولا تُشترى بخيار.
            SectionTitle(stringResource(R.string.editor_alarm_timeout))
            MinutesChoiceRow(
                options = listOf(1, 3, 5, 10),
                selected = state.alarmTimeoutMinutes,
                onSelect = viewModel::setAlarmTimeout,
            )
        }

        SectionTitle(stringResource(R.string.editor_section_snooze))
        MinutesChoiceRow(
            options = listOf(5, 10, 15, 30),
            selected = state.snoozeMinutes,
            onSelect = viewModel::setSnooze,
        )

    }
}

/**
 * Recurrence and category as wrapping chips.
 *
 * These were segmented rows in a horizontal scroll, which silently cut «شهريًا»
 * off the edge of the screen: an option the user could not see was an option
 * they did not have. Chips that wrap cannot clip, and they survive the largest
 * font scales, which is exactly where the old row failed worst.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecurrenceChooser(state: EditorState, viewModel: EditorViewModel) {
    val options = listOf(
        ScheduleType.ONCE to R.string.schedule_type_once,
        ScheduleType.DAILY to R.string.schedule_type_daily,
        ScheduleType.WEEKLY to R.string.schedule_type_weekly,
        ScheduleType.MONTHLY to R.string.schedule_type_monthly,
        ScheduleType.YEARLY to R.string.schedule_type_yearly,
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (type, labelRes) ->
            FilterChip(
                selected = state.type == type,
                onClick = { viewModel.setType(type) },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** A minutes choice that wraps instead of squeezing its labels to nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MinutesChoiceRow(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = selected == minutes,
                onClick = { onSelect(minutes) },
                label = {
                    Text(
                        stringResource(
                            R.string.editor_snooze_option,
                            BalFormats.arabicDigits(minutes.toString()),
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryChooser(state: EditorState, viewModel: EditorViewModel) {
    SectionTitle(stringResource(R.string.editor_section_category))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.entries.forEach { category ->
            FilterChip(
                selected = state.category == category,
                onClick = { viewModel.setCategory(category) },
                label = { Text(stringResource(category.labelRes)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingtoneRow(state: EditorState, viewModel: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = state.ringtones.firstOrNull { it.uri == state.ringtoneUri }?.title
        ?: stringResource(R.string.editor_ringtone_default)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedTitle,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.editor_ringtone)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_ringtone_default)) },
                    onClick = {
                        viewModel.setRingtone(null)
                        expanded = false
                    },
                )
                state.ringtones.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.title) },
                        onClick = {
                            viewModel.setRingtone(choice.uri)
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = {
                if (state.previewingRingtone) viewModel.stopPreview() else viewModel.previewRingtone()
            },
        ) {
            Icon(
                if (state.previewingRingtone) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (state.previewingRingtone) {
                        R.string.editor_ringtone_preview_stop
                    } else {
                        R.string.editor_ringtone_preview
                    },
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ----------------------------------------------------------------- summary

/** The concise pre-save answer to "ماذا سيحدث؟": schedule, actions, calendar basis. */
@Composable
internal fun SummaryCard(state: EditorState) {
    val context = LocalContext.current
    val schedule = state.buildSchedule() ?: return
    if (state.title.isBlank()) return

    val alarm = state.alertMode == AlertMode.ALARM
    val scheduleText = BalFormats.scheduleSummary(context, schedule)
    val first = stringResource(
        if (alarm) R.string.editor_summary_alarm else R.string.editor_summary_standard,
        state.title.trim(),
        scheduleText,
    )
    val snoozeText = context.resources.getQuantityString(
        R.plurals.duration_minutes,
        state.snoozeMinutes,
        BalFormats.arabicDigits(state.snoozeMinutes.toString()),
    )
    val second = stringResource(
        if (alarm) R.string.editor_summary_actions_alarm else R.string.editor_summary_actions_standard,
        snoozeText,
    )
    val basis = when {
        !state.calendarApplies -> null
        schedule.calendar == CalendarSystem.HIJRI -> stringResource(R.string.editor_summary_hijri_basis)
        else -> stringResource(R.string.editor_summary_gregorian_basis)
    }
    // The follow-up changes what "done" means, so the summary says so in the
    // same breath as the schedule rather than leaving it to a sheet.
    val follow = if (state.followUntilComplete) {
        stringResource(
            R.string.editor_summary_follow,
            state.completionLabel?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.editor_completion_default),
            context.resources.getQuantityString(
                R.plurals.duration_minutes,
                state.followUpWindowMinutes,
                BalFormats.arabicDigits(state.followUpWindowMinutes.toString()),
            ),
        )
    } else {
        null
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.editor_summary_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "$first $second",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (follow != null) {
                Text(
                    follow,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (basis != null) {
                Text(
                    basis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- pickers

/**
 * The week as chips that wrap.
 *
 * This was a horizontal scroll, which pushed the end of the week off the edge
 * of the screen: at large font scales «الخميس» and «الجمعة» were simply not
 * there unless you knew to drag sideways. A day you cannot see is a day you do
 * not have. Wrapping cannot clip, and it survives 200% text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekdayPicker(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    error: Boolean,
) {
    // Saturday-first: the Arabic week.
    val ordered = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ordered.forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = { onToggle(day) },
                    label = { Text(BalFormats.dayName(day)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (error) {
            ErrorText(stringResource(R.string.editor_error_days))
        }
    }
}

@Composable
internal fun DayOfMonthPicker(day: Int, maxDay: Int, hijri: Boolean, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.editor_day_of_month),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = { onChange(day - 1) }, enabled = day > 1) { Text("−") }
        Text(
            BalFormats.arabicDigits(day.toString()),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(onClick = { onChange(day + 1) }, enabled = day < maxDay) { Text("+") }
    }
    if (day >= 29) {
        NoteText(
            stringResource(
                if (hijri) R.string.editor_hijri_month_note else R.string.editor_day_of_month_clamp_note,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YearlyPicker(state: EditorState, viewModel: EditorViewModel) {
    val hijri = state.calendar == CalendarSystem.HIJRI
    var expanded by remember { mutableStateOf(false) }
    val monthNames = if (hijri) {
        BalFormats.hijriMonthNames
    } else {
        (1..12).map(BalFormats::gregorianMonthName)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = monthNames[(state.month - 1).coerceIn(0, 11)],
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.editor_hijri_month)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                monthNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            viewModel.setMonth(index + 1)
                            expanded = false
                        },
                    )
                }
            }
        }
        DayOfMonthPicker(
            day = state.dayOfMonth,
            maxDay = if (hijri) 30 else java.time.Month.of(state.month.coerceIn(1, 12)).maxLength(),
            hijri = hijri,
            onChange = viewModel::setDayOfMonth,
        )
    }
}

/**
 * A large-text, RTL-friendly Hijri picker: year and day steppers plus a month
 * list, with the equivalent civil date shown live so nothing stays ambiguous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HijriDatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    adjustmentDays: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    var day by remember { mutableIntStateOf(initialDay) }
    var monthExpanded by remember { mutableStateOf(false) }
    val maxDay = HijriDates.monthLength(year, month) ?: 30
    if (day > maxDay) day = maxDay
    val equivalent = HijriDates.toGregorian(year, month, day, adjustmentDays)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_calendar_hijri)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperRow(
                    label = stringResource(R.string.editor_hijri_year),
                    value = year,
                    onChange = { year = it },
                    min = HijriDates.supportedYears.first,
                    max = HijriDates.supportedYears.last,
                )
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = it },
                ) {
                    OutlinedTextField(
                        value = BalFormats.hijriMonthName(month),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.editor_hijri_month)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                    ExposedDropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false },
                    ) {
                        BalFormats.hijriMonthNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    month = index + 1
                                    monthExpanded = false
                                },
                            )
                        }
                    }
                }
                StepperRow(
                    label = stringResource(R.string.editor_hijri_day),
                    value = day,
                    onChange = { day = it },
                    min = 1,
                    max = maxDay,
                )
                if (equivalent != null) {
                    Text(
                        stringResource(
                            R.string.editor_equivalent_approx,
                            BalFormats.gregorianDate(equivalent),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ErrorText(stringResource(R.string.editor_error_hijri_range))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(year, month, day) },
                enabled = equivalent != null,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit, min: Int, max: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > min) { Text("−") }
        Text(
            BalFormats.arabicDigits(value.toString()),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

// ------------------------------------------------------------------- misc

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun NoteText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
