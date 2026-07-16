package com.bal.reminders.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A button you have to mean.
 *
 * Confirming «سجلت البصمة» is the user asserting that a real thing happened
 * out in the world, and رَنّة writes that down as fact. That deserves a
 * fraction of a second of intent, not a reflex tap on a card that appeared
 * under the thumb. Holding fills the button; letting go early does nothing.
 *
 * The fill is the instruction: nobody needs to be told to keep holding when
 * they can watch it happen. The delay is short enough not to feel like a
 * punishment for being decisive.
 *
 * The ceremony is not paid for with accessibility. The control still exposes a
 * plain click action, so a screen reader or switch user confirms with one
 * activation instead of being asked to emulate a long press.
 */
@Composable
fun HoldToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 550,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val fillColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                role = Role.Button
                onClick(label = text) {
                    onConfirm()
                    true
                }
            }
            .pointerInput(text, holdMillis) {
                detectTapGestures(
                    onPress = {
                        val hold = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(holdMillis))
                            // Arriving at the answer is worth feeling.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        }
                        // Released early: no answer, and no trace of one.
                        tryAwaitRelease()
                        hold.cancel()
                        scope.launch { progress.animateTo(0f, tween(120)) }
                    },
                )
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.drawBehind {
                val filled = size.width * progress.value
                if (filled <= 0f) return@drawBehind
                // Grows from the side the text starts on, so in Arabic it
                // fills right to left with the reading.
                val startX = if (layoutDirection == LayoutDirection.Rtl) size.width - filled else 0f
                drawRect(
                    color = fillColor,
                    topLeft = Offset(startX, 0f),
                    size = Size(filled, size.height),
                )
            },
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
