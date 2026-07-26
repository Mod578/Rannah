package com.bal.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bal.reminders.widget.RannaWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs suspending work from a receiver within the broadcast's lifetime. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            result.finish()
        }
    }
}

private fun Intent.occurrenceExtra(): Instant? =
    getLongExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_MILLIS, 0L)
        .takeIf { it > 0 }
        ?.let(Instant::ofEpochMilli)

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id <= 0) return
        if (intent.action == ACTION_FIRE) {
            val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L)
                .takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: return
            runAsync {
                scheduler.onAlarmFired(id, occurrence)
                // A fired one-time reminder changes "next" without touching alarms.
                RannaWidgetProvider.refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.bal.reminders.action.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrence_millis"
    }
}

/**
 * Handles the alarm notification's «تأجيل» action. Downstream handling is
 * idempotent, so a duplicate tap or a replayed PendingIntent cannot corrupt the
 * schedule.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id <= 0) return
        val occurrence = intent.occurrenceExtra()

        // The tapped notification always disappears.
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (intent.action == ACTION_SNOOZE) {
            runAsync { scheduler.snooze(id, occurrenceAt = occurrence) }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.bal.reminders.action.SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrence_millis"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

/**
 * Rebuilds alarms whenever the system invalidates them: after boot, after the
 * app is updated, and when the clock or timezone changes (reminders keep
 * wall-clock semantics, so their instants must be recomputed).
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> runAsync {
                scheduler.rescheduleAll(fireMissed = true)
                RannaWidgetProvider.refresh(context)
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> runAsync {
                scheduler.rescheduleAll(fireMissed = false)
                // The widget shows today's date; redraw even with no reminders.
                RannaWidgetProvider.refresh(context)
            }
        }
    }
}
