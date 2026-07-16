package com.bal.reminders.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogGroup(val date: LocalDate, val items: List<OccurrenceRecord>)

data class LogState(val groups: List<LogGroup> = emptyList())

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: ReminderRepository,
) : ViewModel() {

    val state = repository.observeRecords()
        .map { records ->
            val zone = ZoneId.systemDefault()
            LogState(
                groups = records
                    .groupBy { it.recordedAt.atZone(zone).toLocalDate() }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (date, items) -> LogGroup(date, items) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    fun clear() {
        viewModelScope.launch { repository.clearRecords() }
    }
}
