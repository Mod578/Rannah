package com.bal.reminders.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.ui.theme.BalTheme

// ------------------------------------------------------------- the رَنّة mark

/**
 * The «رَنّة» mark: the نون that carries the sound in the name, drawn as one
 * calligraphic stroke — thin where the pen enters, heavy through the bowl,
 * lifting again on the way out — with its dot set clear of the rising terminal.
 *
 * The dot sits above that terminal rather than above the centre of the bowl.
 * That is a designed departure from the letter: it puts the mark on a diagonal,
 * so it never reads as a face, and it lets the dot read as the one thing the
 * bowl has released. The ring is said by that posture, not by drawn waves.
 *
 * Two broad solids on a 24-unit grid, no hairline and no colour dependency, so
 * the same geometry survives the launcher mask, themed icons, the 24dp status
 * bar and 20dp inline use. The geometry itself lives in [MARK_PATH_DATA],
 * generated once and shared with every drawable.
 */
@Composable
fun AppMark(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val path = remember { PathParser().parsePathString(MARK_PATH_DATA).toPath() }
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path, tint)
        }
    }
}

// ------------------------------------------------------------- the colophon

/**
 * «صُنع في السعودية» — the origin line, set as a colophon: two hairlines and the
 * words between them, in the quiet slate accent, at label size. Quiet enough to
 * belong at the foot of a screen, deliberate enough to read as part of the
 * identity — and never in a status colour, which would make it look like news.
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

/**
 * A section heading and how many reminders sit under it.
 *
 * The count is a bare numeral on screen, which is right for the eye and wrong
 * for TalkBack: read out, «اليوم ٣» is a heading followed by a number with no
 * noun. The whole row therefore carries one spoken description that names what
 * is being counted, and the two visible texts are muted so the number is not
 * announced twice.
 */
@Composable
fun SectionTitle(text: String, count: Int? = null) {
    val spoken = if (count != null && count > 0) {
        "$text، ${pluralStringResource(R.plurals.reminders_count, count, count)}"
    } else {
        text
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------- the checklist row

/** How loudly a row speaks: waiting for an answer, postponed, overdue, or just listed. */
enum class RowTone { Normal, Waiting, Snoozed, Overdue, Muted }

/**
 * One reminder on the list: what kind it is, when it rings, and — where it means
 * something — the answers that belong to **today only**.
 *
 * [kindLabel] is always shown, so the kind of reminder a row describes («مرة
 * واحدة», «يومي», «أيام العمل», «شهري») never has to be inferred from an icon or
 * remembered from the editor.
 *
 * The leading ring means «تم» and answers **this occurrence only**. It is the
 * one way to complete from the list: the row used to also complete on a
 * horizontal drag, which is how a finger sliding down a list could record that
 * medicine was taken when it was not. A hidden gesture is a bad way to assert a
 * fact, so it is gone; the ring is latched against double activation, and an
 * undo snackbar still covers a mis-tap.
 *
 * [onSkip] is «تخطي اليوم» — a labelled secondary action, not an overflow menu
 * holding a single item, and never offered on a one-time reminder, which has no
 * tomorrow to keep.
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

    val confirmed = remember(title, meta) { mutableStateOf(false) }

    fun fire() {
        if (confirmed.value) return
        confirmed.value = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onComplete?.invoke()
    }

    Box(modifier.fillMaxWidth()) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
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
                        // Status colour comes from the status roles, never from
                        // whatever Material role the brand happens to occupy.
                        color = when (tone) {
                            RowTone.Waiting -> MaterialTheme.colorScheme.primary
                            RowTone.Snoozed -> BalTheme.status.snoozed
                            RowTone.Overdue -> BalTheme.status.overdue
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
 * this one item — two taps and a guess for one action, and the trigger said
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
 *
 * A row waiting for an answer differs from a listed one by **three** things,
 * not one: a heavier stroke, a filled centre, and the action colour. It used to
 * differ by border colour alone, which is invisible to a red-green colour
 * deficiency and to anyone reading the screen in sunlight.
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
            border = BorderStroke(
                if (waiting) 3.dp else 2.dp,
                if (waiting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.size(30.dp).clearAndSetSemantics {},
        ) {
            if (waiting) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp),
                    ) {}
                }
            }
        }
    }
}

/**
 * An occurrence that is finished for today — completed, or deliberately skipped.
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
                    BalTheme.status.done
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
                            MaterialTheme.colorScheme.surfaceContainerLowest
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
 * Shift a node along the reading direction. The one draggable surface left in
 * the app is the alarm screen's slide-to-confirm, and `placeRelative` already
 * mirrors for RTL, so the caller supplies "how far onward" and never a sign.
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
 * The signature empty state: the «رَنّة» mark drawn calm and large, standing on
 * its own. No stock illustration, and no container behind it — the mark is the
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
