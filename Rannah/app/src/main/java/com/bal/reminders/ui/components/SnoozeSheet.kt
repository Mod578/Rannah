package com.bal.reminders.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.SnoozeRequest
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.theme.Space
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What «مدة أخرى» opens, from the alarm screen and from details alike.
 *
 * The alarm screen itself keeps exactly two answers and one quiet way out to
 * this sheet. Putting five durations on the ringing screen would have crowded
 * the one surface رَنّة is read on half-awake, in the dark, with a sound going;
 * putting them behind a long-press would have hidden them from TalkBack and from
 * anyone who cannot hold a press. So: one ordinary labelled button, one layer
 * down, everything named.
 *
 * The choice made here applies to **this occurrence only** and is never
 * remembered. The next ring goes back to «مدة التأجيل الافتراضية», so the big
 * button's label is always telling the truth, a duration silently inherited
 * from a decision made at 3am is worse than no flexibility at all.
 *
 * [limit] is the last instant this occurrence may be postponed to. A choice past
 * it is refused with the reason, never silently clamped: the reminder's own next
 * occurrence is on the other side of that line, and one reminder has one alarm.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SnoozeSheet(
    limit: Instant?,
    rejected: Instant?,
    onPick: (SnoozeRequest) -> Unit,
    onDismiss: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone: ZoneId = clock.zone
    var customOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                stringResource(R.string.snooze_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.snooze_sheet_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (rejected != null) {
                Text(
                    stringResource(
                        R.string.snooze_too_late,
                        BalFormats.dateTime(context, rejected, zone, clock.instant()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (limit != null && !limit.isAfter(clock.instant())) {
                // The reminder's next occurrence is already upon us: there is no
                // room left to postpone into, and saying so is better than
                // offering choices that will all be refused.
                Text(
                    stringResource(R.string.snooze_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Space.xs))

            if (!customOpen) {
                // Durations wrap and each keeps a 48dp target, so the group
                // survives 200% type on a narrow screen instead of clipping.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Reminder.SNOOZE_CHOICES.forEach { minutes ->
                        val allowed = limit == null ||
                            !clock.instant().plusSeconds(minutes * 60L).isAfter(limit)
                        AssistChip(
                            onClick = { onPick(SnoozeRequest.Minutes(minutes)) },
                            enabled = allowed,
                            label = {
                                Text(
                                    context.resources.getQuantityString(
                                        R.plurals.snooze_minutes_option,
                                        minutes,
                                        minutes,
                                    ),
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                TextButton(
                    onClick = { customOpen = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.snooze_until_time)) }
            } else {
                UntilTimePicker(
                    zone = zone,
                    now = clock.instant(),
                    limit = limit,
                    onConfirm = { onPick(SnoozeRequest.Until(it)) },
                    onBack = { customOpen = false },
                )
            }
        }
    }
}

/**
 * «حتى وقت محدد». The resolved instant is spelled out in full before anything is
 * confirmed: «اليوم، ٨:٣٠ مساءً» or «غدًا، ٦:٠٠ صباحًا», so whether the chosen
 * clock time belongs to today or tomorrow is *stated*, never left for the user
 * to work out from a bare time and a hope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilTimePicker(
    zone: ZoneId,
    now: Instant,
    limit: Instant?,
    onConfirm: (Instant) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val seed = remember(now) { now.plusSeconds(3600).atZone(zone).toLocalTime().withSecond(0).withNano(0) }
    val state = rememberTimePickerState(initialHour = seed.hour, initialMinute = seed.minute, is24Hour = false)

    val target = remember(state.hour, state.minute, now) {
        resolveNext(LocalTime.of(state.hour, state.minute), now, zone)
    }
    val tooLate = limit != null && target.isAfter(limit)

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(state = state)
        }
        Text(
            stringResource(
                R.string.snooze_until_resolved,
                BalFormats.dateTime(context, target, zone, now),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = if (tooLate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (tooLate && limit != null) {
            Text(
                stringResource(
                    R.string.snooze_too_late,
                    BalFormats.dateTime(context, limit, zone, now),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = { onConfirm(target) },
            enabled = !tooLate,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text(stringResource(R.string.snooze_confirm)) }
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.action_back)) }
    }
}

/** The next occurrence of [time] strictly after [now], today if it is still ahead, else tomorrow. */
internal fun resolveNext(time: LocalTime, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    val candidate = today.atTime(time).atZone(zone).toInstant()
    return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(time).atZone(zone).toInstant()
}
