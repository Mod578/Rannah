package com.bal.reminders.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.domain.RecurrenceCalculator
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.ReminderKind
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The shapes «متكرر» can take. «يومي» is deliberately not one of them: it is its own choice. */
enum class RecurrencePattern { WEEKLY, MONTHLY, YEARLY }

data class EditorState(
    val loaded: Boolean = false,
    val editingId: Long = 0L,
    val title: String = "",
    val notes: String = "",
    /** The first question: «ما نوع التذكير؟» */
    val kind: ReminderKind = ReminderKind.ONCE,
    /** Only asked when [kind] is [ReminderKind.RECURRING]. */
    val pattern: RecurrencePattern = RecurrencePattern.WEEKLY,
    val time: LocalTime = LocalTime.of(8, 0),
    val date: LocalDate = LocalDate.now(),
    val days: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    /** A schedule understood from the title, offered as a one-tap accelerator. */
    val parsedSchedule: Schedule? = null,
    val parsedTitle: String? = null,
    val titleError: Boolean = false,
    val pastError: Boolean = false,
    val daysError: Boolean = false,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = editingId == 0L

    /**
     * The schedule exactly as it would be saved, or null while invalid. Gregorian
     * only. Selecting all seven weekdays is «يومي» and is stored as such: one
     * representation per meaning, so the reminder reads back as the kind the user
     * actually described.
     */
    fun buildSchedule(): Schedule? = when (kind) {
        ReminderKind.ONCE -> Schedule.Once(date, time)
        ReminderKind.DAILY -> Schedule.Daily(time)
        ReminderKind.RECURRING -> when (pattern) {
            RecurrencePattern.WEEKLY -> when {
                days.isEmpty() -> null
                days.size == DayOfWeek.entries.size -> Schedule.Daily(time)
                else -> Schedule.Weekly(days, time)
            }
            RecurrencePattern.MONTHLY -> Schedule.Monthly(dayOfMonth.coerceIn(1, 31), time)
            RecurrencePattern.YEARLY ->
                Schedule.Yearly(month, dayOfMonth.coerceIn(1, monthLengthMax()), time)
        }
    }

    private fun monthLengthMax(): Int = java.time.Month.of(month.coerceIn(1, 12)).maxLength()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val parser: ReminderParser,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    /** Emitted once after a successful save. */
    val done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var original: Reminder? = null

    init {
        val id: Long = savedStateHandle["id"] ?: 0L
        viewModelScope.launch {
            if (id > 0) loadExisting(id) else loadDraft(savedStateHandle)
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

    private fun loadDraft(handle: SavedStateHandle) {
        val today = LocalDate.now(clock)
        _state.update {
            it.copy(
                loaded = true,
                title = handle.get<String>("title").orEmpty(),
                time = handle.get<String>("time")?.let(LocalTime::parse) ?: LocalTime.of(8, 0),
                date = handle.get<String>("date")?.let(LocalDate::parse) ?: today,
                dayOfMonth = today.dayOfMonth,
                month = today.monthValue,
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

    /** The first question. Changing it never silently keeps the previous answer's errors. */
    fun setKind(v: ReminderKind) =
        _state.update { it.copy(kind = v, pastError = false, daysError = false) }

    fun setPattern(v: RecurrencePattern) =
        _state.update { it.copy(pattern = v, pastError = false, daysError = false) }

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
        val weeklySelected = s.kind == ReminderKind.RECURRING &&
            s.pattern == RecurrencePattern.WEEKLY
        if (weeklySelected && s.days.isEmpty()) {
            _state.update { it.copy(daysError = true) }
            return
        }
        val schedule = s.buildSchedule() ?: run {
            _state.update { it.copy(daysError = weeklySelected) }
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

/** Maps a concrete Gregorian schedule onto the kind, the pattern and the pickers. */
private fun EditorState.applySchedule(s: Schedule): EditorState = copy(
    kind = s.kind,
    pattern = when (s) {
        is Schedule.Weekly -> RecurrencePattern.WEEKLY
        is Schedule.Monthly, is Schedule.HijriMonthly -> RecurrencePattern.MONTHLY
        is Schedule.Yearly, is Schedule.HijriYearly -> RecurrencePattern.YEARLY
        else -> pattern
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
