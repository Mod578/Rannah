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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlarmScreenState(
    val reminder: Reminder? = null,
    val occurrenceAt: Instant = Instant.EPOCH,
    /** True once the user tapped «تم» and the deliberate slide-to-confirm is showing. */
    val confirming: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val session = MutableStateFlow<Pair<Long, Instant>?>(null)
    private val confirming = MutableStateFlow(false)

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
        confirming,
    ) { reminder, (_, occurrence), isConfirming ->
        AlarmScreenState(reminder = reminder, occurrenceAt = occurrence, confirming = isConfirming)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, AlarmScreenState())

    /** For the activity's stop-receiver: while confirming, an external stop must not close the screen. */
    val isConfirming = confirming.asStateFlow()

    fun load(reminderId: Long, occurrenceAt: Instant) {
        // A new occurrence replaces the session and starts back at the choices.
        if (session.value?.first != reminderId || session.value?.second != occurrenceAt) {
            confirming.value = false
            resolving = false
        }
        session.value = reminderId to occurrenceAt
    }

    /** «تم»: reveal the deliberate slide-to-confirm. Completion is never immediate. */
    fun beginConfirm() {
        confirming.value = true
    }

    /** Step back from the slide to the two choices. */
    fun cancelConfirm() {
        confirming.value = false
    }

    /** «تأجيل»: reversible; postpones the occurrence by the default snooze and closes. */
    fun snooze() = resolveOnce { id, occurrence ->
        scheduler.snooze(id, occurrenceAt = occurrence)
    }

    /** Completing the slide: the user asserts the real-world task is done. */
    fun markDone() = resolveOnce { id, occurrence ->
        scheduler.complete(id, occurrence)
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
}
