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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Snooze
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
import com.bal.reminders.ui.theme.BalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

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

@Composable
private fun AlarmScreen(viewModel: AlarmViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    LaunchedEffect(Unit) {
        viewModel.closed.collect { activity?.finish() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val reminder = state.reminder ?: return@Surface
        val occurrenceTime = BalFormats.time(
            context,
            state.occurrenceAt.atZone(ZoneId.systemDefault()).toLocalTime(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppMark(
                stroke = MaterialTheme.colorScheme.primary,
                dot = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = occurrenceTime,
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
            reminder.notes?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(48.dp))

            when (state.phase) {
                AlarmPhase.RINGING -> {
                    Button(
                        onClick = viewModel::stop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            stringResource(R.string.alarm_stop),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = viewModel::snooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            LocalContext.current.resources.getQuantityString(
                                R.plurals.notification_snooze_minutes,
                                reminder.snoozeMinutes,
                                reminder.snoozeMinutes,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                AlarmPhase.STOPPED_PROMPT -> {
                    Text(
                        text = stringResource(R.string.alarm_stopped_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.alarm_done_question, reminder.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::markDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            stringResource(R.string.action_complete_short),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (reminder.schedule.isRecurring) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::skipOnce,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(stringResource(R.string.action_skip_once))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = viewModel::notYet) {
                        Text(stringResource(R.string.alarm_not_yet))
                    }
                }
            }
        }
    }
}
