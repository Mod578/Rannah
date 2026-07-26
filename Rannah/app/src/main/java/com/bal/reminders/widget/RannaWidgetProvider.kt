package com.bal.reminders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.bal.reminders.MainActivity
import com.bal.reminders.R
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ودجت الشاشة الرئيسية: تاريخ اليوم بالميلادي مع لمحة هجرية خفيفة، والرنّة
 * القادمة. تتحدث كل ٣٠ دقيقة ومع كل تغيير في التذكيرات.
 */
@AndroidEntryPoint
class RannaWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var repository: ReminderRepository

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = Instant.now()
                val next = repository.getActive()
                    .filter { it.nextTriggerAt?.isAfter(now) == true }
                    .minByOrNull { it.nextTriggerAt!! }
                val views = buildViews(context, next)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } finally {
                result.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        next: Reminder?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ranna)

        val (primaryDate, secondaryDate) = BalFormats.dateLines(LocalDate.now())
        views.setTextViewText(R.id.widget_date_primary, primaryDate)
        if (secondaryDate != null) {
            views.setViewVisibility(R.id.widget_date_secondary, View.VISIBLE)
            views.setTextViewText(R.id.widget_date_secondary, secondaryDate)
        } else {
            views.setViewVisibility(R.id.widget_date_secondary, View.GONE)
        }

        val nextAt = next?.nextTriggerAt
        if (next != null && nextAt != null) {
            views.setViewVisibility(R.id.widget_next_label, View.VISIBLE)
            views.setViewVisibility(R.id.widget_next_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_next_title, next.title)
            views.setTextViewText(R.id.widget_next_time, BalFormats.dateTime(context, nextAt))
        } else {
            views.setViewVisibility(R.id.widget_next_label, View.GONE)
            views.setViewVisibility(R.id.widget_next_time, View.GONE)
            views.setTextViewText(
                R.id.widget_next_title,
                context.getString(R.string.home_empty_title),
            )
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (next != null) putExtra(MainActivity.EXTRA_REMINDER_ID, next.id)
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    companion object {
        /** Redraws every placed widget instance; no-op when none exist. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RannaWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, RannaWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
