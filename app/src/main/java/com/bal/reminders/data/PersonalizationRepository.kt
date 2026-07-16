package com.bal.reminders.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.CalendarSystem
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.personalizationStore by preferencesDataStore(name = "personalization")

/**
 * Learned, per-category shortcuts the editor can *suggest*. Everything stays
 * on-device, is derived only from the user's own saves, and is surfaced as a
 * transparent optional suggestion, never as a silent behavior change.
 */
data class PersonalSuggestions(
    /** Suggested when the user chose it for ≥[MIN_SAVES] saves and ≥70% of them. */
    val alertMode: AlertMode? = null,
    val calendar: CalendarSystem? = null,
    val snoozeMinutes: Int? = null,
    val lastRingtoneUri: String? = null,
) {
    companion object {
        const val MIN_SAVES = 3
    }
}

@Singleton
class PersonalizationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun suggestionsFor(category: Category): Flow<PersonalSuggestions> =
        context.personalizationStore.data.map { prefs ->
            val alarm = prefs[intKey("alert_alarm", category)] ?: 0
            val standard = prefs[intKey("alert_standard", category)] ?: 0
            val hijri = prefs[intKey("cal_hijri", category)] ?: 0
            val gregorian = prefs[intKey("cal_gregorian", category)] ?: 0
            PersonalSuggestions(
                alertMode = dominant(alarm to AlertMode.ALARM, standard to AlertMode.STANDARD),
                calendar = dominant(hijri to CalendarSystem.HIJRI, gregorian to CalendarSystem.GREGORIAN),
                snoozeMinutes = prefs[intKey("snooze_last", category)],
                lastRingtoneUri = prefs[KEY_LAST_RINGTONE],
            )
        }

    private fun <T> dominant(a: Pair<Int, T>, b: Pair<Int, T>): T? {
        val total = a.first + b.first
        if (total < PersonalSuggestions.MIN_SAVES) return null
        return when {
            a.first * 10 >= total * 7 -> a.second
            b.first * 10 >= total * 7 -> b.second
            else -> null
        }
    }

    /** Called after every successful save so future suggestions reflect real usage. */
    suspend fun recordSave(reminder: Reminder) {
        context.personalizationStore.edit { prefs ->
            val alertKey = intKey(
                if (reminder.alertMode == AlertMode.ALARM) "alert_alarm" else "alert_standard",
                reminder.category,
            )
            prefs[alertKey] = (prefs[alertKey] ?: 0) + 1
            val calKey = intKey(
                if (reminder.schedule.calendar == CalendarSystem.HIJRI) "cal_hijri" else "cal_gregorian",
                reminder.category,
            )
            prefs[calKey] = (prefs[calKey] ?: 0) + 1
            prefs[intKey("snooze_last", reminder.category)] = reminder.snoozeMinutes
            reminder.ringtoneUri?.let { prefs[KEY_LAST_RINGTONE] = it }
        }
    }

    private fun intKey(prefix: String, category: Category) =
        intPreferencesKey("${prefix}_${category.id}")

    private companion object {
        val KEY_LAST_RINGTONE = stringPreferencesKey("last_ringtone")
    }
}
