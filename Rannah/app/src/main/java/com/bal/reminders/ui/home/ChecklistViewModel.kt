package com.bal.reminders.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.R
import com.bal.reminders.domain.OccurrenceStateResolver
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderOccurrence
import com.bal.reminders.domain.ReminderPhase
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.OccurrenceRecord
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
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
    /** Everything due today: waiting, postponed, and still to come — in time order. */
    val today: List<ReminderOccurrence> = emptyList(),
    val upcoming: List<ReminderOccurrence> = emptyList(),
    val closed: List<ClosedItem> = emptyList(),
    val paused: List<ReminderOccurrence> = emptyList(),
    val hasAnyReminder: Boolean = false,
) {
    /** Reminders exist, but nothing is waiting today. */
    val nothingToday: Boolean get() = hasAnyReminder && today.isEmpty()
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
        build(reminders, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistState())

    private fun build(
        reminders: List<Reminder>,
        records: List<OccurrenceRecord>,
    ): ChecklistState {
        val now = clock.instant()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()

        // reminderId -> occurrences already answered (completed or skipped).
        val resolved = HashMap<Long, HashSet<Long>>()
        records.forEach { r ->
            if (r.status.resolvesOccurrence) {
                resolved.getOrPut(r.reminderId) { HashSet() }.add(r.occurrenceAt.toEpochMilli())
            }
        }
        val closedRecords = records.filter {
            it.status.resolvesOccurrence && it.recordedAt.atZone(zone).toLocalDate() == today
        }
        val closedIds = closedRecords.map { it.reminderId }.toHashSet()
        val byId = reminders.associateBy { it.id }

        val todayList = ArrayList<ReminderOccurrence>()
        val upcoming = ArrayList<ReminderOccurrence>()
        val paused = ArrayList<ReminderOccurrence>()

        reminders.forEach { reminder ->
            val ids = resolved[reminder.id]
            val view = OccurrenceStateResolver.resolve(reminder, now, zone) { occ ->
                ids?.contains(occ.toEpochMilli()) == true
            }
            when (view.phase) {
                ReminderPhase.COMPLETED -> Unit // one-time done → listed from its record below
                ReminderPhase.PAUSED -> paused += view
                ReminderPhase.NEEDS_CONFIRMATION, ReminderPhase.SNOOZED -> todayList += view
                ReminderPhase.UPCOMING -> {
                    val at = view.displayAt
                    when {
                        reminder.id in closedIds -> Unit // finished for today; not listed twice
                        at != null && at.atZone(zone).toLocalDate() == today -> todayList += view
                        else -> upcoming += view
                    }
                }
            }
        }

        val closed = closedRecords
            .sortedByDescending { it.recordedAt }
            .map { record ->
                val reminder = byId[record.reminderId]
                ClosedItem(
                    reminderId = record.reminderId,
                    title = record.reminderTitle,
                    occurrenceAt = record.occurrenceAt,
                    status = record.status,
                    returnsAt = reminder?.takeIf { it.schedule.isRecurring && it.enabled }
                        ?.let { nextAfterToday(it.schedule, now) },
                )
            }

        return ChecklistState(
            // What is waiting rides above what is merely scheduled; inside each
            // group, the clock decides.
            today = todayList.sortedWith(
                compareBy({ it.phase != ReminderPhase.NEEDS_CONFIRMATION }, { it.displayAt }),
            ),
            upcoming = upcoming.sortedBy { it.displayAt },
            closed = closed,
            paused = paused.sortedBy { it.title },
            hasAnyReminder = reminders.isNotEmpty(),
        )
    }

    private fun nextAfterToday(schedule: Schedule, now: Instant): Instant? =
        RecurrenceCalculator.nextOccurrence(schedule, now.atZone(clock.zone))?.toInstant()

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
     * «تخطي اليوم» on a repeating row: today's occurrence is closed without
     * claiming it was done, and the reminder keeps every day after it.
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
