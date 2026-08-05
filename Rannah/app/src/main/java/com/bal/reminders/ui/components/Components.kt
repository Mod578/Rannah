package com.bal.reminders.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import kotlinx.coroutines.launch

// ------------------------------------------------------------- the bell mark

/**
 * The «رَنّة» mark: a bell caught mid-swing. The body leans 12°, and the clapper
 * hangs the other way: the lag of a bell that has just been struck. That is the
 * whole idea: **ringing is said by posture**, not by hairlines beside the bell.
 *
 * The mark it replaced carried two wi-fi-style arcs 2.5% of the icon wide, a
 * clapper floating free below the rim, and a notch cut out of the bell's
 * shoulder to make room for the arcs. All three were the first things to vanish
 * or to look like damage: at the sizes that decide whether an icon is
 * recognised: 24dp in the status bar, 48dp on a home screen, one flat tint under
 * a themed-icon mask.
 *
 * The geometry is one canonical path on a 0..24 grid, and every surface wears it
 * unchanged: the launcher tile, the monochrome layer, the splash, the status-bar
 * glyph and every place the mark appears inside the app. The four XML vectors
 * carry the same string verbatim, because XML cannot import Kotlin; nothing else
 * redraws the bell.
 *
 * One colour, always. The crown and the clapper **overlap** the body, so the
 * mark is a single solid shape at any scale and under any tint: there is no
 * second colour left to lose, and nothing that can come apart.
 */
@Composable
fun AppMark(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val path = remember { PathParser().parsePathString(PATH_MARK).toPath() }
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path, tint)
        }
    }
}

/**
 * The bell, leaning, with its crown and clapper, one closed silhouette on a
 * 0..24 grid. Optically centred on (12, 12); its bounds are x [4.71, 17.64] and
 * y [2.23, 20.06].
 */
private const val PATH_MARK =
    "M4.73,14.86 C5.57,14.02 7.30,12.14 7.76,10.19 C8.45,6.96 10.86,4.51 13.55,5.08 " +
        "C16.24,5.65 17.45,8.88 16.76,12.10 C16.39,14.07 17.21,16.49 17.64,17.61 " +
        "C17.52,18.14 16.99,18.49 16.45,18.38 L5.50,16.05 C4.96,15.93 4.61,15.40 4.73,14.86 Z " +
        "M13.85,2.22 C14.65,2.22 15.29,2.87 15.29,3.66 C15.29,4.46 14.65,5.10 13.85,5.10 " +
        "C13.06,5.10 12.41,4.46 12.41,3.66 C12.41,2.87 13.06,2.22 13.85,2.22 Z " +
        "M10.60,15.39 L12.05,15.70 L9.94,18.73 L8.37,18.40 Z " +
        "M9.15,17.06 C9.98,17.06 10.65,17.73 10.65,18.56 C10.65,19.39 9.98,20.06 9.15,20.06 " +
        "C8.32,20.06 7.65,19.39 7.65,18.56 C7.65,17.73 8.32,17.06 9.15,17.06 Z"

// ------------------------------------------------------------- the colophon

/**
 * «صُنع في السعودية»: the origin line, set as a colophon of two hairlines and the
 * words between them, in the brass accent, at label size. Quiet enough to belong
 * at the foot of a screen, deliberate enough to read as part of the identity.
 *
 * This is **not** the «صنع في السعودية» programme mark. That logo is a
 * registered mark of the Saudi Made programme: it may be used only by a
 * registered member company (not an individual), for products enrolled in the
 * programme, and it may not be redrawn, recoloured or combined with other
 * symbols. رَنّة is not enrolled, so the app states its origin in its own words
 * and its own type instead of borrowing an official mark it has no right to.
 */
@Composable
fun MadeInSaudi(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hairline()
        Text(
            text = stringResource(R.string.made_in_saudi),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Hairline()
    }
}

@Composable
private fun Hairline() {
    HorizontalDivider(
        modifier = Modifier.width(26.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
    )
}

// ---------------------------------------------------------------- the section

@Composable
fun SectionTitle(text: String, count: Int? = null) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count != null && count > 0) {
            Text(
                text = com.bal.reminders.format.BalFormats.arabicDigits(count.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ----------------------------------------------------------- the checklist row

/** How loudly a row speaks: waiting for an answer, postponed, overdue, or just listed. */
enum class RowTone { Normal, Waiting, Snoozed, Overdue, Muted }

/**
 * One reminder on the list: what kind it is, when it rings, and (where it means
 * something) the answers that belong to **today only**.
 *
 * [kindLabel] is always shown, so the kind of reminder a row describes («مرة
 * واحدة», «يومي», «أيام العمل», «شهري») never has to be inferred from an icon or
 * remembered from the editor.
 *
 * The ring and the swipe both mean «تم» and both answer **this occurrence only**;
 * they are latched so a double activation cannot double-process, and an undo
 * snackbar covers a mis-tap. [onSkip] is «تخطي اليوم», a labelled secondary
 * action, not an overflow menu holding a single item, and never offered on a
 * one-time reminder, which has no tomorrow to keep.
 *
 * [onComplete] null means this occurrence cannot be answered yet (a future day,
 * a paused reminder): no ring, no swipe, so the row never offers an affordance
 * that does nothing. Nothing destructive is reachable from a row.
 */
@Composable
fun ChecklistRow(
    title: String,
    meta: String,
    kindLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: RowTone = RowTone.Normal,
    onComplete: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val confirmed = remember(title, meta) { mutableStateOf(false) }
    val offset = remember(title, meta) { Animatable(0f) }

    fun fire() {
        if (confirmed.value) return
        confirmed.value = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onComplete?.invoke()
    }

    Box(modifier.fillMaxWidth()) {
        // The reveal behind the row: a teal panel with a check on the leading
        // side, shown only while the row is being dragged toward completion.
        if (onComplete != null) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.action_done),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        val dragModifier = if (onComplete != null) {
            Modifier.pointerInput(title, meta) {
                val travel = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!confirmed.value) {
                            if (offset.value >= travel * 0.5f) fire()
                            else scope.launch { offset.animateTo(0f, tween(200)) }
                        }
                    },
                    onDragCancel = {
                        if (!confirmed.value) scope.launch { offset.animateTo(0f, tween(200)) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (confirmed.value) return@detectHorizontalDragGestures
                    val forward = if (rtl) -dragAmount else dragAmount
                    val next = (offset.value + forward).coerceIn(0f, travel)
                    scope.launch { offset.snapTo(next) }
                }
            }
        } else {
            Modifier
        }

        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .forwardShift { offset.value }
                .then(dragModifier),
        ) {
            Row(
                Modifier.padding(
                    start = if (onComplete != null) 8.dp else 16.dp,
                    end = if (trailing != null || onSkip != null) 8.dp else 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onComplete != null) {
                    CompleteRing(waiting = tone == RowTone.Waiting, label = title, onComplete = ::fire)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (tone == RowTone.Muted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = "$kindLabel · $meta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (tone) {
                            RowTone.Waiting -> MaterialTheme.colorScheme.primary
                            RowTone.Snoozed -> MaterialTheme.colorScheme.tertiary
                            RowTone.Overdue -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (onSkip != null) SkipButton(title = title, onClick = onSkip)
                trailing?.invoke()
            }
        }
    }
}

/**
 * «تخطي اليوم» on a row. It replaced an overflow menu whose entire contents were
 * this one item: two taps and a guess for one action, and the trigger said
 * "more" rather than what it did. A labelled button is one tap, says its own
 * name, and matches «استئناف» in the same slot on a paused row.
 */
@Composable
private fun SkipButton(title: String, onClick: () -> Unit) {
    val label = stringResource(R.string.action_skip_today)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 132.dp)
            .semantics { contentDescription = "$label: $title" },
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Redo,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The leading ring: an empty circle, and the accessible way to say «تم». The
 * visible ring stays small and calm; its touch target is a full 48dp so it is
 * reachable without becoming a big loud button.
 */
@Composable
private fun CompleteRing(waiting: Boolean, label: String, onComplete: () -> Unit) {
    val completeText = "${stringResource(R.string.action_done)}: $label"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onComplete)
            .semantics { contentDescription = completeText; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            // A row that is waiting for an answer wears the action colour, so the
            // one thing to do next is the first thing the eye lands on.
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (waiting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.size(30.dp).clearAndSetSemantics {},
        ) {}
    }
}

/**
 * An occurrence that is finished for today, completed, or deliberately skipped.
 *
 * The two outcomes are told apart three ways at once: the mark (a check or a
 * skip arrow), the leading word of [meta] («مكتمل» / «تم تخطيه»), and the fill
 * behind the mark. The row is deliberately quieter than a live one and offers
 * **only** undo: there is no way into the reminder from here, so a checkmark can
 * never become a path to deleting a repeating reminder.
 *
 * The text column and the undo button share the width rather than competing for
 * it, so a long title at 200% type pushes the button down instead of squeezing
 * it out of the row.
 */
@Composable
fun ClosedRow(
    title: String,
    meta: String,
    completed: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (completed) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (completed) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = null,
                        tint = if (completed) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val undoLabel = stringResource(R.string.action_undo)
            TextButton(
                onClick = onUndo,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(max = 110.dp)
                    .semantics { contentDescription = "$undoLabel: $title" },
            ) {
                Text(undoLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Shift a node along the reading direction. One helper, used by every draggable
 * surface in the app: `placeRelative` already mirrors for RTL, so the caller
 * supplies "how far onward" and never a sign.
 */
internal fun Modifier.forwardShift(x: () -> Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(x().toInt(), 0)
        }
    },
)

// ------------------------------------------------------------ empty state

/**
 * The signature empty state: the «رَنّة» bell drawn calm and large, standing on
 * its own. No stock illustration, and no container behind it, the mark is the
 * mark, on whatever surface it lands.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppMark(
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
