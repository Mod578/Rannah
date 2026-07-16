package com.bal.reminders.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.domain.model.Category
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.EmptyState
import com.bal.reminders.ui.components.ReminderCard
import com.bal.reminders.ui.components.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onOpenDetails: (Long) -> Unit,
    onOpenLog: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = stringResource(R.string.log_title),
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
                .padding(horizontal = 20.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reminders_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text(stringResource(R.string.reminders_filter_all)) },
                )
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = state.filter == category,
                        onClick = { viewModel.setFilter(category) },
                        label = { Text(stringResource(category.labelRes)) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    title = stringResource(
                        if (state.query.isBlank() && state.filter == null) {
                            R.string.reminders_empty_title
                        } else {
                            R.string.reminders_empty_filtered
                        },
                    ),
                    subtitle = if (state.query.isBlank() && state.filter == null) {
                        stringResource(R.string.reminders_empty_subtitle)
                    } else {
                        null
                    },
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                ) {
                    items(state.items, key = { it.id }) { reminder ->
                        val ended = reminder.isDone && reminder.schedule.isRecurring
                        ReminderCard(
                            reminder = reminder,
                            subtitle = if (ended) {
                                stringResource(R.string.reminders_series_ended)
                            } else {
                                BalFormats.scheduleSummary(context, reminder.schedule)
                            },
                            onClick = { onOpenDetails(reminder.id) },
                            onToggle = if (ended) null else { it -> viewModel.setEnabled(reminder.id, it) },
                            awaiting = reminder.id in state.awaitingIds,
                        )
                    }
                }
            }
        }
    }
}
