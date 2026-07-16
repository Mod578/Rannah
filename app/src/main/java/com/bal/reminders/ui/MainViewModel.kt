package com.bal.reminders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bal.reminders.data.SettingsRepository
import com.bal.reminders.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MainState(
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state = settingsRepository.settings
        .map { MainState(loaded = true, themeMode = it.themeMode, onboardingDone = it.onboardingDone) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainState())
}
