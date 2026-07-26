package com.bal.reminders.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.R
import com.bal.reminders.domain.OccurrenceStateResolver
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.scheduling.ReminderScheduler
import com.bal.reminders.ui.UndoCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the details screen shows, split the way the screen is split: today's
 * occurrence, and the reminder as a whole.
 */
data class DetailsState(
    val reminder: Reminder? = null,
    /** The same resolved occurrence state the home uses, so the two cannot disagree. */
    val occurrence: ReminderOccurrence? = null,
    val records: List<OccurrenceRecord> = emptyList(),
    /** Today's occurrence, when there is one to answer or to take back. */
    val todayOccurrence: Instant? = null,
    /** How today's occurrence was already answered, if it was. */
    val todayAnswer: OccurrenceStatus? = null,
    val loaded: Boolean = false,
) {
    val recurring: Boolean get() = reminder?.schedule?.isRecurring == true

    /** Today is still open: رَنّة is waiting for «تم» or «تخطي اليوم». */
    val todayOpen: Boolean get() = todayOccurrence != null && todayAnswer == null
}

sealed interface DetailsEvent {
    data object Closed : DetailsEvent
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val undoCoordinator: UndoCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val id: Long = savedStateHandle["id"] ?: 0L

    val events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 4)

    val state = combine(
        repository.observeById(id),
        repository.observeRecordsFor(id),
    ) { reminder, records ->
        val now = clock.instant()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()

        val occurrence = reminder?.let {
            val resolved = records
                .filter { r -> r.status.resolvesOccurrence }
                .map { r -> r.occurrenceAt.toEpochMilli() }
                .toHashSet()
            OccurrenceStateResolver.resolve(it, now, zone) { occ ->
                resolved.contains(occ.toEpochMilli())
            }
        }

        // Today's answer, if one was given today. Skipping tomorrow's occurrence
        // early would be answered from tomorrow's screen, not this one.
        val answered = records.firstOrNull { r ->
            r.status.resolvesOccurrence && r.recordedAt.atZone(zone).toLocalDate() == today
        }
        // The occurrence today's zone acts on: the one already answered, or the
        // live one when it belongs to today.
        val live = occurrence?.occurrenceAt?.takeIf {
            occurrence.phase == ReminderPhase.NEEDS_CONFIRMATION ||
                occurrence.phase == ReminderPhase.SNOOZED ||
                (
                    occurrence.phase == ReminderPhase.UPCOMING &&
                        it.atZone(zone).toLocalDate() == today
                    )
        }

        DetailsState(
            reminder = reminder,
            occurrence = occurrence,
            records = records,
            todayOccurrence = answered?.occurrenceAt ?: live,
            todayAnswer = answered?.status,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState())

    /** «تم» — today's occurrence only; a repeating reminder keeps its future. */
    fun complete(occurrenceAt: Instant) {
        viewModelScope.launch { scheduler.complete(id, occurrenceAt) }
    }

    /** «تخطي اليوم» — close today without claiming it was done. Repeating only. */
    fun skipToday(occurrenceAt: Instant) {
        viewModelScope.launch { scheduler.skipOccurrence(id, occurrenceAt) }
    }

    /** «تراجع» on today's answer, whichever answer it was. */
    fun undoToday(occurrenceAt: Instant, status: OccurrenceStatus) {
        viewModelScope.launch {
            when (status) {
                OccurrenceStatus.SKIPPED -> scheduler.undoSkip(id, occurrenceAt)
                else -> scheduler.undoComplete(id, occurrenceAt)
            }
        }
    }

    /** «إيقاف مؤقت» / «استئناف» — the whole reminder. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { scheduler.setEnabled(id, enabled) }
    }

    /**
     * «حذف التذكير». The reminder and its records go for real; what is kept is an
     * in-memory snapshot, offered as «تراجع» on the home screen this closes back to.
     */
    fun delete() {
        viewModelScope.launch {
            val deleted = scheduler.delete(id)
            if (deleted != null) {
                undoCoordinator.offer(R.string.undo_deleted, deleted.reminder.title) {
                    scheduler.restore(deleted)
                }
            }
            events.emit(DetailsEvent.Closed)
        }
    }
}
