package com.bal.reminders

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bal.reminders.scheduling.NotificationPresenter
import com.bal.reminders.scheduling.ReconcileWorker
import com.bal.reminders.scheduling.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class BalApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationPresenter: NotificationPresenter
    @Inject lateinit var scheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationPresenter.ensureChannels()
        ReconcileWorker.ensureScheduled(this)
        // Restore alarms whenever the process starts — covers the case where a
        // force-stop wiped them and the user just reopened the app.
        appScope.launch { scheduler.rescheduleAll(fireMissed = true) }
    }
}
