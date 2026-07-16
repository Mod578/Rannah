package com.bal.reminders.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.SectionTitle

/**
 * Hosts the editor's focused sheets. Each sheet answers exactly one of the
 * four questions and closes; nothing here needs a Save of its own, because the
 * editor's state is the draft and the screen's single Save is what commits it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheetHost(
    sheet: EditorSheet?,
    state: EditorState,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit,
    onPickTime: () -> Unit,
    onPickDate: () -> Unit,
    onPickHijri: () -> Unit,
) {
    if (sheet == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    when (sheet) {
                        EditorSheet.WHAT -> R.string.editor_sheet_what
                        EditorSheet.WHEN -> R.string.editor_sheet_when
                        EditorSheet.ALERT -> R.string.editor_sheet_alert
                        EditorSheet.FOLLOW -> R.string.editor_sheet_follow
                        EditorSheet.ADVANCED -> R.string.editor_sheet_advanced
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (sheet) {
                EditorSheet.WHAT -> WhatSheet(state, viewModel)
                EditorSheet.WHEN -> WhenSheet(state, viewModel, onPickTime, onPickDate, onPickHijri)
                EditorSheet.ALERT -> AlertSheet(state, viewModel)
                EditorSheet.FOLLOW -> FollowSheet(state, viewModel)
                EditorSheet.ADVANCED -> MoreOptions(state = state, viewModel = viewModel)
            }

            Spacer(Modifier.width(0.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(stringResource(R.string.editor_sheet_done))
            }
        }
    }
}

@Composable
private fun WhatSheet(state: EditorState, viewModel: EditorViewModel) {
    val focus = remember { FocusRequester() }
    // The one field in this sheet is the reason it opened; go straight to it.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::setTitle,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        label = { Text(stringResource(R.string.editor_sheet_what)) },
        isError = state.titleError,
        supportingText = if (state.titleError) {
            { Text(stringResource(R.string.editor_error_title)) }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
    )
    OutlinedTextField(
        value = state.notes,
        onValueChange = viewModel::setNotes,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.editor_field_notes)) },
        shape = MaterialTheme.shapes.small,
    )
    CategoryChooser(state = state, viewModel = viewModel)
}

@Composable
private fun WhenSheet(
    state: EditorState,
    viewModel: EditorViewModel,
    onPickTime: () -> Unit,
    onPickDate: () -> Unit,
    onPickHijri: () -> Unit,
) {
    val context = LocalContext.current

    RecurrenceChooser(state = state, viewModel = viewModel)

    // The calendar question only exists where it changes the schedule. Daily
    // and weekly reminders have no date to disagree about, so asking would be
    // a decision with no consequence.
    if (state.calendarApplies) {
        SectionTitle(stringResource(R.string.editor_section_calendar))
        CalendarSelector(selected = state.calendar, onSelect = viewModel::setCalendar)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onPickTime,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Rounded.AccessTime, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(BalFormats.time(context, state.time))
        }
        if (state.type == ScheduleType.ONCE) {
            OutlinedButton(
                onClick = { if (state.calendar == CalendarSystem.HIJRI) onPickHijri() else onPickDate() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.calendar == CalendarSystem.HIJRI) {
                        BalFormats.hijriDateText(state.hijriYear, state.hijriMonth, state.hijriDay)
                    } else {
                        BalFormats.date(context, state.date)
                    },
                )
            }
        }
    }

    if (state.type == ScheduleType.ONCE) {
        EquivalentDateLines(state)
    }
    if (state.type == ScheduleType.WEEKLY) {
        WeekdayPicker(
            selected = state.days,
            onToggle = viewModel::toggleDay,
            error = state.daysError,
        )
    }
    if (state.type == ScheduleType.MONTHLY) {
        DayOfMonthPicker(
            day = state.dayOfMonth,
            maxDay = if (state.calendar == CalendarSystem.HIJRI) 30 else 31,
            hijri = state.calendar == CalendarSystem.HIJRI,
            onChange = viewModel::setDayOfMonth,
        )
    }
    if (state.type == ScheduleType.YEARLY) {
        YearlyPicker(state = state, viewModel = viewModel)
    }
}

@Composable
private fun AlertSheet(state: EditorState, viewModel: EditorViewModel) {
    AlertModeChooser(selected = state.alertMode, onSelect = viewModel::setAlertMode)
}
