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
) {
    val disabled = !reminder.enabled
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
                    if (reminder.priority == Priority.HIGH) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics {
                                    contentDescription = ""
                                },
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
                Switch(checked = reminder.enabled, onCheckedChange = onToggle)
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
 * Signature empty state: the «رنّه» bell mark drawn calm and large,
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

/** The «رنّه» mark: a ringing bell whose clapper is the accent dot. */
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

        val bell = Path().apply {
            moveTo(x(12f), y(3.4f))
            cubicTo(x(8.8f), y(3.4f), x(6.9f), y(5.8f), x(6.9f), y(9.1f))
            lineTo(x(6.9f), y(11.2f))
            cubicTo(x(6.9f), y(13.1f), x(6.1f), y(14.5f), x(4.9f), y(15.7f))
            cubicTo(x(4.4f), y(16.2f), x(4.7f), y(17f), x(5.4f), y(17f))
            lineTo(x(18.6f), y(17f))
            cubicTo(x(19.3f), y(17f), x(19.6f), y(16.2f), x(19.1f), y(15.7f))
            cubicTo(x(17.9f), y(14.5f), x(17.1f), y(13.1f), x(17.1f), y(11.2f))
            lineTo(x(17.1f), y(9.1f))
            cubicTo(x(17.1f), y(5.8f), x(15.2f), y(3.4f), x(12f), y(3.4f))
            close()
        }
        val waves = Path().apply {
            moveTo(x(3.6f), y(8.8f))
            cubicTo(x(3.6f), y(6.8f), x(4.4f), y(5.1f), x(5.8f), y(3.8f))
            moveTo(x(20.4f), y(8.8f))
            cubicTo(x(20.4f), y(6.8f), x(19.6f), y(5.1f), x(18.2f), y(3.8f))
        }
        drawPath(
            bell,
            color = stroke,
            style = Stroke(width = x(1.9f), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            waves,
            color = stroke,
            style = Stroke(width = x(1.6f), cap = StrokeCap.Round),
        )
        drawCircle(
            color = dot,
            radius = x(1.9f),
            center = Offset(x(12f), y(20.3f)),
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
