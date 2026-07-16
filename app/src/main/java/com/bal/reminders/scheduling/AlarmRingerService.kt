package com.bal.reminders.scheduling

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.domain.model.Reminder
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The «منبّه مهم» ringer: a foreground service (systemExempted type, started
 * from the exact-alarm broadcast) that loops the user's alarm ringtone on the
 * alarm stream, vibrates, optionally ramps the volume, and gives up after the
 * reminder's timeout by handing the occurrence back to [ReminderScheduler] as
 * missed. It never decides semantics itself: إيقاف/تأجيل/تم all route through
 * the scheduler.
 *
 * The alarm stream is why silent mode does not mute it, and why Do Not Disturb
 * only mutes it when the user excluded alarms in their DND settings.
 */
@AndroidEntryPoint
class AlarmRingerService : Service() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var presenter: NotificationPresenter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private var reminderId: Long = 0L
    private var occurrenceAt: Instant = Instant.EPOCH
    private var attempt: Int = 1
    private var startGeneration: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L)
                if (id <= 0 || occurrence <= 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val at = Instant.ofEpochMilli(occurrence)
                // Redelivered/duplicate service starts for one occurrence must
                // not restart the player, vibration, timeout or alarm screen.
                if (currentReminderId != id || reminderId != id || occurrenceAt != at) {
                    start(id, at, intent.getIntExtra(EXTRA_ATTEMPT, 1))
                }
            }

            ACTION_STOP -> {
                val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                if (id == reminderId || id <= 0) {
                    finish(removeNotification = true)
                }
            }

            else -> stopSelf()
        }
        // Redelivery restores the ring after process death mid-alarm.
        return START_REDELIVER_INTENT
    }

    private fun start(id: Long, occurrence: Instant, attemptNumber: Int) {
        // A newer alarm replaces whatever was ringing.
        val generation = ++startGeneration
        stopEffects()
        reminderId = id
        occurrenceAt = occurrence
        attempt = attemptNumber
        currentReminderId = id

        scope.launch {
            val reminder = repository.getById(id)
            // A stop or a newer start may have happened while Room was read.
            if (generation != startGeneration) return@launch
            if (reminder == null || !reminder.enabled || reminder.isDone) {
                finish(removeNotification = true)
                return@launch
            }
            val notification = presenter.buildAlarmNotification(reminder, occurrence)
            ServiceCompat.startForeground(
                this@AlarmRingerService,
                NotificationPresenter.alarmNotificationId(id),
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                } else {
                    0
                },
            )
            acquireWakeLock(reminder)
            ring(reminder)
            vibrate(reminder)
            handler.postDelayed(
                { onTimeout() },
                reminder.alarmTimeoutMinutes.coerceIn(1, 30) * 60_000L,
            )
        }
    }

    private fun ring(reminder: Reminder) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audioManager.requestAudioFocus(it) }

        val candidates = buildList {
            reminder.ringtoneUri?.let { runCatching { add(Uri.parse(it)) } }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
        }
        for (uri in candidates) {
            val prepared = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmRingerService, uri)
                    isLooping = true
                    prepare()
                }
            }.getOrNull()
            if (prepared != null) {
                player = prepared
                if (reminder.alarmGradualVolume) {
                    rampVolume(prepared, step = 0)
                } else {
                    prepared.setVolume(1f, 1f)
                }
                prepared.start()
                return
            }
        }
        // No playable sound at all: vibration and the notification still run.
    }

    /** 0.15 → 1.0 over ~30 seconds, so a night alarm does not start at full blast. */
    private fun rampVolume(target: MediaPlayer, step: Int) {
        val fraction = (0.15f + step * 0.085f).coerceAtMost(1f)
        runCatching { target.setVolume(fraction, fraction) }
        if (fraction < 1f && target === player) {
            handler.postDelayed({ rampVolume(target, step + 1) }, 3_000L)
        }
    }

    private fun vibrate(reminder: Reminder) {
        if (!reminder.vibrationEnabled) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 700, 500, 700, 1_200)
        @Suppress("DEPRECATION") // AudioAttributes overload deprecated only at API 35.
        runCatching {
            v.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun acquireWakeLock(reminder: Reminder) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ranna:alarm").apply {
            acquire((reminder.alarmTimeoutMinutes.coerceIn(1, 30) + 1) * 60_000L)
        }
    }

    private fun onTimeout() {
        val id = reminderId
        val occurrence = occurrenceAt
        val attemptNumber = attempt
        scope.launch {
            scheduler.onAlarmTimeout(id, occurrence, attemptNumber)
            finish(removeNotification = true)
        }
    }

    private fun stopEffects() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
        focusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching { audioManager.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun finish(removeNotification: Boolean) {
        startGeneration += 1
        stopEffects()
        if (currentReminderId == reminderId) currentReminderId = null
        // Tell any open alarm screen the ring is over.
        sendBroadcast(
            Intent(ACTION_ALARM_STOPPED)
                .setPackage(packageName)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
        )
        ServiceCompat.stopForeground(
            this,
            if (removeNotification) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH,
        )
        stopSelf()
    }

    override fun onDestroy() {
        startGeneration += 1
        stopEffects()
        if (currentReminderId == reminderId) currentReminderId = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.bal.reminders.action.RING_START"
        const val ACTION_STOP = "com.bal.reminders.action.RING_STOP"
        const val ACTION_ALARM_STOPPED = "com.bal.reminders.action.ALARM_STOPPED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrence_millis"
        const val EXTRA_ATTEMPT = "attempt"

        @Volatile
        private var currentReminderId: Long? = null

        fun isRingingFor(reminderId: Long): Boolean = currentReminderId == reminderId

        /** Stops the ringer if (and only if) it is sounding for [reminderId]. */
        fun stop(context: Context, reminderId: Long) {
            if (currentReminderId != reminderId) return
            runCatching {
                context.startService(
                    Intent(context, AlarmRingerService::class.java)
                        .setAction(ACTION_STOP)
                        .putExtra(EXTRA_REMINDER_ID, reminderId),
                )
            }
        }
    }
}
