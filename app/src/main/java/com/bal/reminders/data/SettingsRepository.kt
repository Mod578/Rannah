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

/** Which calendar(s) the date lines show, on the home screen and the widget. */
enum class DateDisplay { BOTH, HIJRI, GREGORIAN }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSnoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val onboardingDone: Boolean = false,
    val dateDisplay: DateDisplay = DateDisplay.BOTH,
    /** Umm al-Qura vs. local moon sighting can differ; user-set, -2..+2 days. */
    val hijriAdjustmentDays: Int = 0,
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
            dateDisplay = prefs[KEY_DATE_DISPLAY]
                ?.let { runCatching { DateDisplay.valueOf(it) }.getOrNull() }
                ?: DateDisplay.BOTH,
            hijriAdjustmentDays = (prefs[KEY_HIJRI_ADJUST] ?: 0).coerceIn(-2, 2),
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

    suspend fun setDateDisplay(display: DateDisplay) {
        context.dataStore.edit { it[KEY_DATE_DISPLAY] = display.name }
    }

    suspend fun setHijriAdjustmentDays(days: Int) {
        context.dataStore.edit { it[KEY_HIJRI_ADJUST] = days.coerceIn(-2, 2) }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val KEY_DATE_DISPLAY = stringPreferencesKey("date_display")
        val KEY_HIJRI_ADJUST = intPreferencesKey("hijri_adjustment_days")
    }
}
