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
 * Thin seam over AlarmManager so scheduling logic stays unit-testable. One alarm
 * per reminder: the reminder id is the PendingIntent request code, which makes
 * scheduling idempotent and edits/cancellations atomic. Every reminder is a real
 * alarm, so it schedules through `setAlarmClock`, the system surfaces it as the
 * device's next alarm and exempts it from Doze deferral.
 */
interface AlarmGateway {
    fun schedule(reminderId: Long, at: Instant)

    fun cancel(reminderId: Long)

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

    override fun schedule(reminderId: Long, at: Instant) {
        val pi = alarmIntent(reminderId, at.toEpochMilli())
        val triggerAt = at.toEpochMilli()
        try {
            if (canScheduleExact()) {
                try {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAt, showIntent(reminderId)),
                        pi,
                    )
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

    override fun cancel(reminderId: Long) {
        alarmManager.cancel(alarmIntent(reminderId, 0L))
        RannaWidgetProvider.refresh(context)
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
