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
        when (intent.action) {
            ACTION_FIRE -> {
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L)
                    .takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: return
                runAsync {
                    scheduler.onAlarmFired(id, occurrence)
                    // A fired one-time reminder changes "next" without touching alarms.
                    RannaWidgetProvider.refresh(context)
                }
            }

            ACTION_RE_ALERT -> {
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L)
                    .takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: return
                runAsync { scheduler.onReAlertFired(id, occurrence) }
            }

            ACTION_FOLLOW_UP -> {
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L)
                    .takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: return
                runAsync { scheduler.onFollowUpDue(id, occurrence) }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.bal.reminders.action.FIRE"
        const val ACTION_RE_ALERT = "com.bal.reminders.action.RE_ALERT"
        const val ACTION_FOLLOW_UP = "com.bal.reminders.action.FOLLOW_UP"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrence_millis"
    }
}

/**
 * Handles every notification action. All downstream handling is idempotent,
 * so a duplicate tap or a replayed PendingIntent cannot double-complete,
 * double-skip or corrupt the schedule.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id <= 0) return
        val occurrence = intent.occurrenceExtra()

        // The tapped notification always disappears, no matter which surface.
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        when (intent.action) {
            ACTION_COMPLETE -> runAsync {
                scheduler.complete(id, occurrence, fromNotification = true)
            }

            ACTION_SNOOZE -> runAsync { scheduler.snooze(id, occurrenceAt = occurrence) }

            // «ذكّرني بعد ٥ دقائق» from the follow-up moves the ask, not the
            // schedule: the task is still due at its own time.
            ACTION_SNOOZE_FOLLOW_UP -> {
                val at = occurrence ?: return
                runAsync { scheduler.snoozeFollowUp(id, at) }
            }

            ACTION_SKIP -> runAsync { scheduler.skipOccurrence(id, occurrence) }

            ACTION_STOP_ALARM -> {
                val at = occurrence ?: return
                runAsync { scheduler.stopAlarm(id, at, askFollowUp = true) }
            }

            ACTION_UNDO_COMPLETE -> {
                val at = occurrence ?: return
                runAsync { scheduler.undoComplete(id, at) }
            }

            ACTION_DISMISSED -> {
                val at = occurrence ?: return
                runAsync { scheduler.onNotificationDismissed(id, at) }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.bal.reminders.action.COMPLETE"
        const val ACTION_SNOOZE = "com.bal.reminders.action.SNOOZE"
        const val ACTION_SNOOZE_FOLLOW_UP = "com.bal.reminders.action.SNOOZE_FOLLOW_UP"
        const val ACTION_SKIP = "com.bal.reminders.action.SKIP"
        const val ACTION_STOP_ALARM = "com.bal.reminders.action.STOP_ALARM"
        const val ACTION_UNDO_COMPLETE = "com.bal.reminders.action.UNDO_COMPLETE"
        const val ACTION_DISMISSED = "com.bal.reminders.action.DISMISSED"
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
