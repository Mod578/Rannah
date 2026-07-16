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
    /** The ringer is sounding: تأجيل، تم الإنجاز، إيقاف الصوت فقط. */
    RINGING,

    /**
     * The sound was stopped from this screen. Stopping is not completing, so
     * the screen still asks — «تم الإنجاز» (and «تخطي هذه المرة» when recurring).
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

    /**
     * One resolution per occurrence. The scheduler is already idempotent at the
     * record level, but this stops a second coroutine from being launched at all
     * by a double tap, a restored activity, or a re-delivered intent.
     */
    private var resolving = false

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
            resolving = false
        }
        session.value = reminderId to occurrenceAt
    }

    /** إيقاف الصوت فقط: silence the sound. Completion is always its own act. */
    fun stop() {
        val (id, occurrence) = session.value ?: return
        viewModelScope.launch {
            scheduler.stopAlarm(id, occurrence, askFollowUp = false)
            phase.value = AlarmPhase.STOPPED_PROMPT
        }
    }

    /** تأجيل: reversible, and the only action that moves the occurrence itself. */
    fun snooze(minutes: Int? = null) = resolveOnce { id, occurrence ->
        scheduler.snooze(id, minutes = minutes, occurrenceAt = occurrence)
    }

    /** تم الإنجاز: the user asserts the real-world task is done. */
    fun markDone() = resolveOnce { id, occurrence ->
        scheduler.complete(id, occurrence)
    }

    fun skipOnce() = resolveOnce { id, occurrence ->
        scheduler.skipOccurrence(id, occurrence)
    }

    private fun resolveOnce(action: suspend (Long, Instant) -> Unit) {
        val (id, occurrence) = session.value ?: return
        if (resolving) return
        resolving = true
        viewModelScope.launch {
            action(id, occurrence)
            closed.tryEmit(Unit)
        }
    }

    fun notYet() {
        closed.tryEmit(Unit)
    }
}
