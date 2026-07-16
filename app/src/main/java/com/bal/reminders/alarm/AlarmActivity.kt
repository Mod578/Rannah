package com.bal.reminders.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.format.BalFormats
import com.bal.reminders.scheduling.AlarmRingerService
import com.bal.reminders.ui.MainViewModel
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.SlideToConfirm
import com.bal.reminders.ui.editor.MinutesChoiceRow
import com.bal.reminders.ui.theme.BalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The full-screen «منبّه مهم» surface, shown over the lock screen while the
 * ringer sounds. إيقاف silences; completion is offered as its own explicit
 * step afterwards, because stopping a sound is not fulfilling an obligation.
 * launchMode=singleInstance keeps one screen per ringing occurrence.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The ringer ended elsewhere (notification action, timeout, another
            // device surface). If we are not in the completion prompt, close.
            if (viewModel.currentPhase.value == AlarmPhase.RINGING) finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        val locale = Locale("ar")
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        readIntent(intent)

        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(AlarmRingerService.ACTION_ALARM_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val mainState by mainViewModel.state.collectAsStateWithLifecycle()
            val dark = when (mainState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            BalTheme(darkTheme = dark) {
                AlarmScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        val occurrence = intent?.getLongExtra(EXTRA_OCCURRENCE_MILLIS, 0L) ?: 0L
        if (id > 0 && occurrence > 0) {
            viewModel.load(id, Instant.ofEpochMilli(occurrence))
        } else {
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_MILLIS = "occurrence_millis"
    }
}

/**
 * The alarm hierarchy, in the order a half-awake person meets it:
 *
 * 1. **تأجيل** — large, obvious, and reversible. The safest thing to hand
 *    someone who is not ready to decide is a way to postpone, not a way to
 *    dismiss.
 * 2. **اسحب للتأكيد: <عبارة الإنجاز>** — deliberate, and phrased as the claim
 *    it actually records («سجلت البصمة»), not an abstract «تم».
 * 3. **إيقاف الصوت فقط** — quiet, secondary, and honest about its own limits:
 *    it ends a sound and says nothing about the task.
 *
 * Nothing destructive is reachable from here, and nothing is icon-only.
 */
@Composable
private fun AlarmScreen(viewModel: AlarmViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var changingSnooze by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.closed.collect { activity?.finish() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val reminder = state.reminder ?: return@Surface

        // The wall clock, not the occurrence: the first question at 5am is
        // "what time is it now?". It re-reads on each phase change and tick.
        val now by produceState(LocalTime.now()) {
            while (true) {
                value = LocalTime.now()
                delay(10_000)
            }
        }
        val ringing = state.phase == AlarmPhase.RINGING
        val completionLabel = reminder.completionLabel?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.editor_completion_default)
        val snoozeLabel = context.resources.getQuantityString(
            R.plurals.notification_snooze_minutes,
            reminder.snoozeMinutes,
            reminder.snoozeMinutes,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppMark(
                stroke = MaterialTheme.colorScheme.onBackground,
                dot = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            // Alarm state in words, never colour alone.
            Text(
                text = stringResource(
                    if (ringing) R.string.alarm_ringing_now else R.string.alarm_stopped_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = BalFormats.time(context, now),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = BalFormats.scheduleSummary(context, reminder.schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            reminder.notes?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))

            // 1. Reversible, and the biggest thing on the screen.
            if (ringing) {
                Button(
                    onClick = { viewModel.snooze() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(snoozeLabel, style = MaterialTheme.typography.headlineSmall)
                }
                TextButton(
                    onClick = { changingSnooze = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.alarm_snooze_change))
                }
                Spacer(Modifier.height(16.dp))
            }

            // 2. Deliberate: says what is being claimed, and asks for a movement.
            SlideToConfirm(
                text = stringResource(R.string.alarm_slide_to_confirm, completionLabel),
                hint = stringResource(R.string.alarm_slide_hint),
                onConfirm = viewModel::markDone,
            )

            Spacer(Modifier.height(20.dp))

            // 3. Honest about its own limits.
            if (ringing) {
                OutlinedButton(
                    onClick = viewModel::stop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(R.string.alarm_stop),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (reminder.followUntilComplete) {
                            R.string.alarm_stop_explains_follow
                        } else {
                            R.string.alarm_stop_explains_none
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                OutlinedButton(
                    onClick = { viewModel.snooze() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(snoozeLabel, style = MaterialTheme.typography.titleLarge)
                }
            }

            if (reminder.schedule.isRecurring) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = viewModel::skipOnce,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_skip_once))
                }
            }
            if (!ringing) {
                TextButton(
                    onClick = viewModel::notYet,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.alarm_not_yet))
                }
            }
        }
    }

    // One focused control for the duration, rather than a row of buttons
    // competing with the action they modify.
    if (changingSnooze) {
        val reminder = state.reminder
        AlertDialog(
            onDismissRequest = { changingSnooze = false },
            title = { Text(stringResource(R.string.editor_section_snooze)) },
            text = {
                MinutesChoiceRow(
                    options = listOf(5, 10, 15, 30),
                    selected = reminder?.snoozeMinutes ?: 10,
                    onSelect = {
                        changingSnooze = false
                        viewModel.snooze(it)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { changingSnooze = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
