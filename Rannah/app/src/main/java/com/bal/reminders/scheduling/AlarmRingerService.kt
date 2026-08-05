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
 * The ringer: a foreground service (systemExempted type, started from the exact
 * alarm broadcast) that loops the device alarm ringtone on the alarm stream,
 * vibrates, ramps the volume up gently, and gives up quietly after a timeout, 
 * leaving the occurrence unresolved («يحتاج تأكيدك») rather than deciding
 * anything for the user. تأجيل and تم always route through the scheduler from the
 * alarm screen.
 *
 * The alarm stream is why silent mode does not mute it, and why Do Not Disturb
 * only mutes it when the user excluded alarms in their DND settings.
 */
@AndroidEntryPoint
class AlarmRingerService : Service() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var scheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private var reminderId: Long = 0L
    private var occurrenceAt: Instant = Instant.EPOCH
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
                    start(id, at)
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

    private fun start(id: Long, occurrence: Instant) {
        // A newer alarm replaces whatever was ringing.
        val generation = ++startGeneration
        stopEffects()
        reminderId = id
        occurrenceAt = occurrence
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
            acquireWakeLock()
            ring()
            vibrate()
            handler.postDelayed({ onTimeout() }, TIMEOUT_MS)
        }
    }

    private fun ring() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audioManager.requestAudioFocus(it) }

        val candidates = buildList<Uri> {
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
                rampVolume(prepared, step = 0)
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

    private fun vibrate() {
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

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ranna:alarm").apply {
            acquire(TIMEOUT_MS + 60_000L)
        }
    }

    /**
     * The alarm rang its full length unanswered: stop quietly. The occurrence
     * stays «يحتاج تأكيدك»: رَنّة decides nothing for the user, but the fact
     * that it rang out is written down, so an ignored ring leaves a trace in the
     * reminder's history instead of vanishing when the day turns.
     */
    private fun onTimeout() {
        val id = reminderId
        val occurrence = occurrenceAt
        if (id > 0 && occurrence != Instant.EPOCH) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                scheduler.markMissed(id, occurrence)
            }
        }
        finish(removeNotification = true)
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

        private val TIMEOUT_MS = Reminder.DEFAULT_ALARM_TIMEOUT_MINUTES * 60_000L

        @Volatile
        private var currentReminderId: Long? = null

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
