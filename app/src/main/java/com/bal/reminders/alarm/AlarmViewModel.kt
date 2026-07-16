package com.bal.reminders.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.scheduling.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the alarm screen is currently doing. */
enum class AlarmPhase {
    /** The ringer is sounding: إيقاف and تأجيل are the only choices. */
    RINGING,

    /**
     * The sound was stopped from this screen. Stopping is not completing, so
     * the screen now offers تم (and تخطي هذه المرة for recurring reminders).
     */
    STOPPED_PROMPT,
}

data class AlarmScreenState(
    val reminder: Reminder? = null,
    val occurrenceAt: Instant = Instant.EPOCH,
    val phase: AlarmPhase = AlarmPhase.RINGING,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val session = MutableStateFlow<Pair<Long, Instant>?>(null)
    private val phase = MutableStateFlow(AlarmPhase.RINGING)

    /** Emitted when the screen should close. */
    val closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state = combine(
        session.filterNotNull().flatMapLatest { (id, _) -> repository.observeById(id) },
        session.filterNotNull(),
        phase,
    ) { reminder, (_, occurrence), currentPhase ->
        AlarmScreenState(reminder = reminder, occurrenceAt = occurrence, phase = currentPhase)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlarmScreenState())

    val currentPhase = phase.asStateFlow()

    fun load(reminderId: Long, occurrenceAt: Instant) {
        // A new occurrence replaces the session and starts back at ringing.
        if (session.value?.first != reminderId || session.value?.second != occurrenceAt) {
            phase.value = AlarmPhase.RINGING
        }
        session.value = reminderId to occurrenceAt
    }

    /** إيقاف: silence the sound. Completion stays a separate, explicit choice. */
    fun stop() {
        val (id, occurrence) = session.value ?: return
        val marksCompleted = state.value.reminder?.stopMarksCompleted == true
        viewModelScope.launch {
            scheduler.stopAlarm(id, occurrence, askFollowUp = false)
            if (marksCompleted) {
                closed.tryEmit(Unit)
            } else {
                phase.value = AlarmPhase.STOPPED_PROMPT
            }
        }
    }

    fun snooze() {
        val (id, occurrence) = session.value ?: return
        viewModelScope.launch {
            scheduler.snooze(id, occurrenceAt = occurrence)
            closed.tryEmit(Unit)
        }
    }

    fun markDone() {
        val (id, occurrence) = session.value ?: return
        viewModelScope.launch {
            scheduler.complete(id, occurrence)
            closed.tryEmit(Unit)
        }
    }

    fun skipOnce() {
        val (id, occurrence) = session.value ?: return
        viewModelScope.launch {
            scheduler.skipOccurrence(id, occurrence)
            closed.tryEmit(Unit)
        }
    }

    fun notYet() {
        closed.tryEmit(Unit)
    }
}
