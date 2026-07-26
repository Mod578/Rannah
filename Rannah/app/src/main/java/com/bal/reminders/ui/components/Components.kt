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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.bal.reminders.ui.theme.BrandBell
import com.bal.reminders.ui.theme.BrandInk
import com.bal.reminders.ui.theme.BrandRing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ------------------------------------------------------------- the bell mark

/**
 * The «رَنّة» mark: a bell caught mid-swing, its clapper hanging free, two arcs
 * of sound coming off its right shoulder.
 *
 * The geometry is the product's logo, kept as one canonical pair of paths on a
 * 0..24 grid — [PATH_BELL] (body and clapper) and [PATH_RING] (the two arcs) —
 * and worn unchanged by every surface: the launcher tile, the splash, the
 * status-bar glyph and every place the mark appears inside the app. The three
 * XML vectors carry the same two strings verbatim, because XML cannot import
 * Kotlin; nothing else redraws the bell.
 *
 * Two colours, split by meaning: the bell is one solid [body], and the sound —
 * the arcs — is the brand [ring] red. The notch under the arcs is carved out of
 * the bell itself, so the gap stays true whatever colour sits behind it, right
 * down to a one-colour notification mask.
 */
@Composable
fun AppMark(
    body: Color,
    ring: Color,
    modifier: Modifier = Modifier,
) {
    val bellPath = remember { PathParser().parsePathString(PATH_BELL).toPath() }
    val ringPath = remember { PathParser().parsePathString(PATH_RING).toPath() }
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(bellPath, body)
            drawPath(ringPath, ring)
        }
    }
}

/**
 * The app icon itself: the mark on its ink tile, in the brand's own colours.
 * Used where رَنّة introduces itself — the welcome screen and «عن رَنّة» — so the
 * thing on the home screen and the thing in the app are visibly one object.
 */
@Composable
fun AppIconTile(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 23),
        color = BrandInk,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The fraction, not a fixed inset: the tile keeps the logo's own
            // proportions at 96 dp and at 112 dp alike.
            AppMark(
                body = BrandBell,
                ring = BrandRing,
                modifier = Modifier.fillMaxSize(MARK_FRACTION),
            )
        }
    }
}

/** How much of the tile the mark fills — measured from the logo artwork. */
private const val MARK_FRACTION = 0.74f

/** The bell body and its clapper, on a 0..24 grid. */
private const val PATH_BELL =
    "M10.62,2.08 C9.99,2.18 9.4,2.69 9.27,3.23 C9.23,3.37 9.23,3.37 8.94,3.47 C8.19,3.72 7.56,4.12 6.94,4.74 " +
        "C6.46,5.21 6.15,5.63 5.9,6.13 C5.42,7.05 5.26,7.73 5.17,9.38 C4.95,13.04 4.6,14.29 3.24,15.99 " +
        "C2.64,16.76 2.41,17.16 2.31,17.61 C2.2,18.17 2.49,18.57 3.09,18.69 C3.24,18.71 5.4,18.72 11.25,18.71 " +
        "C18.8,18.7 19.21,18.7 19.35,18.64 C19.95,18.4 20.1,17.87 19.77,17.15 C19.66,16.91 19.32,16.41 19,16.01 " +
        "C17.74,14.43 17.29,13.06 17.12,10.21 C17.09,9.72 17.06,9.25 17.05,9.15 L17.04,8.97 L16.82,9.13 " +
        "C16.25,9.54 15.61,9.64 14.94,9.43 C13.63,9.01 13.06,7.44 13.8,6.3 C14.08,5.86 14.62,5.48 15.08,5.38 " +
        "C15.18,5.36 15.27,5.33 15.27,5.32 C15.27,5.27 14.79,4.73 14.53,4.48 C14.04,4.02 13.47,3.68 12.85,3.47 L12.56,3.37 L12.51,3.2 " +
        "C12.27,2.44 11.43,1.94 10.62,2.08 Z M9.76,19.31 C9.44,19.67 9.38,20.42 9.62,20.91 " +
        "C10.41,22.5 12.85,21.87 12.74,20.1 C12.73,19.82 12.6,19.47 12.46,19.32 C12.38,19.22 9.85,19.22 9.76,19.31 " +
        "Z"

/** The two arcs of sound, on the same grid. */
private const val PATH_RING =
    "M16.05,1.54 C15.87,1.61 15.75,1.85 15.8,2.06 C15.85,2.27 15.94,2.33 16.37,2.44 C16.9,2.57 17.13,2.66 17.55,2.87 " +
        "C19.97,4.06 21.28,6.67 20.72,9.21 C20.62,9.68 20.61,9.75 20.67,9.88 C20.8,10.13 21.16,10.19 21.36,10 " +
        "C21.61,9.76 21.8,8.24 21.7,7.33 C21.37,4.51 19.21,2.13 16.44,1.56 C16.17,1.5 16.15,1.5 16.05,1.54 " +
        "Z M15.51,3.48 C15.36,3.57 15.28,3.79 15.33,3.97 C15.38,4.17 15.48,4.24 15.82,4.34 C17.75,4.9 18.95,6.82 18.63,8.84 " +
        "C18.58,9.15 18.58,9.18 18.63,9.28 C18.78,9.59 19.25,9.63 19.4,9.35 C19.56,9.03 19.6,7.76 19.47,7.17 " +
        "C19.08,5.44 17.91,4.11 16.27,3.55 C15.86,3.41 15.65,3.39 15.51,3.48 Z"

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
                    ring = BrandRing,
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
