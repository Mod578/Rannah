package com.bal.reminders.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.scheduling.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailsState(
    val reminder: Reminder? = null,
    val records: List<OccurrenceRecord> = emptyList(),
    val loaded: Boolean = false,
)

/** One-shot UI events; undo carries the reverse action so mistakes cost one tap. */
sealed interface DetailsEvent {
    data object Closed : DetailsEvent
    data class UndoableDone(val kind: UndoKind, val title: String, val undo: suspend () -> Unit) : DetailsEvent
}

enum class UndoKind { COMPLETED, SKIPPED, SERIES_ENDED }

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val id: Long = savedStateHandle["id"] ?: 0L

    val events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 4)

    val state = combine(
        repository.observeById(id),
        repository.observeRecordsFor(id),
    ) { reminder, records ->
        DetailsState(reminder = reminder, records = records, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState())

    fun completeNow() {
        viewModelScope.launch {
            val title = state.value.reminder?.title ?: return@launch
            val occurrence: Instant = scheduler.complete(id) ?: return@launch
            events.emit(
                DetailsEvent.UndoableDone(UndoKind.COMPLETED, title) {
                    scheduler.undoComplete(id, occurrence)
                },
            )
        }
    }

    fun snooze() {
        viewModelScope.launch { scheduler.snooze(id) }
    }

    fun skipOnce() {
        viewModelScope.launch {
            val title = state.value.reminder?.title ?: return@launch
            val occurrence = scheduler.skipOccurrence(id) ?: return@launch
            events.emit(
                DetailsEvent.UndoableDone(UndoKind.SKIPPED, title) {
                    scheduler.undoSkip(id, occurrence)
                },
            )
        }
    }

    /** Series-wide; the screen confirms before calling this. */
    fun endSeries() {
        viewModelScope.launch {
            val title = state.value.reminder?.title ?: return@launch
            scheduler.endSeries(id)
            events.emit(
                DetailsEvent.UndoableDone(UndoKind.SERIES_ENDED, title) {
                    scheduler.reactivate(id)
                },
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { scheduler.setEnabled(id, enabled) }
    }

    fun duplicate() {
        viewModelScope.launch {
            val current = state.value.reminder ?: return@launch
            scheduler.save(
                current.copy(
                    id = 0L,
                    snoozedUntil = null,
                    nextTriggerAt = null,
                    completedAt = null,
                    createdAt = java.time.Instant.now(),
                ),
            )
        }
    }

    fun delete() {
        viewModelScope.launch {
            scheduler.delete(id)
            events.emit(DetailsEvent.Closed)
        }
    }

    /** Runs an undo lambda from the snackbar action. */
    fun undo(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }
}
