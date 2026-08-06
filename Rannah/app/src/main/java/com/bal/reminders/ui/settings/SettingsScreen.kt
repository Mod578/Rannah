package com.bal.reminders.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.ui.components.ChoiceChips
import com.bal.reminders.ui.theme.Space

/**
 * Two choices and two doors. رَنّة has no preferences worth a screen of
 * switches: the theme, how long «تأجيل» lasts, and the way out to permissions
 * and to «عن رَنّة».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reflect the real permission state when the user returns from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            SettingCard(stringResource(R.string.settings_theme)) {
                ChoiceChips(
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ),
                    selected = state.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }

            SettingCard(stringResource(R.string.settings_default_snooze)) {
                ChoiceChips(
                    // Android plurals, not «%s دقيقة» pasted together: «٥ دقائق»
                    // and «١٥ دقيقة» are different words in Arabic, and the
                    // notification has always said them correctly while this
                    // screen said «٥ دقيقة».
                    options = Reminder.SNOOZE_CHOICES.map { minutes ->
                        minutes to pluralStringResource(
                            R.plurals.snooze_minutes_option,
                            minutes,
                            minutes,
                        )
                    },
                    selected = state.defaultSnoozeMinutes,
                    onSelect = viewModel::setDefaultSnooze,
                )
                Text(
                    stringResource(R.string.settings_default_snooze_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsLink(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.settings_permissions),
                subtitle = stringResource(
                    if (state.permissionsOk) {
                        R.string.settings_permissions_ok
                    } else {
                        R.string.settings_permissions_attention
                    },
                ),
                warn = !state.permissionsOk,
                onClick = onOpenPermissions,
            )

            SettingsLink(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.privacy_title),
                subtitle = null,
                onClick = onOpenPrivacy,
            )

            SettingsLink(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.settings_about_title),
                subtitle = null,
                onClick = onOpenAbout,
            )

            Spacer(Modifier.height(Space.lg))
        }
    }
}

/** A titled block of controls, on its own card. */
@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun SettingsLink(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    warn: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warn) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            // Auto-mirrored: it points the way the next screen comes from, which
            // in Arabic is leftwards. The un-mirrored left chevron this replaced
            // was flipping to point right: back, in an RTL layout.
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
