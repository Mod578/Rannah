package com.bal.reminders.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Diversity1
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.time.Instant
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.Category
import com.bal.reminders.domain.model.Priority
import com.bal.reminders.domain.model.Reminder

// ------------------------------------------------------------- categories

val Category.icon: ImageVector
    get() = when (this) {
        Category.WORK -> Icons.Rounded.Work
        Category.HEALTH -> Icons.Rounded.Favorite
        Category.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
        Category.STUDY -> Icons.Rounded.School
        Category.FAMILY -> Icons.Rounded.Diversity1
        Category.PERSONAL -> Icons.Rounded.Person
    }

val Category.labelRes: Int
    get() = when (this) {
        Category.WORK -> R.string.category_work
        Category.HEALTH -> R.string.category_health
        Category.BILLS -> R.string.category_bills
        Category.STUDY -> R.string.category_study
        Category.FAMILY -> R.string.category_family
        Category.PERSONAL -> R.string.category_personal
    }

// ------------------------------------------------------------------ cards

/**
 * The standard reminder row. [subtitle] is the schedule/relative-time line,
 * already formatted by the caller.
 */
@Composable
fun ReminderCard(
    reminder: Reminder,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    subtitleEmphasis: Boolean = false,
    awaiting: Boolean = false,
    now: Instant = Instant.now(),
) {
    val disabled = !reminder.enabled
    val state = reminderStateOf(reminder, now, awaiting)
    val stateWords = reminderStateLabel(reminder, state)
    val toggleLabel = stringResource(R.string.reminder_toggle_description)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryBadge(reminder.category, muted = disabled)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (disabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null,
                    )
                    if (reminder.alertMode == AlertMode.ALARM) {
                        Icon(
                            Icons.Rounded.Alarm,
                            contentDescription = stringResource(R.string.alert_mode_alarm),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    // A reminder that follows until completed behaves
                    // differently, so it says so instead of hiding behind a dot.
                    if (reminder.followUntilComplete) {
                        Icon(
                            Icons.Rounded.TaskAlt,
                            contentDescription = stringResource(R.string.reminder_state_follow),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        disabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        subtitleEmphasis -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                // The state in words. Silent for a plain active reminder,
                // where "مفعّل" would just be noise on every row.
                if (state != ReminderState.ACTIVE) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        text = reminderStateLabel(reminder, state),
                        color = reminderStateColor(state),
                    )
                }
            }
            if (onComplete != null) {
                // «تم» بكلمة واضحة وهدف لمس كبير، لا أيقونة فقط.
                FilledTonalButton(
                    onClick = onComplete,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_complete))
                }
            }
            if (onToggle != null) {
                // The visible status pill is this switch's label; the semantics
                // carry the same words so a screen reader is not left with a
                // nameless toggle.
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = toggleLabel
                        stateDescription = stateWords
                    },
                )
            }
        }
    }
}

@Composable
fun CategoryBadge(category: Category, muted: Boolean = false, size: Int = 40) {
    Surface(
        shape = CircleShape,
        color = if (muted) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = category.icon,
                contentDescription = stringResource(category.labelRes),
                tint = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.size((size * 0.5).dp),
            )
        }
    }
}

// ------------------------------------------------------------ empty state

/**
 * Signature empty state: the «رَنّة» bell mark drawn calm and large,
 * with a message underneath. No stock illustrations.
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
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppMark(
            stroke = MaterialTheme.colorScheme.outlineVariant,
            dot = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(96.dp)
                .padding(bottom = 8.dp),
        )
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

/**
 * A section rule carrying the Najdi stepped rhythm.
 *
 * A plain hairline says "these are different"; this says the same thing in the
 * product's own accent. It is drawn once per group heading and nowhere else —
 * the identity is a rhythm you notice at the edges, not a pattern tiled over
 * every surface. Purely decorative, so it is hidden from screen readers.
 */
@Composable
fun NajdiRule(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clearAndSetSemantics {},
    ) {
        val step = 10.dp.toPx()
        val rise = 4.dp.toPx()
        val baseline = size.height
        val path = Path().apply {
            moveTo(0f, baseline)
            var x = 0f
            var up = true
            while (x < size.width) {
                val next = (x + step).coerceAtMost(size.width)
                lineTo(x, if (up) baseline - rise else baseline)
                lineTo(next, if (up) baseline - rise else baseline)
                x = next
                up = !up
            }
        }
        drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * The «رَنّة» mark.
 *
 * Three elements and nothing else:
 *
 * - **A stepped Najdi parapet (شُرفة).** The mark's rhythm comes from the
 *   crenellated skyline of Najdi mud-brick walls, not from a bell. Read
 *   upwards, the ascending steps also read as a signal rising — sound, without
 *   drawing a speaker.
 * - **The saffron point, held in the wall's opening.** Najdi walls are punched
 *   with small openings; this one holds the single coloured element in the mark.
 *   It is the completion رَنّة is always asking about — the app's whole question
 *   («هل تم؟») in one dot.
 *
 * A third element — the «ر» sweeping beneath as a letterform — was drawn,
 * rendered, and cut. On a device the curve plus the dot read unmistakably as a
 * smiling face, which is not a thing an alarm should do at 5am. Legibility beat
 * the cleverness; the Arabic identity is carried by the language everywhere
 * else, and does not need to be smuggled into a 24dp glyph.
 *
 * What it deliberately is not: a bell (a ringtone app), a clock face (a clock
 * clone), or a flag, sword, palm or map. The Saudi-ness is in the geometry's
 * rhythm, which is felt rather than decoded.
 */
@Composable
fun AppMark(
    stroke: Color,
    dot: Color,
    modifier: Modifier = Modifier.size(96.dp),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(v: Float) = v / 24f * w
        fun y(v: Float) = v / 24f * h

        // Merlons: the crenellation of a Najdi parapet, seen head-on. Solid
        // shapes, not an outline — an outline at 24dp turns to mush, and a
        // closed outline turns into a camera.
        val merlonW = 4.2f
        val gap = 2.4f
        val top = 6.5f
        val baseTop = 15.5f
        val baseBottom = 19.5f
        val startX = 12f - (merlonW * 3 + gap * 2) / 2f

        repeat(3) { i ->
            val left = startX + i * (merlonW + gap)
            // The middle merlon is the point: the one occurrence that is due now,
            // standing in the same rhythm as all the others. The mark carries the
            // product's whole question without drawing a bell to ask it.
            val fill = if (i == 1) dot else stroke
            drawPath(
                Path().apply {
                    moveTo(x(left), y(baseTop))
                    lineTo(x(left), y(top))
                    lineTo(x(left + merlonW), y(top))
                    lineTo(x(left + merlonW), y(baseTop))
                    close()
                },
                color = fill,
            )
        }
        // The wall the rhythm stands on.
        drawPath(
            Path().apply {
                moveTo(x(3f), y(baseTop))
                lineTo(x(21f), y(baseTop))
                lineTo(x(21f), y(baseBottom))
                lineTo(x(3f), y(baseBottom))
                close()
            },
            color = stroke,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
