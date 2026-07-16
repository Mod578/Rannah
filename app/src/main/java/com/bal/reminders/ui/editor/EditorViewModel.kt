package com.bal.reminders.ui.editor

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bal.reminders.data.PersonalSuggestions
import com.bal.reminders.data.PersonalizationRepository
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.domain.HijriDates
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Priority
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import com.bal.reminders.scheduling.ReminderScheduler
import com.bal.reminders.ui.templates.Templates
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScheduleType { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** A local system alarm sound the user can pick and preview. */
data class RingtoneChoice(val title: String, val uri: String)

/** Where an alert-mode suggestion came from; shown so it stays transparent. */
enum class SuggestionSource { CATEGORY, LEARNED }

data class EditorState(
    val loaded: Boolean = false,
    val editingId: Long = 0L,
    val title: String = "",
    val notes: String = "",
    val category: Category = Category.PERSONAL,
    val priority: Priority = Priority.NORMAL,
    val type: ScheduleType = ScheduleType.ONCE,
    val calendar: CalendarSystem = CalendarSystem.GREGORIAN,
    val time: LocalTime = LocalTime.of(8, 0),
    val date: LocalDate = LocalDate.now(),
    val hijriYear: Int = 1448,
    val hijriMonth: Int = 1,
    val hijriDay: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    val alertMode: AlertMode = AlertMode.STANDARD,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val ringtones: List<RingtoneChoice> = emptyList(),
    val ringtoneUri: String? = null,
    val alarmTimeoutMinutes: Int = Reminder.DEFAULT_ALARM_TIMEOUT_MINUTES,
    val alarmGradualVolume: Boolean = true,
    val alarmRepeatIfIgnored: Boolean = false,
    val stopMarksCompleted: Boolean = false,
    val moreExpanded: Boolean = false,
    val hijriAdjustmentDays: Int = 0,
    val suggestedAlertMode: AlertMode? = null,
    val suggestionSource: SuggestionSource? = null,
    val suggestionDismissed: Boolean = false,
    val previewingRingtone: Boolean = false,
    val titleError: Boolean = false,
    val pastError: Boolean = false,
    val daysError: Boolean = false,
    val hijriRangeError: Boolean = false,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = editingId == 0L

    /** Calendar choice only matters for date-bearing schedule types. */
    val calendarApplies: Boolean
        get() = type == ScheduleType.ONCE || type == ScheduleType.MONTHLY || type == ScheduleType.YEARLY

    val showSuggestion: Boolean
        get() = suggestedAlertMode != null && suggestedAlertMode != alertMode && !suggestionDismissed

    /** The schedule exactly as it would be saved, or null while invalid. */
    fun buildSchedule(): Schedule? = when (type) {
        ScheduleType.ONCE ->
            if (calendar == CalendarSystem.HIJRI) {
                Schedule.OnceHijri(hijriYear, hijriMonth, hijriDay.coerceIn(1, 30), time)
            } else {
                Schedule.Once(date, time)
            }
        ScheduleType.DAILY -> Schedule.Daily(time)
        ScheduleType.WEEKLY -> if (days.isEmpty()) null else Schedule.Weekly(days, time)
        ScheduleType.MONTHLY ->
            if (calendar == CalendarSystem.HIJRI) {
                Schedule.HijriMonthly(dayOfMonth.coerceIn(1, 30), time)
            } else {
                Schedule.Monthly(dayOfMonth.coerceIn(1, 31), time)
            }
        ScheduleType.YEARLY ->
            if (calendar == CalendarSystem.HIJRI) {
                Schedule.HijriYearly(month, dayOfMonth.coerceIn(1, 30), time)
            } else {
                Schedule.Yearly(month, dayOfMonth.coerceIn(1, monthLengthMax()), time)
            }
    }

    private fun monthLengthMax(): Int = java.time.Month.of(month.coerceIn(1, 12)).maxLength()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val personalization: PersonalizationRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    /** Emitted once after a successful save or delete. */
    val done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var original: Reminder? = null
    private var preview: Ringtone? = null

    init {
        val id: Long = savedStateHandle["id"] ?: 0L
        val templateId: String? = savedStateHandle["template"]
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            seedHijriToday(settings.hijriAdjustmentDays)
            when {
                id > 0 -> loadExisting(id)
                templateId != null -> loadTemplate(templateId, settings.defaultSnoozeMinutes)
                else -> loadDraft(savedStateHandle, settings.defaultSnoozeMinutes)
            }
            _state.update { it.copy(hijriAdjustmentDays = settings.hijriAdjustmentDays) }
            loadRingtones()
            refreshSuggestion()
        }
    }

    private fun seedHijriToday(adjustmentDays: Int) {
        val today = HijriDates.fromGregorian(LocalDate.now(clock), adjustmentDays) ?: return
        _state.update {
            it.copy(
                hijriYear = today.get(ChronoField.YEAR),
                hijriMonth = today.get(ChronoField.MONTH_OF_YEAR),
                hijriDay = today.get(ChronoField.DAY_OF_MONTH),
            )
        }
    }

    private suspend fun loadExisting(id: Long) {
        val reminder = repository.getById(id)
        if (reminder == null) {
            _state.update { it.copy(loaded = true) }
            return
        }
        original = reminder
        val s = reminder.schedule
        val today = LocalDate.now(clock)
        _state.update {
            it.copy(
                loaded = true,
                editingId = id,
                title = reminder.title,
                notes = reminder.notes.orEmpty(),
                category = reminder.category,
                priority = reminder.priority,
                type = s.toType(),
                calendar = s.calendar,
                time = s.time,
                date = (s as? Schedule.Once)?.date ?: today,
                hijriYear = (s as? Schedule.OnceHijri)?.year ?: it.hijriYear,
                hijriMonth = (s as? Schedule.OnceHijri)?.month ?: it.hijriMonth,
                hijriDay = (s as? Schedule.OnceHijri)?.day ?: it.hijriDay,
                days = (s as? Schedule.Weekly)?.days ?: emptySet(),
                dayOfMonth = when (s) {
                    is Schedule.Monthly -> s.dayOfMonth
                    is Schedule.HijriMonthly -> s.dayOfMonth
                    is Schedule.Yearly -> s.day
                    is Schedule.HijriYearly -> s.day
                    else -> today.dayOfMonth
                },
                month = when (s) {
                    is Schedule.Yearly -> s.month
                    is Schedule.HijriYearly -> s.month
                    else -> today.monthValue
                },
                alertMode = reminder.alertMode,
                soundEnabled = reminder.soundEnabled,
                vibrationEnabled = reminder.vibrationEnabled,
                snoozeMinutes = reminder.snoozeMinutes,
                ringtoneUri = reminder.ringtoneUri,
                alarmTimeoutMinutes = reminder.alarmTimeoutMinutes,
                alarmGradualVolume = reminder.alarmGradualVolume,
                alarmRepeatIfIgnored = reminder.alarmRepeatIfIgnored,
                stopMarksCompleted = reminder.stopMarksCompleted,
            )
        }
    }

    private fun loadTemplate(templateId: String, defaultSnooze: Int) {
        val template = Templates.firstOrNull { it.id == templateId }
        val today = LocalDate.now(clock)
        if (template == null) {
            _state.update { it.copy(loaded = true, snoozeMinutes = defaultSnooze) }
            return
        }
        val schedule = template.schedule(today)
        _state.update {
            it.copy(
                loaded = true,
                title = appContext.getString(template.titleRes),
                category = template.category,
                type = schedule.toType(),
                calendar = schedule.calendar,
                time = schedule.time,
                date = (schedule as? Schedule.Once)?.date ?: today,
                days = (schedule as? Schedule.Weekly)?.days ?: emptySet(),
                dayOfMonth = (schedule as? Schedule.Monthly)?.dayOfMonth ?: today.dayOfMonth,
                snoozeMinutes = defaultSnooze,
            )
        }
    }

    private fun loadDraft(handle: SavedStateHandle, defaultSnooze: Int) {
        val today = LocalDate.now(clock)
        val type = when (handle.get<String>("type")) {
            "daily" -> ScheduleType.DAILY
            "weekly" -> ScheduleType.WEEKLY
            "monthly", "hijri_monthly" -> ScheduleType.MONTHLY
            "yearly", "hijri_yearly" -> ScheduleType.YEARLY
            else -> ScheduleType.ONCE
        }
        val hijri = handle.get<String>("type")?.startsWith("hijri") == true ||
            handle.get<String>("cal") == CalendarSystem.HIJRI.id
        _state.update {
            it.copy(
                loaded = true,
                title = handle.get<String>("title").orEmpty(),
                type = type,
                calendar = if (hijri) CalendarSystem.HIJRI else CalendarSystem.GREGORIAN,
                time = handle.get<String>("time")?.let(LocalTime::parse) ?: LocalTime.of(8, 0),
                date = handle.get<String>("date")?.let(LocalDate::parse) ?: today,
                hijriYear = handle.get<String>("hy")?.toIntOrNull() ?: it.hijriYear,
                hijriMonth = handle.get<String>("hm")?.toIntOrNull() ?: it.hijriMonth,
                hijriDay = handle.get<String>("hd")?.toIntOrNull() ?: it.hijriDay,
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

    private suspend fun loadRingtones() {
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val manager = RingtoneManager(appContext).apply { setType(RingtoneManager.TYPE_ALARM) }
                val cursor = manager.cursor
                buildList {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        add(RingtoneChoice(title, manager.getRingtoneUri(cursor.position).toString()))
                    }
                }
            }.getOrDefault(emptyList())
        }
        _state.update { it.copy(ringtones = list) }
    }

    /**
     * Transparent alert-mode suggestion: learned per-category usage first,
     * a static category rule (medication/work-style reminders ring better as
     * alarms) as fallback. Never auto-applied; the user taps to accept.
     */
    private suspend fun refreshSuggestion() {
        if (!_state.value.isNew) return
        val category = _state.value.category
        val learned: PersonalSuggestions = personalization.suggestionsFor(category).first()
        val (suggested, source) = when {
            learned.alertMode != null -> learned.alertMode to SuggestionSource.LEARNED
            category == Category.HEALTH || category == Category.WORK ->
                AlertMode.ALARM to SuggestionSource.CATEGORY
            else -> null to null
        }
        _state.update {
            it.copy(suggestedAlertMode = suggested, suggestionSource = source)
        }
    }

    // -------------------------------------------------------------- updates

    fun setTitle(v: String) = _state.update { it.copy(title = v, titleError = false) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun setCategory(v: Category) {
        _state.update { it.copy(category = v, suggestionDismissed = false) }
        viewModelScope.launch { refreshSuggestion() }
    }

    fun setPriority(high: Boolean) =
        _state.update { it.copy(priority = if (high) Priority.HIGH else Priority.NORMAL) }

    fun setType(v: ScheduleType) =
        _state.update { it.copy(type = v, pastError = false, daysError = false, hijriRangeError = false) }

    fun setTime(v: LocalTime) = _state.update { it.copy(time = v, pastError = false) }
    fun setDate(v: LocalDate) = _state.update { it.copy(date = v, pastError = false) }

    /**
     * Switching the calendar system converts the currently chosen one-time
     * date so the same civil day stays selected; the stored semantics switch
     * to the newly chosen calendar from here on.
     */
    fun setCalendar(v: CalendarSystem) {
        val s = _state.value
        if (s.calendar == v) return
        if (s.type == ScheduleType.ONCE) {
            if (v == CalendarSystem.HIJRI) {
                val hijri = HijriDates.fromGregorian(s.date, s.hijriAdjustmentDays)
                if (hijri != null) {
                    _state.update {
                        it.copy(
                            calendar = v,
                            hijriYear = hijri.get(ChronoField.YEAR),
                            hijriMonth = hijri.get(ChronoField.MONTH_OF_YEAR),
                            hijriDay = hijri.get(ChronoField.DAY_OF_MONTH),
                            pastError = false,
                            hijriRangeError = false,
                        )
                    }
                    return
                }
            } else {
                val civil = HijriDates.toGregorian(
                    s.hijriYear, s.hijriMonth, s.hijriDay, s.hijriAdjustmentDays,
                )
                if (civil != null) {
                    _state.update {
                        it.copy(calendar = v, date = civil, pastError = false, hijriRangeError = false)
                    }
                    return
                }
            }
        }
        _state.update { it.copy(calendar = v, pastError = false, hijriRangeError = false) }
    }

    fun setHijriDate(year: Int, month: Int, day: Int) {
        val maxDay = HijriDates.monthLength(year, month) ?: 30
        _state.update {
            it.copy(
                hijriYear = year,
                hijriMonth = month.coerceIn(1, 12),
                hijriDay = day.coerceIn(1, maxDay),
                pastError = false,
                hijriRangeError = false,
            )
        }
    }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val days = if (day in it.days) it.days - day else it.days + day
        it.copy(days = days, daysError = false)
    }

    fun setDayOfMonth(v: Int) = _state.update {
        val max = if (it.calendar == CalendarSystem.HIJRI) 30 else 31
        it.copy(dayOfMonth = v.coerceIn(1, max))
    }

    fun setMonth(v: Int) = _state.update { it.copy(month = v.coerceIn(1, 12)) }

    fun setAlertMode(v: AlertMode) = _state.update { it.copy(alertMode = v) }

    fun applySuggestion() {
        val suggested = _state.value.suggestedAlertMode ?: return
        _state.update { it.copy(alertMode = suggested, suggestionDismissed = true) }
    }

    fun dismissSuggestion() = _state.update { it.copy(suggestionDismissed = true) }

    fun setMoreExpanded(v: Boolean) = _state.update { it.copy(moreExpanded = v) }
    fun setSound(v: Boolean) = _state.update { it.copy(soundEnabled = v) }
    fun setVibration(v: Boolean) = _state.update { it.copy(vibrationEnabled = v) }
    fun setSnooze(v: Int) = _state.update { it.copy(snoozeMinutes = v) }
    fun setRingtone(uri: String?) = _state.update { it.copy(ringtoneUri = uri) }
    fun setAlarmTimeout(v: Int) = _state.update { it.copy(alarmTimeoutMinutes = v.coerceIn(1, 30)) }
    fun setGradualVolume(v: Boolean) = _state.update { it.copy(alarmGradualVolume = v) }
    fun setRepeatIfIgnored(v: Boolean) = _state.update { it.copy(alarmRepeatIfIgnored = v) }
    fun setStopMarksCompleted(v: Boolean) = _state.update { it.copy(stopMarksCompleted = v) }

    // -------------------------------------------------------------- preview

    /** Plays the chosen (or default) alarm sound once so the user hears it before saving. */
    fun previewRingtone() {
        stopPreview()
        val uri = _state.value.ringtoneUri?.let(android.net.Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        preview = runCatching {
            RingtoneManager.getRingtone(appContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }.getOrNull()
        _state.update { it.copy(previewingRingtone = preview != null) }
    }

    fun stopPreview() {
        preview?.let { runCatching { it.stop() } }
        preview = null
        _state.update { it.copy(previewingRingtone = false) }
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }

    // ----------------------------------------------------------------- save

    fun save() {
        val s = _state.value
        if (s.saving) return // double-tap guard

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
        when (schedule) {
            is Schedule.Once -> {
                val at = schedule.date.atTime(schedule.time).atZone(clock.zone)
                if (!at.isAfter(ZonedDateTime.now(clock))) {
                    _state.update { it.copy(pastError = true) }
                    return
                }
            }

            is Schedule.OnceHijri -> {
                val civil = HijriDates.toGregorian(
                    schedule.year, schedule.month, schedule.day, s.hijriAdjustmentDays,
                )
                if (civil == null) {
                    _state.update { it.copy(hijriRangeError = true) }
                    return
                }
                val at = civil.atTime(schedule.time).atZone(clock.zone)
                if (!at.isAfter(ZonedDateTime.now(clock))) {
                    _state.update { it.copy(pastError = true) }
                    return
                }
            }

            else -> Unit
        }

        stopPreview()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val base = original
                val reminder = Reminder(
                    id = s.editingId,
                    title = title,
                    notes = s.notes.trim().ifEmpty { null },
                    category = s.category,
                    priority = s.priority,
                    schedule = schedule,
                    enabled = true,
                    alertMode = s.alertMode,
                    soundEnabled = s.soundEnabled,
                    vibrationEnabled = s.vibrationEnabled,
                    snoozeMinutes = s.snoozeMinutes,
                    ringtoneUri = s.ringtoneUri,
                    alarmTimeoutMinutes = s.alarmTimeoutMinutes,
                    alarmGradualVolume = s.alarmGradualVolume,
                    alarmRepeatIfIgnored = s.alarmRepeatIfIgnored,
                    stopMarksCompleted = s.stopMarksCompleted,
                    createdAt = base?.createdAt ?: clock.instant(),
                    completedAt = null, // editing re-activates a completed/ended one
                )
                scheduler.save(reminder)
                personalization.recordSave(reminder)
                done.tryEmit(Unit)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        val id = _state.value.editingId
        if (id == 0L) return
        viewModelScope.launch {
            scheduler.delete(id)
            done.tryEmit(Unit)
        }
    }
}

private fun Schedule.toType(): ScheduleType = when (this) {
    is Schedule.Once, is Schedule.OnceHijri -> ScheduleType.ONCE
    is Schedule.Daily -> ScheduleType.DAILY
    is Schedule.Weekly -> ScheduleType.WEEKLY
    is Schedule.Monthly, is Schedule.HijriMonthly -> ScheduleType.MONTHLY
    is Schedule.Yearly, is Schedule.HijriYearly -> ScheduleType.YEARLY
}
