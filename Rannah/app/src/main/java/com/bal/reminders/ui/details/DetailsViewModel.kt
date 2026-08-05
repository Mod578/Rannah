package com.bal.reminders.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.R
import com.bal.reminders.alarm.SnoozeOptions
import com.bal.reminders.domain.OccurrenceStateResolver
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.SnoozeRequest
import com.bal.reminders.domain.SnoozeResult
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the details screen shows, split the way the screen is split: this
 * occurrence, and the reminder as a whole.
 */
data class DetailsState(
    val reminder: Reminder? = null,
    /** The same resolved occurrence state the home uses, so the two cannot disagree. */
    val occurrence: ReminderOccurrence? = null,
    val records: List<OccurrenceRecord> = emptyList(),
    /** The occurrence «اليوم» acts on, when there is one to answer or to take back. */
    val todayOccurrence: Instant? = null,
    /** How that occurrence was already answered, if it was. */
    val todayAnswer: OccurrenceStatus? = null,
    /** Non-null while «تغيير وقت التأجيل» is open. */
    val snoozeOptions: SnoozeOptions? = null,
    val loaded: Boolean = false,
) {
    val recurring: Boolean get() = reminder?.schedule?.isRecurring == true

    /** «تخطي اليوم» exists only where there is a next occurrence to keep. */
    val canSkip: Boolean get() = recurring && todayAnswer == null && todayOccurrence != null

    /** The occurrence is postponed: it can be moved again, or brought back. */
    val snoozed: Boolean get() = occurrence?.phase == ReminderPhase.SNOOZED
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
    private val snoozeOptions = MutableStateFlow<SnoozeOptions?>(null)

    val events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 4)

    val state = combine(
        repository.observeById(id),
        repository.observeRecordsFor(id),
        snoozeOptions,
    ) { reminder, records, options ->
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

        // The occurrence this zone acts on: a live one first, always. Preferring
        // an answer given earlier today would hide a *new* occurrence that an
        // edit has put back on the clock: the screen would say «اكتمل اليوم»
        // while an alarm it never mentioned was still armed for this evening.
        val live = occurrence?.occurrenceAt?.takeIf {
            when (occurrence.phase) {
                ReminderPhase.NEEDS_CONFIRMATION,
                ReminderPhase.SNOOZED,
                ReminderPhase.OVERDUE,
                -> true
                ReminderPhase.UPCOMING -> it.atZone(zone).toLocalDate() == today
                else -> false
            }
        }
        val answered = records.firstOrNull { r ->
            r.status.resolvesOccurrence && r.recordedAt.atZone(zone).toLocalDate() == today
        }

        DetailsState(
            reminder = reminder,
            occurrence = occurrence,
            records = records,
            todayOccurrence = live ?: answered?.occurrenceAt,
            todayAnswer = if (live != null) null else answered?.status,
            snoozeOptions = options,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState())

    /** «تم»: this occurrence only; a repeating reminder keeps its future. */
    fun complete(occurrenceAt: Instant) {
        viewModelScope.launch {
            val done = scheduler.complete(id, occurrenceAt) ?: return@launch
            val reminder = state.value.reminder ?: return@launch
            undoCoordinator.offer(
                if (reminder.schedule.isRecurring) R.string.undo_completed_today else R.string.undo_completed,
                reminder.title,
            ) { scheduler.undoComplete(id, done) }
        }
    }

    /** «تخطي اليوم»: close today without claiming it was done. Repeating only. */
    fun skipToday(occurrenceAt: Instant) {
        viewModelScope.launch {
            val skipped = scheduler.skipOccurrence(id, occurrenceAt) ?: return@launch
            val reminder = state.value.reminder ?: return@launch
            undoCoordinator.offer(R.string.undo_skipped_today, reminder.title) {
                scheduler.undoSkip(id, skipped)
            }
        }
    }

    /** «تراجع» on this occurrence's answer, whichever answer it was. */
    fun undoToday(occurrenceAt: Instant, status: OccurrenceStatus) {
        viewModelScope.launch {
            when (status) {
                OccurrenceStatus.SKIPPED -> scheduler.undoSkip(id, occurrenceAt)
                else -> scheduler.undoComplete(id, occurrenceAt)
            }
        }
    }

    // ------------------------------------------------------------------ تأجيل

    /** «تغيير وقت التأجيل»: the same sheet the alarm screen offers, in daylight. */
    fun openSnoozeOptions() {
        val occurrence = state.value.occurrence?.occurrenceAt ?: return
        viewModelScope.launch {
            snoozeOptions.value = SnoozeOptions(limit = scheduler.snoozeLimit(id, occurrence))
        }
    }

    fun dismissSnoozeOptions() {
        snoozeOptions.value = null
    }

    /**
     * Moving a live postponement. The current snooze is cleared first so the
     * scheduler sees an unpostponed occurrence, otherwise it would refuse the
     * change as a duplicate of the postponement already in place.
     */
    fun changeSnooze(request: SnoozeRequest) {
        val occurrence = state.value.occurrence?.occurrenceAt ?: return
        viewModelScope.launch {
            val previous = state.value.reminder?.snoozedUntil
            scheduler.cancelSnooze(id)
            when (val result = scheduler.snooze(id, occurrence, request)) {
                is SnoozeResult.Scheduled -> snoozeOptions.value = null
                is SnoozeResult.TooLate -> {
                    // Put the original postponement back before reporting the
                    // refusal, so a rejected change never silently un-snoozes.
                    if (previous != null) scheduler.snooze(id, occurrence, SnoozeRequest.Until(previous))
                    snoozeOptions.value = SnoozeOptions(limit = result.latest, rejected = result.latest)
                }
                SnoozeResult.Unavailable -> snoozeOptions.value = null
            }
        }
    }

    /**
     * «إلغاء التأجيل»: the occurrence returns to where it was, unresolved and
     * unanswered. It is not completed, not skipped, not paused and not deleted.
     */
    fun cancelSnooze() {
        viewModelScope.launch { scheduler.cancelSnooze(id) }
    }

    // ------------------------------------------------------------ the reminder

    /** «إيقاف مؤقت» / «استئناف»: the whole reminder. */
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
