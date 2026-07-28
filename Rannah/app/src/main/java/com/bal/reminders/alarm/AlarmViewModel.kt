package com.bal.reminders.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.SnoozeRequest
import com.bal.reminders.domain.SnoozeResult
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

/** «مدة أخرى» while it is open: the ceiling, and the last refusal if there was one. */
data class SnoozeOptions(
    val limit: Instant? = null,
    val rejected: Instant? = null,
)

data class AlarmScreenState(
    val reminder: Reminder? = null,
    val occurrenceAt: Instant = Instant.EPOCH,
    /** True once the user tapped «تم» and the deliberate slide-to-confirm is showing. */
    val confirming: Boolean = false,
    /** The current «مدة التأجيل الافتراضية», so the button can say what it will do. */
    val defaultSnoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    /** Non-null while the «مدة أخرى» sheet is open. */
    val snoozeOptions: SnoozeOptions? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val session = MutableStateFlow<Pair<Long, Instant>?>(null)
    private val confirming = MutableStateFlow(false)
    private val snoozeOptions = MutableStateFlow<SnoozeOptions?>(null)

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
        settingsRepository.settings,
        snoozeOptions,
    ) { reminder, (_, occurrence), isConfirming, settings, options ->
        AlarmScreenState(
            reminder = reminder,
            occurrenceAt = occurrence,
            confirming = isConfirming,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            snoozeOptions = options,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, AlarmScreenState())

    /** For the activity's stop-receiver: while confirming, an external stop must not close the screen. */
    val isConfirming = confirming.asStateFlow()

    fun load(reminderId: Long, occurrenceAt: Instant) {
        // A new occurrence replaces the session and starts back at the choices.
        if (session.value?.first != reminderId || session.value?.second != occurrenceAt) {
            confirming.value = false
            snoozeOptions.value = null
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

    /** «تأجيل»: the global default, applied now — not a number frozen into the reminder. */
    fun snooze() = resolveOnce { id, occurrence ->
        scheduler.snooze(id, occurrence, SnoozeRequest.Default)
    }

    /** «مدة أخرى»: work out how far this occurrence may be postponed, then offer the sheet. */
    fun openSnoozeOptions() {
        val (id, occurrence) = session.value ?: return
        viewModelScope.launch {
            snoozeOptions.value = SnoozeOptions(limit = scheduler.snoozeLimit(id, occurrence))
        }
    }

    fun dismissSnoozeOptions() {
        snoozeOptions.value = null
    }

    /**
     * A duration or a target time chosen from the sheet, for this occurrence only.
     * A refusal keeps the sheet open and says why, rather than quietly choosing a
     * different time than the one the user asked for.
     */
    fun applySnooze(request: SnoozeRequest) {
        val (id, occurrence) = session.value ?: return
        if (resolving) return
        viewModelScope.launch {
            when (val result = scheduler.snooze(id, occurrence, request)) {
                is SnoozeResult.Scheduled -> {
                    resolving = true
                    snoozeOptions.value = null
                    closed.tryEmit(Unit)
                }
                is SnoozeResult.TooLate ->
                    snoozeOptions.value = SnoozeOptions(limit = result.latest, rejected = result.latest)
                SnoozeResult.Unavailable -> {
                    snoozeOptions.value = null
                    closed.tryEmit(Unit)
                }
            }
        }
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
