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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ------------------------------------------------------------- the bell mark

/**
 * The «رَنّة» mark: a solid bell caught mid-ring.
 *
 * Three parts and nothing else — a stout crown bar, a body that narrows at the
 * shoulders and flares to a flat rim, and the ring itself: a clapper hanging
 * clear below the mouth with one short arc of sound on each side. The silhouette
 * is the whole idea, so it survives being shrunk to a 24 dp status-bar glyph and
 * still reads as a bell and nothing else.
 *
 * Two colours, split by meaning rather than decoration: the bell is one solid
 * [body] colour, and everything that *is* the ring — clapper and arcs — is the
 * warm [ring] accent. One geometry source, on a 0..24 grid; the three XML
 * vectors (launcher, splash, notification) mirror these same numbers by hand,
 * because XML cannot import Kotlin.
 */
@Composable
fun AppMark(
    body: Color,
    ring: Color,
    modifier: Modifier = Modifier.size(96.dp),
) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun p(v: Float) = v * u

        // The crown: a stubby bar the bell hangs from. A bar, not a ring — a
        // circle up here turns the whole mark into a lollipop.
        drawRoundRect(
            color = body,
            topLeft = Offset(p(10.4f), p(2.3f)),
            size = Size(p(3.2f), p(2.4f)),
            cornerRadius = CornerRadius(p(1.2f)),
        )
        drawPath(buildBellPath(::p), color = body)
        // The ring: the clapper hanging clear of the mouth, and one arc of sound
        // either side. Short arcs, wide gap — motion, not a target symbol.
        val r = p(9.5f)
        val arcTopLeft = Offset(p(12f) - r, p(11.3f) - r)
        val arcSize = Size(r * 2, r * 2)
        val arcStroke = Stroke(width = p(1.25f), cap = StrokeCap.Round)
        drawArc(ring, -18f, 36f, false, arcTopLeft, arcSize, style = arcStroke)
        drawArc(ring, 162f, 36f, false, arcTopLeft, arcSize, style = arcStroke)
        drawCircle(color = ring, radius = p(1.45f), center = Offset(p(12f), p(20f)))
    }
}

/**
 * The bell body on a 0..24 grid, kept in sync with the pathData in
 * ic_launcher_foreground / ic_splash / ic_notification: narrow shoulders, a
 * long flare, and a flat rim closing the mouth — the classic bell profile,
 * drawn once and worn by every surface.
 */
private fun buildBellPath(p: (Float) -> Float): Path = Path().apply {
    moveTo(p(12f), p(4.35f))
    cubicTo(p(14.75f), p(4.35f), p(16.35f), p(6.6f), p(16.6f), p(10.1f))
    cubicTo(p(16.85f), p(13.5f), p(17.2f), p(15.4f), p(18.5f), p(16.85f))
    cubicTo(p(19.0f), p(17.45f), p(18.7f), p(18.25f), p(17.85f), p(18.25f))
    lineTo(p(6.15f), p(18.25f))
    cubicTo(p(5.3f), p(18.25f), p(5.0f), p(17.45f), p(5.5f), p(16.85f))
    cubicTo(p(6.8f), p(15.4f), p(7.15f), p(13.5f), p(7.4f), p(10.1f))
    cubicTo(p(7.65f), p(6.6f), p(9.25f), p(4.35f), p(12f), p(4.35f))
    close()
}

// ------------------------------------------------------------- the colophon

/**
 * «صُنع في السعودية» — the origin line, set as a colophon: two hairlines and the
 * words between them, in the brass accent, at label size. Quiet enough to belong
 * at the foot of a screen, deliberate enough to read as part of the identity.
 *
 * This is **not** the «صنع في السعودية» programme mark. That logo is a
 * registered mark of the Saudi Made programme: it may be used only by a
 * registered member company (not an individual), for products enrolled in the
 * programme, and it may not be redrawn, recoloured or combined with other
 * symbols. رَنّة is not enrolled, so the app states its origin in its own words
 * and its own type instead of borrowing an official mark it has no right to.
 * If the programme membership is ever granted, the official asset can replace
 * this composable without touching anything else.
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

/** How loudly a row speaks: waiting for an answer, postponed, or just listed. */
enum class RowTone { Normal, Waiting, Snoozed, Muted }

/**
 * One reminder on the list: what it is, when it rings, and — where it means
 * something — the one useful answer, «تم».
 *
 * [recurring] draws the repeat mark, so the *kind* of reminder is readable at a
 * glance; [repeatLabel] spells the cadence out beside it. A row that is waiting
 * for an answer passes the mark without the words, because on that row the
 * state is worth more line than the cadence.
 *
 * The row is built to be read in one glance and to say which *kind* of reminder
 * it is without a word of explanation: a repeating reminder wears the repeat
 * mark and its cadence («كل يوم»), a one-time reminder shows only its date. The
 * ring and the swipe both mean «تم» and both answer **today's occurrence only**;
 * they are latched so a double activation cannot double-process, and an undo
 * snackbar covers a mis-tap.
 *
 * [onComplete] null means this occurrence cannot be answered yet (a future day,
 * a paused reminder): no ring, no swipe, so the row never offers an affordance
 * that does nothing. [trailing] carries the row's own action instead —
 * «استئناف» on a paused reminder. Nothing destructive is reachable from a row.
 */
@Composable
fun ChecklistRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recurring: Boolean = false,
    repeatLabel: String? = null,
    tone: RowTone = RowTone.Normal,
    onComplete: (() -> Unit)? = null,
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
                .absoluteShift { if (rtl) -offset.value else offset.value }
                .then(dragModifier),
        ) {
            Row(
                Modifier.padding(
                    start = if (onComplete != null) 8.dp else 16.dp,
                    end = if (trailing != null) 8.dp else 16.dp,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (recurring) {
                            Icon(
                                Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Text(
                            text = if (repeatLabel != null) "$repeatLabel · $meta" else meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (tone) {
                                RowTone.Waiting -> MaterialTheme.colorScheme.primary
                                RowTone.Snoozed -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                trailing?.invoke()
            }
        }
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
 * An occurrence that is finished for today — completed, or deliberately skipped.
 *
 * The two outcomes are told apart three ways at once: the mark (a check or a
 * skip arrow), the leading word of [meta] («مكتمل» / «تم تخطيه»), and the fill
 * behind the mark. The row is deliberately quieter than a live one and offers
 * **only** undo: there is no way into the reminder from here, so a checkmark can
 * never become a path to deleting a repeating reminder.
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
                    .semantics { contentDescription = "$undoLabel: $title" },
            ) {
                Text(undoLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Today's secondary action, folded into a menu so the row keeps exactly one
 * visible action: the completion ring. The trigger is the ordinary "more"
 * affordance, not an invented glyph for skipping — the action names itself in
 * full inside the menu, where there is room to say «تخطي اليوم».
 */
@Composable
fun TodayMenu(title: String, onSkip: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.action_more)
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "$menuLabel: $title" },
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_skip_today)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                },
                onClick = {
                    open = false
                    onSkip()
                },
            )
        }
    }
}

/** Shift a node by absolute pixels (the caller supplies the RTL-correct sign). */
private fun Modifier.absoluteShift(x: () -> Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(x().roundToInt(), 0)
        }
    },
)

// ------------------------------------------------------------ empty state

/**
 * The signature empty state: the «رَنّة» bell drawn calm and large, with a
 * message underneath. No stock illustration.
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppMark(
                    body = MaterialTheme.colorScheme.primary,
                    ring = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(50.dp),
                )
            }
        }
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
