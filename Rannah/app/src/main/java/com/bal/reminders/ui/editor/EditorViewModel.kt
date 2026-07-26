package com.bal.reminders.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import com.bal.reminders.parser.ParseResult
import com.bal.reminders.parser.ReminderParser
import com.bal.reminders.scheduling.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScheduleType { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

data class EditorState(
    val loaded: Boolean = false,
    val editingId: Long = 0L,
    val title: String = "",
    val notes: String = "",
    val type: ScheduleType = ScheduleType.ONCE,
    val time: LocalTime = LocalTime.of(8, 0),
    val date: LocalDate = LocalDate.now(),
    val days: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    /** A schedule understood from the title, offered as a one-tap accelerator. */
    val parsedSchedule: Schedule? = null,
    val parsedTitle: String? = null,
    val titleError: Boolean = false,
    val pastError: Boolean = false,
    val daysError: Boolean = false,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = editingId == 0L

    /** The schedule exactly as it would be saved, or null while invalid. Gregorian only. */
    fun buildSchedule(): Schedule? = when (type) {
        ScheduleType.ONCE -> Schedule.Once(date, time)
        ScheduleType.DAILY -> Schedule.Daily(time)
        ScheduleType.WEEKLY -> if (days.isEmpty()) null else Schedule.Weekly(days, time)
        ScheduleType.MONTHLY -> Schedule.Monthly(dayOfMonth.coerceIn(1, 31), time)
        ScheduleType.YEARLY -> Schedule.Yearly(month, dayOfMonth.coerceIn(1, monthLengthMax()), time)
    }

    private fun monthLengthMax(): Int = java.time.Month.of(month.coerceIn(1, 12)).maxLength()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val parser: ReminderParser,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    /** Emitted once after a successful save or delete. */
    val done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var original: Reminder? = null

    init {
        val id: Long = savedStateHandle["id"] ?: 0L
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (id > 0) loadExisting(id) else loadDraft(savedStateHandle, settings.defaultSnoozeMinutes)
        }
    }

    private suspend fun loadExisting(id: Long) {
        val reminder = repository.getById(id)
        if (reminder == null) {
            _state.update { it.copy(loaded = true) }
            return
        }
        original = reminder
        // A reminder saved before the Gregorian-only switch keeps firing on its
        // Hijri dates, but the editor is Gregorian: show its Gregorian equivalent.
        val schedule = reminder.schedule.toGregorian()
        _state.update {
            it.applySchedule(schedule).copy(
                loaded = true,
                editingId = id,
                title = reminder.title,
                notes = reminder.notes.orEmpty(),
                snoozeMinutes = reminder.snoozeMinutes,
            )
        }
    }

    /** Maps a (possibly Hijri) stored schedule to the nearest Gregorian one for editing. */
    private fun Schedule.toGregorian(): Schedule {
        val now = ZonedDateTime.now(clock)
        return when (this) {
            is Schedule.OnceHijri -> {
                val next = RecurrenceCalculator.nextOccurrence(this, now)?.toLocalDate()
                    ?: now.toLocalDate()
                Schedule.Once(next, time)
            }
            is Schedule.HijriMonthly -> Schedule.Monthly(dayOfMonth.coerceIn(1, 31), time)
            is Schedule.HijriYearly -> {
                val next = RecurrenceCalculator.nextOccurrence(this, now)?.toLocalDate()
                if (next != null) Schedule.Yearly(next.monthValue, next.dayOfMonth, time)
                else Schedule.Yearly(now.monthValue, now.dayOfMonth, time)
            }
            else -> this
        }
    }

    private fun loadDraft(handle: SavedStateHandle, defaultSnooze: Int) {
        val today = LocalDate.now(clock)
        val type = when (handle.get<String>("type")) {
            "daily" -> ScheduleType.DAILY
            "weekly" -> ScheduleType.WEEKLY
            "monthly" -> ScheduleType.MONTHLY
            "yearly" -> ScheduleType.YEARLY
            else -> ScheduleType.ONCE
        }
        _state.update {
            it.copy(
                loaded = true,
                title = handle.get<String>("title").orEmpty(),
                type = type,
                time = handle.get<String>("time")?.let(LocalTime::parse) ?: LocalTime.of(8, 0),
                date = handle.get<String>("date")?.let(LocalDate::parse) ?: today,
                days = handle.get<String>("days")
                    ?.split(",")
                    ?.mapNotNull { v -> v.toIntOrNull()?.let(DayOfWeek::of) }
                    ?.toSet()
                    ?: emptySet(),
                dayOfMonth = handle.get<String>("dom")?.toIntOrNull() ?: today.dayOfMonth,
                month = handle.get<String>("month")?.toIntOrNull() ?: today.monthValue,
                snoozeMinutes = defaultSnooze,
            )
        }
    }

    // -------------------------------------------------------------- updates

    /** Sets the title and, quietly, parses it for a schedule to offer. */
    fun setTitle(v: String) {
        val parsed = when (val r = parser.parse(v.trim(), ZonedDateTime.now(clock))) {
            is ParseResult.Success -> r.title to r.schedule
            else -> null
        }
        _state.update {
            it.copy(
                title = v,
                titleError = false,
                parsedTitle = parsed?.first,
                parsedSchedule = parsed?.second,
            )
        }
    }

    /** Applies the understood schedule (and its cleaner title) to the pickers. */
    fun applyParsed() {
        val s = _state.value
        val schedule = s.parsedSchedule ?: return
        _state.update {
            it.applySchedule(schedule).copy(
                title = s.parsedTitle ?: it.title,
                parsedSchedule = null,
                parsedTitle = null,
                titleError = false,
                pastError = false,
                daysError = false,
            )
        }
    }

    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun setType(v: ScheduleType) =
        _state.update { it.copy(type = v, pastError = false, daysError = false) }

    fun setTime(v: LocalTime) = _state.update { it.copy(time = v, pastError = false) }
    fun setDate(v: LocalDate) = _state.update { it.copy(date = v, pastError = false) }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val days = if (day in it.days) it.days - day else it.days + day
        it.copy(days = days, daysError = false)
    }

    fun setDayOfMonth(v: Int) = _state.update { it.copy(dayOfMonth = v.coerceIn(1, 31)) }

    fun setMonth(v: Int) = _state.update { it.copy(month = v.coerceIn(1, 12)) }

    // ----------------------------------------------------------------- save

    fun save() {
        val s = _state.value
        if (s.saving) return

        val title = s.title.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(titleError = true) }
            return
        }
        if (s.type == ScheduleType.WEEKLY && s.days.isEmpty()) {
            _state.update { it.copy(daysError = true) }
            return
        }
        val schedule = s.buildSchedule() ?: run {
            _state.update { it.copy(daysError = s.type == ScheduleType.WEEKLY) }
            return
        }
        if (schedule is Schedule.Once &&
            !schedule.date.atTime(schedule.time).atZone(clock.zone).isAfter(ZonedDateTime.now(clock))
        ) {
            _state.update { it.copy(pastError = true) }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val base = original
                scheduler.save(
                    Reminder(
                        id = s.editingId,
                        title = title,
                        notes = s.notes.trim().ifEmpty { null },
                        schedule = schedule,
                        // Editing changes what was edited and nothing else: a
                        // paused reminder stays paused (with its new schedule
                        // shown as what «استئناف» will return to), rather than
                        // silently starting to ring again.
                        enabled = base?.enabled ?: true,
                        snoozeMinutes = s.snoozeMinutes,
                        createdAt = base?.createdAt ?: clock.instant(),
                        // A completed reminder is never editable — details offers
                        // «تراجع» or «حذف» instead — so this is always already null.
                        completedAt = null,
                    ),
                )
                done.tryEmit(Unit)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/** Maps a concrete Gregorian schedule onto the granular picker fields. */
private fun EditorState.applySchedule(s: Schedule): EditorState = copy(
    type = when (s) {
        is Schedule.Once, is Schedule.OnceHijri -> ScheduleType.ONCE
        is Schedule.Daily -> ScheduleType.DAILY
        is Schedule.Weekly -> ScheduleType.WEEKLY
        is Schedule.Monthly, is Schedule.HijriMonthly -> ScheduleType.MONTHLY
        is Schedule.Yearly, is Schedule.HijriYearly -> ScheduleType.YEARLY
    },
    time = s.time,
    date = (s as? Schedule.Once)?.date ?: date,
    days = (s as? Schedule.Weekly)?.days ?: emptySet(),
    dayOfMonth = when (s) {
        is Schedule.Monthly -> s.dayOfMonth
        is Schedule.Yearly -> s.day
        else -> dayOfMonth
    },
    month = when (s) {
        is Schedule.Yearly -> s.month
        else -> month
    },
)
