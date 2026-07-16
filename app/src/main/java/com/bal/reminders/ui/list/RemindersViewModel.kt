package com.bal.reminders.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.scheduling.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemindersState(
    val items: List<Reminder> = emptyList(),
    val query: String = "",
    val filter: Category? = null,
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<Category?>(null)

    val state = combine(repository.observeAll(), query, filter) { reminders, q, f ->
        RemindersState(
            items = reminders
                // Ended recurring series stay listed (labeled), finished
                // one-time reminders live in the log instead.
                .filter { !it.isDone || it.schedule.isRecurring }
                .filter { f == null || it.category == f }
                .filter { q.isBlank() || it.title.contains(q.trim()) || it.notes?.contains(q.trim()) == true }
                .sortedWith(
                    compareByDescending<Reminder> { it.enabled }
                        .thenBy { it.nextTriggerAt ?: java.time.Instant.MAX },
                ),
            query = q,
            filter = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersState())

    fun setQuery(v: String) {
        query.value = v
    }

    fun setFilter(v: Category?) {
        filter.value = v
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { scheduler.setEnabled(id, enabled) }
    }
}
