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
import com.bal.reminders.MainActivity
import com.bal.reminders.R
import com.bal.reminders.domain.model.Priority
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Builds and posts every user-facing alert surface. Action labels here are the
 * same canonical words used inside the app: تم، تأجيل، تخطي هذه المرة، إيقاف.
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
        val channels = listOf(
            channel(CHANNEL_FULL, R.string.channel_full_name, sound = true, vibration = true),
            channel(CHANNEL_SOUND, R.string.channel_sound_name, sound = true, vibration = false),
            channel(CHANNEL_VIBRATE, R.string.channel_vibrate_name, sound = false, vibration = true),
            channel(CHANNEL_SILENT, R.string.channel_silent_name, sound = false, vibration = false),
            // The alarm channel is silent on purpose: the ringer service owns
            // the sound and vibration so they loop until إيقاف or تأجيل.
            NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarm_description)
                setSound(null, null)
                enableVibration(false)
            },
            // Its own channel so «المتابعة حتى الإنجاز» can be turned down or
            // off from the system settings without silencing real reminders.
            NotificationChannel(
                CHANNEL_FOLLOW_UP,
                context.getString(R.string.channel_followup_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_followup_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
        channels.forEach(nm::createNotificationChannel)
    }

    private fun channel(id: String, nameRes: Int, sound: Boolean, vibration: Boolean): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(vibration)
            if (!sound) setSound(null, null)
        }

    /** True when the system will honor a full-screen alarm UI. */
    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    // ------------------------------------------------------------- standard

    override fun show(reminder: Reminder, occurrenceAt: Instant) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()

        val notifId = notificationId(reminder.id)
        val late = Duration.between(occurrenceAt, Instant.now()) > Duration.ofMinutes(2)
        val body = context.getString(
            if (late) R.string.notification_was_due_at else R.string.notification_due_at,
            occurrenceTime(occurrenceAt),
        )

        val builder = NotificationCompat.Builder(context, standardChannelFor(reminder))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (reminder.priority == Priority.HIGH) {
                    NotificationCompat.PRIORITY_MAX
                } else {
                    NotificationCompat.PRIORITY_HIGH
                },
            )
            .setAutoCancel(true)
            .setWhen(occurrenceAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openIntent(reminder.id, notifId))
            // تم first: completing the obligation is the primary action.
            .addAction(
                0,
                completionLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_COMPLETE, reminder, occurrenceAt, notifId, 1),
            )
            .addAction(
                0,
                snoozeLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE, reminder, occurrenceAt, notifId, 2),
            )
            .setDeleteIntent(
                actionIntent(NotificationActionReceiver.ACTION_DISMISSED, reminder, occurrenceAt, notifId, 6),
            )
        if (reminder.schedule.isRecurring) {
            builder.addAction(
                0,
                context.getString(R.string.notification_skip),
                actionIntent(NotificationActionReceiver.ACTION_SKIP, reminder, occurrenceAt, notifId, 3),
            )
        }

        notifySafely(notifId, builder.build())
    }

    // ---------------------------------------------------------------- alarm

    override fun startAlarm(reminder: Reminder, occurrenceAt: Instant, attempt: Int) {
        ensureChannels()
        val intent = Intent(context, AlarmRingerService::class.java)
            .setAction(AlarmRingerService.ACTION_START)
            .putExtra(AlarmRingerService.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(AlarmRingerService.EXTRA_OCCURRENCE_MILLIS, occurrenceAt.toEpochMilli())
            .putExtra(AlarmRingerService.EXTRA_ATTEMPT, attempt)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // The service could not start (odd OEM restriction): degrade to a
            // loud standard notification rather than staying silent.
            show(reminder, occurrenceAt)
        }
    }

    /**
     * The alarm notification the ringer service runs in the foreground with:
     * full-screen إيقاف/تأجيل UI when permitted, heads-up otherwise.
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
                context.getString(R.string.notification_stop),
                actionIntent(NotificationActionReceiver.ACTION_STOP_ALARM, reminder, occurrenceAt, notifId, 4),
            )
            .addAction(
                0,
                snoozeLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE, reminder, occurrenceAt, notifId, 2),
            )
            .build()
    }

    override fun showMissed(reminder: Reminder, occurrenceAt: Instant) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notifId = notificationId(reminder.id)
        val builder = NotificationCompat.Builder(context, standardChannelFor(reminder))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(
                context.getString(R.string.notification_alarm_missed, occurrenceTime(occurrenceAt)),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(openIntent(reminder.id, notifId))
            .addAction(
                0,
                completionLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_COMPLETE, reminder, occurrenceAt, notifId, 1),
            )
            .addAction(
                0,
                snoozeLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE, reminder, occurrenceAt, notifId, 2),
            )
        notifySafely(notifId, builder.build())
    }

    /**
     * The «المتابعة حتى الإنجاز» ask.
     *
     * Ongoing, so a glance does not wipe out the only trace of an unfinished
     * obligation, and silent, because the alert has already been heard: this
     * asks, it does not shout. It is still removable in every way Android
     * offers (the completion action, the app, the channel, the system's own
     * controls) and it stops itself once the reminder's repeat budget is spent,
     * which is why it needs no foreground service to stay alive.
     */
    override fun showFollowUp(reminder: Reminder, occurrenceAt: Instant, nudge: Int, remaining: Int) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notifId = followUpNotificationId(reminder.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_FOLLOW_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(followUpQuestion(reminder))
            .setContentText(
                context.getString(R.string.notification_followup_since, occurrenceTime(occurrenceAt)),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notification_followup_since, occurrenceTime(occurrenceAt)) +
                        "\n" + followUpLimitLine(remaining),
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(occurrenceAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openIntent(reminder.id, notifId))
            // The real answer first; «ذكّرني» stays available but secondary.
            .addAction(
                0,
                completionLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_COMPLETE, reminder, occurrenceAt, notifId, 1),
            )
            .addAction(
                0,
                context.resources.getQuantityString(
                    R.plurals.notification_followup_snooze,
                    reminder.followUpIntervalMinutes,
                    reminder.followUpIntervalMinutes,
                ),
                actionIntent(
                    NotificationActionReceiver.ACTION_SNOOZE_FOLLOW_UP,
                    reminder,
                    occurrenceAt,
                    notifId,
                    8,
                ),
            )
        // Two actions, deliberately. A third («تخطي هذه المرة») squeezed all
        // three into a third of the width each and Android truncated every one
        // of them: «أخذت الد...» is not an answer anyone can give. Skipping is
        // a rarer, more considered choice, so it lives in the app where it has
        // room for its full name.
        notifySafely(notifId, builder.build())
    }

    override fun dismissFollowUp(reminderId: Long) {
        manager.cancel(followUpNotificationId(reminderId))
    }

    override fun showStopFollowUp(reminder: Reminder, occurrenceAt: Instant) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notifId = followUpNotificationId(reminder.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_followup_title))
            .setContentText(context.getString(R.string.notification_followup_body, reminder.title))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(FOLLOW_UP_TIMEOUT_MS)
            .setContentIntent(openIntent(reminder.id, notifId))
            .addAction(
                0,
                completionLabel(reminder),
                actionIntent(NotificationActionReceiver.ACTION_COMPLETE, reminder, occurrenceAt, notifId, 1),
            )
        if (reminder.schedule.isRecurring) {
            builder.addAction(
                0,
                context.getString(R.string.notification_skip),
                actionIntent(NotificationActionReceiver.ACTION_SKIP, reminder, occurrenceAt, notifId, 3),
            )
        }
        notifySafely(notifId, builder.build())
    }

    override fun showCompletedUndo(reminder: Reminder, occurrenceAt: Instant) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notifId = undoNotificationId(reminder.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_completed_title, reminder.title))
            .setContentText(context.getString(R.string.notification_completed_body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(UNDO_TIMEOUT_MS)
            .addAction(
                0,
                context.getString(R.string.notification_undo),
                actionIntent(NotificationActionReceiver.ACTION_UNDO_COMPLETE, reminder, occurrenceAt, notifId, 5),
            )
            .build()
        notifySafely(notifId, notification)
    }

    override fun dismiss(reminderId: Long) {
        manager.cancel(notificationId(reminderId))
        manager.cancel(alarmNotificationId(reminderId))
        AlarmRingerService.stop(context, reminderId)
    }

    // -------------------------------------------------------------- helpers

    private fun occurrenceTime(occurrenceAt: Instant): String =
        BalFormats.time(context, occurrenceAt.atZone(ZoneId.systemDefault()).toLocalTime())

    /**
     * «سجلت البصمة» when the reminder carries its own phrase, «تم الإنجاز»
     * otherwise. The phrase only ever comes from a template or from the user's
     * own typing, so there is nothing here that could invent a claim the user
     * never made.
     */
    private fun completionLabel(reminder: Reminder): String =
        reminder.completionLabel?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_complete)

    /**
     * «هل سجلت البصمة؟» Completion phrases are first-person past-tense
     * statements, so prefixing «هل» and adding «؟» yields a real question for
     * every trusted template. Without a phrase, رَنّة quotes the title instead
     * of guessing at a verb for it.
     */
    private fun followUpQuestion(reminder: Reminder): String {
        val label = reminder.completionLabel?.takeIf { it.isNotBlank() }
        return if (label != null) {
            context.getString(R.string.notification_followup_question, label)
        } else {
            context.getString(R.string.notification_followup_question_generic, reminder.title)
        }
    }

    private fun followUpLimitLine(remaining: Int): String =
        if (remaining > 0) {
            context.resources.getQuantityString(
                R.plurals.notification_followup_remaining,
                remaining,
                remaining,
            )
        } else {
            context.getString(R.string.notification_followup_last)
        }

    private fun snoozeLabel(reminder: Reminder): String =
        context.resources.getQuantityString(
            R.plurals.notification_snooze_minutes,
            reminder.snoozeMinutes,
            reminder.snoozeMinutes,
        )

    private fun standardChannelFor(reminder: Reminder): String = when {
        reminder.soundEnabled && reminder.vibrationEnabled -> CHANNEL_FULL
        reminder.soundEnabled -> CHANNEL_SOUND
        reminder.vibrationEnabled -> CHANNEL_VIBRATE
        else -> CHANNEL_SILENT
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked mid-flight; nothing to do.
        }
    }

    private fun openIntent(reminderId: Long, requestSpace: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestSpace * 10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            // Distinct request codes per (surface, action, reminder).
            notifId * 10 + actionIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(reminderId: Long): Int = reminderId.toInt()

    companion object {
        const val CHANNEL_FULL = "reminders_full"
        const val CHANNEL_SOUND = "reminders_sound"
        const val CHANNEL_VIBRATE = "reminders_vibrate"
        const val CHANNEL_SILENT = "reminders_silent"
        const val CHANNEL_ALARM = "reminders_alarm"
        const val CHANNEL_FOLLOW_UP = "reminders_followup"

        private const val UNDO_TIMEOUT_MS = 15_000L
        private const val FOLLOW_UP_TIMEOUT_MS = 10L * 60L * 1000L

        /** Separate id spaces so surfaces never overwrite each other. */
        fun alarmNotificationId(reminderId: Long): Int = 3_000_000 + reminderId.toInt()
        fun undoNotificationId(reminderId: Long): Int = 1_000_000 + reminderId.toInt()
        fun followUpNotificationId(reminderId: Long): Int = 2_000_000 + reminderId.toInt()
    }
}
