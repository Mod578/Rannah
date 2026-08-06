package com.bal.reminders.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bal.reminders.R
import com.bal.reminders.domain.model.OccurrenceStatus
import com.bal.reminders.ui.theme.BalTheme

/**
 * The single place an occurrence status becomes words, an icon and a colour,
 * so the same outcome never reads one way in the history and another way in
 * the details screen.
 */
val OccurrenceStatus.labelRes: Int
    get() = when (this) {
        OccurrenceStatus.COMPLETED -> R.string.status_completed
        OccurrenceStatus.SKIPPED -> R.string.status_skipped
        OccurrenceStatus.IGNORED -> R.string.status_ignored
        OccurrenceStatus.MISSED -> R.string.status_missed
    }

val OccurrenceStatus.icon: ImageVector
    get() = when (this) {
        OccurrenceStatus.COMPLETED -> Icons.Rounded.Check
        OccurrenceStatus.SKIPPED -> Icons.AutoMirrored.Rounded.Redo
        OccurrenceStatus.IGNORED -> Icons.Rounded.NotificationsPaused
        OccurrenceStatus.MISSED -> Icons.Rounded.EventBusy
    }

/**
 * Status colours come from the status roles, not from Material's accent slots.
 *
 * They used to: «أُنجزت» wore `secondary` and «فات موعده» wore `error`, so when
 * the brand's secondary became a warm red the app ended up telling the user
 * that done and late were the same kind of news, in two adjacent reds. Done is
 * now green wherever it appears, and late is red wherever it appears, whatever
 * the identity does next.
 *
 * Ignored and missed still share one colour: both mean the obligation did not
 * happen, and the difference between them belongs in the label, not in a colour
 * the user has to decode.
 */
val OccurrenceStatus.color: Color
    @Composable @ReadOnlyComposable
    get() = when (this) {
        OccurrenceStatus.COMPLETED -> BalTheme.status.done
        OccurrenceStatus.SKIPPED -> BalTheme.status.skipped
        OccurrenceStatus.IGNORED -> BalTheme.status.overdue
        OccurrenceStatus.MISSED -> BalTheme.status.overdue
    }
