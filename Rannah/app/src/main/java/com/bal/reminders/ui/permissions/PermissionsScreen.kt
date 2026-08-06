package com.bal.reminders.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bal.reminders.R
import com.bal.reminders.scheduling.NotificationPresenter
import com.bal.reminders.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Permissions.status(context)) }

    // Refresh when the user returns from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = Permissions.status(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { status = Permissions.status(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = Space.screen, end = Space.screen, bottom = Space.scrollBottom),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            // The verdict first, in one sentence, before any of the machinery:
            // «رَنّة جاهزة للتنبيه» is the only thing most people came to learn.
            ReadinessSummary(status)

            PermissionCard(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.permissions_notifications_title),
                body = stringResource(R.string.permissions_notifications_body),
                granted = status.notificationsGranted,
                actionLabel = stringResource(R.string.permissions_enable),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Permissions.notificationSettingsIntent(context))
                    }
                },
            )

            PermissionCard(
                icon = Icons.Rounded.Alarm,
                title = stringResource(R.string.permissions_exact_title),
                body = stringResource(R.string.permissions_exact_body),
                granted = status.exactAlarmsGranted,
                actionLabel = stringResource(R.string.permissions_open_settings),
                onAction = {
                    context.startActivity(Permissions.exactAlarmSettingsIntent(context))
                },
            )

            // شاشة المنبّه الكاملة: قابلة للسحب من النظام في أندرويد 14+.
            PermissionCard(
                icon = Icons.Rounded.Fullscreen,
                title = stringResource(R.string.permissions_fsi_title),
                body = stringResource(R.string.permissions_fsi_body),
                granted = status.fullScreenAlarmGranted,
                optional = true,
                actionLabel = stringResource(R.string.permissions_open_settings),
                onAction = {
                    context.startActivity(Permissions.fullScreenIntentSettingsIntent(context))
                },
            )

            PermissionCard(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.permissions_battery_title),
                body = stringResource(R.string.permissions_battery_body),
                granted = status.batteryUnrestricted,
                optional = true,
                actionLabel = stringResource(R.string.permissions_open_settings),
                onAction = {
                    context.startActivity(Permissions.batterySettingsIntent())
                },
            )

            // «الرنّات صامتة» used to be reported as a blocking problem with no
            // way to act on it: the readiness summary named it, the home banner
            // linked here, and this screen had no card for it. The intent to fix
            // it already existed and had no caller.
            if (status.alarmChannelBlocked) {
                PermissionCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.permissions_channel_title),
                    body = stringResource(R.string.permissions_channel_body),
                    granted = false,
                    actionLabel = stringResource(R.string.permissions_channel_action),
                    onAction = {
                        context.startActivity(
                            Permissions.channelSettingsIntent(
                                context,
                                NotificationPresenter.CHANNEL_ALARM,
                            ),
                        )
                    },
                )
            }

            if (status.alarmVolumeMuted) {
                PermissionCard(
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    title = stringResource(R.string.permissions_alarm_volume_title),
                    body = stringResource(R.string.permissions_alarm_volume_body),
                    granted = false,
                    optional = true,
                    actionLabel = stringResource(R.string.permissions_open_settings),
                    onAction = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))
                    },
                )
            }

            // A limit the app cannot lift, and the user is entitled to know it.
            Text(
                stringResource(R.string.permissions_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )

            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (granted) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.permissions_granted),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (optional && !granted) {
                Text(
                    stringResource(R.string.permissions_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * «رَنّة جاهزة للتنبيه», or the list of what is stopping it.
 *
 * Reports only what is actionable: when nothing is wrong it is one calm line,
 * not a wall of green ticks asking to be audited.
 */
@Composable
private fun ReadinessSummary(status: PermissionsStatus) {
    val issues = status.issues()
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (issues.isEmpty()) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (issues.isEmpty()) {
                Text(
                    stringResource(R.string.readiness_ready_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.readiness_ready_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Text(
                    stringResource(R.string.readiness_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                issues.forEach { issue ->
                    Text(
                        stringResource(issue.titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (issue.blocking) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
