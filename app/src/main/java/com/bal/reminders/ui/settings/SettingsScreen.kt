package com.bal.reminders.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bal.reminders.R
import com.bal.reminders.data.DateDisplay
import com.bal.reminders.data.ThemeMode
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.ChoiceChips
import com.bal.reminders.ui.components.NajdiRule
import com.bal.reminders.ui.components.SectionTitle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_group_appearance))
            ChoiceChips(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                ),
                selected = state.themeMode,
                onSelect = viewModel::setThemeMode,
            )

            // التاريخ والتقويم: العرض والتصحيح الهجري معًا. التصحيح كان يُقرأ
            // خيارًا متقدمًا منفصلًا، وهو في الحقيقة جزء من كيف تقرأ رَنّة التقويم.
            SettingsGroup(stringResource(R.string.settings_group_calendar))
            SectionTitle(stringResource(R.string.settings_date_display))
            ChoiceChips(
                options = listOf(
                    DateDisplay.BOTH to stringResource(R.string.settings_date_both),
                    DateDisplay.HIJRI to stringResource(R.string.settings_date_hijri),
                    DateDisplay.GREGORIAN to stringResource(R.string.settings_date_gregorian),
                ),
                selected = state.dateDisplay,
                onSelect = viewModel::setDateDisplay,
            )
            if (state.dateDisplay != DateDisplay.GREGORIAN) {
                SectionTitle(stringResource(R.string.settings_hijri_adjust))
                ChoiceChips(
                    options = listOf(-2, -1, 0, 1, 2).map { it to adjustmentLabel(it) },
                    selected = state.hijriAdjustmentDays,
                    onSelect = viewModel::setHijriAdjustment,
                )
                Text(
                    stringResource(R.string.settings_hijri_adjust_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val today = LocalDate.now()
                val todayHijri = BalFormats.hijriDate(today, state.hijriAdjustmentDays)
                if (todayHijri != null) {
                    Text(
                        stringResource(
                            R.string.settings_hijri_today,
                            "${BalFormats.weekdayName(today)} $todayHijri",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            SettingsGroup(stringResource(R.string.settings_group_alerts))
            SectionTitle(stringResource(R.string.settings_default_snooze))
            ChoiceChips(
                options = listOf(5, 10, 15, 30).map { minutes ->
                    minutes to stringResource(
                        R.string.editor_snooze_option,
                        BalFormats.arabicDigits(minutes.toString()),
                    )
                },
                selected = state.defaultSnoozeMinutes,
                onSelect = viewModel::setDefaultSnooze,
            )
            Text(
                stringResource(R.string.settings_default_snooze_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsLink(
                icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
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

            SettingsGroup(stringResource(R.string.settings_group_log))
            SettingsLink(
                icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                title = stringResource(R.string.log_title),
                subtitle = stringResource(R.string.settings_log_subtitle),
                onClick = onOpenLog,
            )

            SettingsGroup(stringResource(R.string.settings_group_about))
            SettingsLink(
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onClick = onOpenAbout,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A named settings group.
 *
 * The screen used to be a flat run of controls separated by hairlines, which
 * made «تصحيح التاريخ الهجري» look like a peer of «المظهر». Naming the groups
 * is what makes the list skimmable; the stepped rule underneath is the Najdi
 * rhythm doing structural work — marking where one idea ends — rather than
 * decorating a surface.
 */
@Composable
private fun SettingsGroup(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    NajdiRule(Modifier.padding(top = 4.dp, bottom = 4.dp))
}

/** «-٢» / «٠» / «+١» for the Hijri sighting adjustment buttons. */
private fun adjustmentLabel(days: Int): String = when {
    days > 0 -> "+" + BalFormats.arabicDigits(days.toString())
    days < 0 -> "-" + BalFormats.arabicDigits((-days).toString())
    else -> BalFormats.arabicDigits("0")
}

@Composable
private fun SettingsLink(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    warn: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
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
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
