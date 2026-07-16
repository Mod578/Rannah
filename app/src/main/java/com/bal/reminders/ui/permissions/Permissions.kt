package com.bal.reminders.ui.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.bal.reminders.scheduling.NotificationPresenter

data class PermissionsStatus(
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    /** Full-screen alarm UI availability (API 34+ can revoke it). */
    val fullScreenAlarmGranted: Boolean,
    /** The device's alarm stream is muted: a «منبّه مهم» would ring silently. */
    val alarmVolumeMuted: Boolean,
    /** The user turned the alarm channel off: notifications on, alarms silent. */
    val alarmChannelBlocked: Boolean = false,
    /** The follow-up channel is off, so «بانتظار تأكيدك» cannot reach them. */
    val followUpChannelBlocked: Boolean = false,
) {
    val essentialsGranted: Boolean get() = notificationsGranted && exactAlarmsGranted
}

object Permissions {

    fun status(context: Context): PermissionsStatus {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return PermissionsStatus(
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarmsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms(),
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            fullScreenAlarmGranted = canUseFullScreenIntent(context),
            alarmVolumeMuted = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0,
            alarmChannelBlocked = channelBlocked(context, NotificationPresenter.CHANNEL_ALARM),
            followUpChannelBlocked = channelBlocked(context, NotificationPresenter.CHANNEL_FOLLOW_UP),
        )
    }

    /**
     * A channel the user switched off. Worth reporting on its own: app-level
     * notifications can be fully allowed while the one channel that carries
     * real alarms is silent, which looks like رَنّة simply failing.
     */
    private fun channelBlocked(context: Context, channelId: String): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun channelSettingsIntent(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            appDetailsIntent(context)
        }

    fun fullScreenIntentSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            appDetailsIntent(context)
        }

    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    private fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
}
