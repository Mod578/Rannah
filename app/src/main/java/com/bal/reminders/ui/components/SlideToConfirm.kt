package com.bal.reminders.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Slide to confirm «تم الإنجاز» on the alarm screen.
 *
 * The alarm screen is the one place رَنّة is read by someone half-awake, in the
 * dark, with a sound going. A tap there is not evidence of intent — it is
 * evidence of wanting the noise to stop. So the act that writes down "I did the
 * thing in the real world" asks for a movement nobody performs by accident, and
 * the movement is labelled with what is actually being claimed («سجلت البصمة»),
 * not an abstract «تم».
 *
 * Deliberately not a lock-screen imitation: the track is a labelled button that
 * fills as it travels, not a bare rail with a chevron. The user is confirming a
 * fact, not opening their phone, and the two should not feel alike.
 *
 * Direction is handled by the layout system, not by arithmetic here:
 * [Alignment.CenterStart] already resolves to the right edge in RTL, and
 * `placeRelative` already mirrors its x, so a positive offset always means
 * "onward through the text". Only the raw drag delta, which arrives in absolute
 * screen pixels, needs its sign flipped for RTL.
 *
 * Accessibility is not paid for with ceremony. The control exposes one ordinary
 * click action, so TalkBack and switch access confirm with a single activation
 * instead of being asked to emulate a drag. The label carries the whole meaning,
 * so it stays understandable with animation off and without relying on colour.
 */
@Composable
fun SlideToConfirm(
    text: String,
    hint: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    val trackHeight = 76.dp
    val thumbSize = 64.dp
    val inset = 6.dp

    // Latched at the threshold: a drag that keeps going, a second drag, or a
    // recomposition cannot fire the confirmation twice.
    val confirmed = remember(text) { mutableStateOf(false) }
    val offsetPx = remember(text) { Animatable(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val travelPx = with(density) {
                (maxWidth - thumbSize - inset * 2).toPx()
            }.coerceAtLeast(1f)
            val progress = (offsetPx.value / travelPx).coerceIn(0f, 1f)

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .semantics(mergeDescendants = true) {
                        contentDescription = text
                        role = Role.Button
                        onClick(label = text) {
                            if (!confirmed.value) {
                                confirmed.value = true
                                onConfirm()
                            }
                            true
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // The fill is feedback, never the message: the label says the
                    // same thing whether or not anything animates.
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.large,
                        content = {},
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(progress.coerceAtLeast(0.0001f))
                            .height(trackHeight),
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = thumbSize),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = inset)
                            .forwardOffset(offsetPx.value)
                            .size(thumbSize)
                            .pointerInput(text, travelPx) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        // Released short of the threshold: nothing
                                        // was claimed, and the thumb returns home.
                                        if (!confirmed.value) {
                                            scope.launch { offsetPx.animateTo(0f, tween(180)) }
                                        }
                                    },
                                    onDragCancel = {
                                        if (!confirmed.value) {
                                            scope.launch { offsetPx.animateTo(0f, tween(180)) }
                                        }
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (confirmed.value) return@detectHorizontalDragGestures
                                    val forward = if (rtl) -dragAmount else dragAmount
                                    val next = (offsetPx.value + forward).coerceIn(0f, travelPx)
                                    scope.launch { offsetPx.snapTo(next) }
                                    if (next >= travelPx) {
                                        confirmed.value = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onConfirm()
                                    }
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** Offsets by raw pixels along the reading direction; `placeRelative` mirrors for RTL. */
private fun Modifier.forwardOffset(x: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(x.roundToInt(), 0)
            }
        },
    )
