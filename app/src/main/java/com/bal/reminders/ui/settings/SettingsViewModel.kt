package com.bal.reminders.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.data.DateDisplay
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.ui.permissions.Permissions
import com.bal.reminders.widget.RannaWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSnoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val permissionsOk: Boolean = true,
    val dateDisplay: DateDisplay = DateDisplay.BOTH,
    val hijriAdjustmentDays: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val permissionsOk =
        MutableStateFlow(Permissions.status(appContext).essentialsGranted)

    val state = combine(settingsRepository.settings, permissionsOk) { settings, permissions ->
        SettingsState(
            themeMode = settings.themeMode,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            permissionsOk = permissions,
            dateDisplay = settings.dateDisplay,
            hijriAdjustmentDays = settings.hijriAdjustmentDays,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultSnoozeMinutes(minutes) }
    }

    fun setDateDisplay(display: DateDisplay) {
        viewModelScope.launch {
            settingsRepository.setDateDisplay(display)
            RannaWidgetProvider.refresh(appContext)
        }
    }

    fun setHijriAdjustment(days: Int) {
        viewModelScope.launch {
            settingsRepository.setHijriAdjustmentDays(days)
            RannaWidgetProvider.refresh(appContext)
        }
    }

    fun refreshPermissions() {
        permissionsOk.value = Permissions.status(appContext).essentialsGranted
    }
}
