package com.bal.reminders.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.R
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.scheduling.ReminderScheduler
import com.bal.reminders.ui.UndoCoordinator
import com.bal.reminders.ui.UndoRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * An occurrence that is finished for today — confirmed or deliberately skipped.
 * [returnsAt] is the next time this reminder will ring, so a skipped recurring
 * reminder says out loud that tomorrow is untouched.
 */
data class ClosedItem(
    val reminderId: Long,
    val title: String,
    val occurrenceAt: Instant,
    val status: OccurrenceStatus,
    val returnsAt: Instant?,
)

data class ChecklistState(
    /**
     * One-time reminders whose day is behind us and which were never answered.
     * They ride above the day with their real date — filing a three-week-old
     * errand under «اليوم» with nothing but a clock time was simply untrue.
     */
    val overdue: List<ReminderOccurrence> = emptyList(),
    /** Everything due today: waiting, postponed, and still to come — in time order. */
    val today: List<ReminderOccurrence> = emptyList(),
    val upcoming: List<ReminderOccurrence> = emptyList(),
    val closed: List<ClosedItem> = emptyList(),
    val paused: List<ReminderOccurrence> = emptyList(),
    val hasAnyReminder: Boolean = false,
) {
    /** Reminders exist, but nothing is waiting today. */
    val nothingToday: Boolean get() = hasAnyReminder && today.isEmpty() && overdue.isEmpty()
}

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val undoCoordinator: UndoCoordinator,
    private val clock: Clock,
) : ViewModel() {

    /** Undo offers to show, including ones raised by the details screen. */
    val undoOffers = undoCoordinator.pending

    // A coarse minute tick so occurrences cross into «ينتظر تأكيدك» / out of «مؤجل»
    // even while the app sits open and no database change happens.
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    init {
        // A reminder finished yesterday leaves today's list at midnight. Clean it
        // up as the day turns, so it cannot linger in the database with no surface
        // left to show it while the app stays open across midnight.
        viewModelScope.launch {
            var day = LocalDate.now(clock)
            minuteTick.collect {
                val today = LocalDate.now(clock)
                if (today != day) {
                    day = today
                    scheduler.pruneFinished()
                }
            }
        }
    }

    val state = combine(
        repository.observeAll(),
        repository.observeRecords(),
        minuteTick,
    ) { reminders, records, _ ->
        ChecklistGrouping.group(reminders, records, clock.instant(), clock.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistState())

    /** «تم» on a row — the ring, or the swipe. Idempotent and undoable. */
    fun complete(item: ReminderOccurrence) {
        val occ = item.occurrenceAt ?: return
        viewModelScope.launch {
            val occurrence = scheduler.complete(item.reminderId, occ) ?: return@launch
            undoCoordinator.offer(
                if (item.recurring) R.string.undo_completed_today else R.string.undo_completed,
                item.title,
            ) {
                scheduler.undoComplete(item.reminderId, occurrence)
            }
        }
    }

    /**
     * «تخطي اليوم» on a daily or recurring row: this occurrence is closed without
     * claiming it was done, and the reminder keeps every day after it. The undo
     * message names the scope out loud — «لليوم فقط» — because the one thing a
     * person needs to be sure of here is that they did not just end the series.
     */
    fun skipToday(item: ReminderOccurrence) {
        val occ = item.occurrenceAt ?: return
        viewModelScope.launch {
            val occurrence = scheduler.skipOccurrence(item.reminderId, occ) ?: return@launch
            undoCoordinator.offer(R.string.undo_skipped_today, item.title) {
                scheduler.undoSkip(item.reminderId, occurrence)
            }
        }
    }

    /** «تراجع» on a finished row. */
    fun undoClosed(item: ClosedItem) {
        viewModelScope.launch {
            when (item.status) {
                OccurrenceStatus.SKIPPED -> scheduler.undoSkip(item.reminderId, item.occurrenceAt)
                else -> scheduler.undoComplete(item.reminderId, item.occurrenceAt)
            }
        }
    }

    /** «استئناف» on a paused row: the reminder starts ringing again from its schedule. */
    fun resume(item: ReminderOccurrence) {
        viewModelScope.launch { scheduler.setEnabled(item.reminderId, true) }
    }

    /** Takes the pending undo offer, so it is shown once and never replayed. */
    fun takeUndo(): UndoRequest? = undoCoordinator.take()

    fun runUndo(request: UndoRequest) {
        viewModelScope.launch { request.action() }
    }

    fun clockNow(): Instant = clock.instant()
}
