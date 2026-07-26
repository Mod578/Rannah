package com.bal.reminders.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bal.reminders.R
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Builds and posts the alarm surface. رَنّة has a single kind of alert: a
 * ringing alarm with a full-screen تأجيل/تم screen, backed by a foreground
 * ringer service.
 */
@Singleton
class NotificationPresenter @Inject constructor(
    @ApplicationContext appContext: Context,
) : ReminderNotifications {

    // The app speaks Arabic regardless of the system language.
    private val context: Context = appContext.let {
        val config = Configuration(it.resources.configuration)
        config.setLocale(Locale("ar"))
        it.createConfigurationContext(config)
    }

    private val manager get() = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // The alarm channel is silent on purpose: the ringer service owns the
        // sound and vibration so they loop until تأجيل or تم.
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_description)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    /** True when the system will honor a full-screen alarm UI. */
    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    override fun startAlarm(reminder: Reminder, occurrenceAt: Instant) {
        ensureChannels()
        val intent = Intent(context, AlarmRingerService::class.java)
            .setAction(AlarmRingerService.ACTION_START)
            .putExtra(AlarmRingerService.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(AlarmRingerService.EXTRA_OCCURRENCE_MILLIS, occurrenceAt.toEpochMilli())
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // The service could not start (odd OEM restriction): still post the
            // full-screen alarm surface so the reminder is not lost.
            notifySafely(alarmNotificationId(reminder.id), buildAlarmNotification(reminder, occurrenceAt))
        }
    }

    /**
     * The alarm notification the ringer service runs in the foreground with:
     * full-screen تأجيل/تم UI when permitted, heads-up otherwise. The only inline
     * action is تأجيل; تم is always a deliberate act on the alarm screen itself.
     */
    fun buildAlarmNotification(reminder: Reminder, occurrenceAt: Instant): android.app.Notification {
        val notifId = alarmNotificationId(reminder.id)
        val fullScreen = alarmScreenIntent(reminder.id, occurrenceAt)
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(
                context.getString(R.string.notification_alarm_ringing, occurrenceTime(occurrenceAt)),
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(occurrenceAt.toEpochMilli())
            .setShowWhen(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(
                0,
                snoozeLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE, reminder, occurrenceAt, notifId, 2),
            )
            .build()
    }

    override fun dismiss(reminderId: Long) {
        manager.cancel(alarmNotificationId(reminderId))
        AlarmRingerService.stop(context, reminderId)
    }

    // -------------------------------------------------------------- helpers

    private fun occurrenceTime(occurrenceAt: Instant): String =
        BalFormats.time(context, occurrenceAt.atZone(ZoneId.systemDefault()).toLocalTime())

    private fun snoozeLabel(reminder: Reminder): String =
        context.resources.getQuantityString(
            R.plurals.notification_snooze_minutes,
            reminder.snoozeMinutes,
            reminder.snoozeMinutes,
        )

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked mid-flight; nothing to do.
        }
    }

    private fun alarmScreenIntent(reminderId: Long, occurrenceAt: Instant): PendingIntent {
        val intent = Intent(context, com.bal.reminders.alarm.AlarmActivity::class.java)
            .putExtra(com.bal.reminders.alarm.AlarmActivity.EXTRA_REMINDER_ID, reminderId)
            .putExtra(
                com.bal.reminders.alarm.AlarmActivity.EXTRA_OCCURRENCE_MILLIS,
                occurrenceAt.toEpochMilli(),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            alarmNotificationId(reminderId) * 10 + 7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(
        action: String,
        reminder: Reminder,
        occurrenceAt: Instant,
        notifId: Int,
        actionIndex: Int,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceAt.toEpochMilli())
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        return PendingIntent.getBroadcast(
            context,
            notifId * 10 + actionIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ALARM = "reminders_alarm"

        /** Separate id space so the alarm surface never collides with anything else. */
        fun alarmNotificationId(reminderId: Long): Int = 3_000_000 + reminderId.toInt()
    }
}
