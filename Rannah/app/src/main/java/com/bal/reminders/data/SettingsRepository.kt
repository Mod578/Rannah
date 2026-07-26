package com.bal.reminders.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bal.reminders.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSnoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val onboardingDone: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            defaultSnoozeMinutes = prefs[KEY_SNOOZE] ?: Reminder.DEFAULT_SNOOZE_MINUTES,
            onboardingDone = prefs[KEY_ONBOARDING] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_SNOOZE] = minutes.coerceIn(1, 120) }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING] = true }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
    }
}
