package com.bal.reminders.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bal.reminders.MainActivity
import com.bal.reminders.widget.RannaWidgetProvider
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Thin seam over AlarmManager so scheduling logic stays unit-testable.
 * One alarm per reminder: the reminder id is the PendingIntent request code,
 * which makes scheduling idempotent and edits/cancellations atomic. A second,
 * negative request-code space carries the optional single re-alert of an
 * ignored alarm without disturbing the next real occurrence.
 */
interface AlarmGateway {
    /**
     * [alarmClock] marks a «منبّه مهم» occurrence: it schedules through
     * `setAlarmClock`, which the system surfaces as the device's next alarm
     * and exempts from Doze deferral.
     */
    fun schedule(reminderId: Long, at: Instant, alarmClock: Boolean = false)

    /** Schedules the single re-alert of an ignored alarm-mode occurrence. */
    fun scheduleReAlert(reminderId: Long, occurrenceAt: Instant, at: Instant)

    /** Cancels the reminder's alarm and any pending re-alert. */
    fun cancel(reminderId: Long)

    fun cancelReAlert(reminderId: Long)

    fun canScheduleExact(): Boolean
}

@Singleton
class AndroidAlarmGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmGateway {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(reminderId: Long, at: Instant, alarmClock: Boolean) {
        val pi = alarmIntent(reminderId, at.toEpochMilli())
        val triggerAt = at.toEpochMilli()
        try {
            if (canScheduleExact()) {
                try {
                    if (alarmClock) {
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerAt, showIntent(reminderId)),
                            pi,
                        )
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                    return
                } catch (_: SecurityException) {
                    // Permission was revoked between the check and the call; degrade.
                }
            }
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MS, pi)
        } finally {
            // Every reminder mutation ends here; keep the widget in sync.
            RannaWidgetProvider.refresh(context)
        }
    }

    override fun scheduleReAlert(reminderId: Long, occurrenceAt: Instant, at: Instant) {
        val pi = reAlertIntent(reminderId, occurrenceAt.toEpochMilli())
        val triggerAt = at.toEpochMilli()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MS, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MS, pi)
        }
    }

    override fun cancel(reminderId: Long) {
        alarmManager.cancel(alarmIntent(reminderId, 0L))
        alarmManager.cancel(reAlertIntent(reminderId, 0L))
        RannaWidgetProvider.refresh(context)
    }

    override fun cancelReAlert(reminderId: Long) {
        alarmManager.cancel(reAlertIntent(reminderId, 0L))
    }

    private fun alarmIntent(reminderId: Long, occurrenceMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            .putExtra(AlarmReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis)
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reAlertIntent(reminderId: Long, occurrenceMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RE_ALERT)
            .putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            .putExtra(AlarmReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis)
        // Negative request-code space: never collides with the main alarm.
        return PendingIntent.getBroadcast(
            context,
            -reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Tapping the system's alarm-clock indicator opens the reminder. */
    private fun showIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val INEXACT_WINDOW_MS = 10L * 60L * 1000L
    }
}
