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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.applyBarAppearance
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.format.BalFormats
import com.bal.reminders.scheduling.AlarmRingerService
import com.bal.reminders.ui.MainViewModel
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.SlideToConfirm
import com.bal.reminders.ui.components.SnoozeSheet
import com.bal.reminders.ui.theme.BalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The full-screen alarm surface, shown over the lock screen while the ringer
 * sounds. It offers exactly two answers: «تأجيل» postpones and closes; «تم»
 * reveals a deliberate slide-to-confirm, and only completing that slide records
 * the occurrence as done. Nothing here: a background tap, the back gesture,
 * leaving the screen: ever counts as completion.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The ringer ended elsewhere (timeout, another surface). Close, unless
            // the user is mid-confirmation (we hushed the ring for them on «تم»).
            if (!viewModel.isConfirming.value) finish()
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
        // Android 15 draws this window edge-to-edge whether it is asked to or
        // not. Declaring it here means the inset values are real, so the screen
        // can pad itself instead of hoping its fixed margins clear the bars.
        enableEdgeToEdge()
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
            SideEffect { applyBarAppearance(dark) }
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

        // The wall clock, not the occurrence: the first question at 5am is
        // "what time is it now?". It re-reads on each tick.
        val now by produceState(LocalTime.now()) {
            while (true) {
                value = LocalTime.now()
                delay(10_000)
            }
        }
        // The label reads the setting, so it is always describing what the
        // button will actually do: including right after the user changed it.
        val snoozeLabel = context.resources.getQuantityString(
            R.plurals.notification_snooze_minutes,
            state.defaultSnoozeMinutes,
            state.defaultSnoozeMinutes,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Outside the scroll, so «تم» and «تأجيل» can never be pushed
                // under the navigation bar however long the title is.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // While the ring sounds, the mark breathes: a slow swell, the one
            // place motion says "this is happening now". It used to rotate
            // about a point 14% down the height, which was a bell's hanging
            // loop; the mark has no loop to hang from, and a tilted نون reads
            // as a mistake rather than as movement. Scale carries the same
            // meaning and stays true whatever the identity is.
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(760, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse-scale",
            )
            AppMark(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val s = if (state.confirming) 1f else pulse
                        scaleX = s
                        scaleY = s
                    },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.alarm_ringing_now),
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
            Spacer(Modifier.height(36.dp))

            if (state.confirming) {
                // The deliberate step: says what is being claimed, and asks for a
                // movement nobody performs by accident.
                SlideToConfirm(
                    text = stringResource(R.string.alarm_slide_to_confirm),
                    hint = stringResource(R.string.alarm_slide_hint),
                    onConfirm = viewModel::markDone,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = viewModel::cancelConfirm,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            } else {
                // تأجيل: the safe, reversible choice, largest on the screen.
                Button(
                    onClick = viewModel::snooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        snoozeLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // «مدة أخرى»: one quiet, ordinary, focusable button: not a
                // long-press, which TalkBack and switch access cannot reach and
                // nobody discovers. Everything it offers lives one layer down,
                // so the ringing screen keeps its two large answers.
                TextButton(
                    onClick = viewModel::openSnoozeOptions,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.snooze_other),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // تم: hush the ring (postpone-safe) and reveal the slide.
                OutlinedButton(
                    onClick = {
                        viewModel.beginConfirm()
                        AlarmRingerService.stop(context, reminder.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(R.string.action_done),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        state.snoozeOptions?.let { options ->
            SnoozeSheet(
                limit = options.limit,
                rejected = options.rejected,
                onPick = viewModel::applySnooze,
                onDismiss = viewModel::dismissSnoozeOptions,
            )
        }
    }
}
