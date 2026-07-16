package com.bal.reminders.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.data.DateDisplay
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import com.bal.reminders.parser.ParseResult
import com.bal.reminders.parser.ReminderParser
import com.bal.reminders.scheduling.ReminderScheduler
import com.bal.reminders.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The parse preview shown before saving. */
data class ParsePreview(
    val title: String,
    val schedule: Schedule,
)

data class HomeState(
    val next: Reminder? = null,
    val today: List<Reminder> = emptyList(),
    val overdue: List<Reminder> = emptyList(),
    val hasAnyReminder: Boolean = false,
    val input: String = "",
    val preview: ParsePreview? = null,
    val parseFailed: Boolean = false,
    val saving: Boolean = false,
)

sealed interface HomeEvent {
    data class OpenEditor(val route: String) : HomeEvent
    data object Saved : HomeEvent

    /** A completion happened; [undo] reverses it while the snackbar shows. */
    data class UndoableComplete(val title: String, val undo: suspend () -> Unit) : HomeEvent
}

/** Calendar preference for the greeting's date lines. */
data class DateState(
    val display: DateDisplay = DateDisplay.BOTH,
    val hijriAdjustmentDays: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: ReminderRepository,
    private val parser: ReminderParser,
    private val scheduler: ReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val inputState = MutableStateFlow("")
    private val previewState = MutableStateFlow<ParsePreview?>(null)
    private val parseFailedState = MutableStateFlow(false)
    private val savingState = MutableStateFlow(false)

    val events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)

    val state = combine(
        repository.observeAll(),
        inputState,
        previewState,
        parseFailedState,
        savingState,
    ) { reminders, input, preview, parseFailed, saving ->
        val now = clock.instant()
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val active = reminders.filter { it.enabled && !it.isDone }
        val upcoming = active
            .filter { it.nextTriggerAt != null && it.nextTriggerAt!!.isAfter(now) }
            .sortedBy { it.nextTriggerAt }
        val todayList = upcoming.filter {
            it.nextTriggerAt!!.atZone(zone).toLocalDate() == today
        }
        val overdue = reminders.filter { reminder ->
            val s = reminder.schedule
            s is Schedule.Once && !reminder.isDone && reminder.enabled &&
                s.date.atTime(s.time).atZone(zone).toInstant() <= now
        }.sortedByDescending { (it.schedule as Schedule.Once).date }
        HomeState(
            next = upcoming.firstOrNull(),
            today = todayList,
            overdue = overdue,
            hasAnyReminder = reminders.isNotEmpty(),
            input = input,
            preview = preview,
            parseFailed = parseFailed,
            saving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val dateState = settingsRepository.settings
        .map { DateState(it.dateDisplay, it.hijriAdjustmentDays) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DateState())

    fun onInputChange(text: String) {
        inputState.value = text
        parseFailedState.value = false
        if (text.isBlank()) previewState.value = null
    }

    fun onSubmitInput() {
        val text = inputState.value.trim()
        if (text.isEmpty()) return
        when (val result = parser.parse(text, ZonedDateTime.now(clock))) {
            is ParseResult.Success -> {
                previewState.value = ParsePreview(result.title, result.schedule)
                parseFailedState.value = false
            }

            is ParseResult.Incomplete -> {
                previewState.value = null
                viewModelScope.launch {
                    events.emit(
                        HomeEvent.OpenEditor(
                            Routes.editorDraft(result.draft.title, result.draft.schedule),
                        ),
                    )
                }
            }

            ParseResult.NoMatch -> parseFailedState.value = true
        }
    }

    fun onEditPreview() {
        val preview = previewState.value ?: return
        previewState.value = null
        viewModelScope.launch {
            events.emit(HomeEvent.OpenEditor(Routes.editorDraft(preview.title, preview.schedule)))
        }
    }

    fun onDismissPreview() {
        previewState.value = null
    }

    fun onConfirmPreview() {
        val preview = previewState.value ?: return
        if (savingState.value) return // double-tap guard
        savingState.value = true
        viewModelScope.launch {
            try {
                val defaultSnooze = settingsRepository.settings.first().defaultSnoozeMinutes
                scheduler.save(
                    Reminder(
                        title = preview.title,
                        category = guessCategory(preview.title),
                        schedule = preview.schedule,
                        snoozeMinutes = defaultSnooze,
                        createdAt = clock.instant(),
                    ),
                )
                previewState.value = null
                inputState.value = ""
                events.emit(HomeEvent.Saved)
            } finally {
                savingState.value = false
            }
        }
    }

    fun complete(reminder: Reminder) {
        viewModelScope.launch {
            val occurrence = scheduler.complete(reminder.id) ?: return@launch
            events.emit(
                HomeEvent.UndoableComplete(reminder.title) {
                    scheduler.undoComplete(reminder.id, occurrence)
                },
            )
        }
    }

    fun undo(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }

    fun snooze(reminder: Reminder) {
        viewModelScope.launch { scheduler.snooze(reminder.id) }
    }

    /** Light keyword → category mapping for quick NL creation. */
    private fun guessCategory(title: String): Category {
        val t = title
        return when {
            listOf("دوام", "اجتماع", "بصم", "عمل", "شغل", "مشروع").any { t.contains(it) } -> Category.WORK
            listOf("دواء", "علاج", "حبوب", "ماء", "رياضه", "رياضة", "نادي", "تمرين").any { t.contains(it) } -> Category.HEALTH
            listOf("فاتور", "دفع", "سداد", "اشتراك", "ايجار", "إيجار").any { t.contains(it) } -> Category.BILLS
            listOf("مذاكر", "دراس", "اختبار", "محاضر", "واجب").any { t.contains(it) } -> Category.STUDY
            listOf("امي", "أمي", "ابوي", "أبوي", "اهل", "أهل", "عائل", "اتصال", "اتصل").any { t.contains(it) } -> Category.FAMILY
            else -> Category.PERSONAL
        }
    }

    fun clockNow(): Instant = clock.instant()
}
