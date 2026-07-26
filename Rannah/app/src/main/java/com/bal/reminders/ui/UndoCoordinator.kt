package com.bal.reminders.ui

import androidx.annotation.StringRes
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * One reversible act, waiting to be offered as «تراجع».
 *
 * [subject] is the reminder's title, so the message names what happened rather
 * than announcing that something did.
 */
data class UndoRequest(
    @StringRes val messageRes: Int,
    val subject: String,
    val offeredAt: Instant,
    val action: suspend () -> Unit,
)

/**
 * The single channel for undo offers. Completing sits on the home screen, but
 * deleting happens in the details screen and immediately closes it, so the
 * offer has to outlive the screen that produced it — without ever becoming
 * persisted state. Nothing here touches the database: an undo either runs
 * while the app lives, or the act it reverses simply stands.
 *
 * The home screen takes each request exactly once, so returning to the list
 * later cannot replay an old snackbar, and a request nobody collected in
 * [MAX_AGE] is dropped rather than surfacing out of context.
 */
@Singleton
class UndoCoordinator @Inject constructor(private val clock: Clock) {

    private val _pending = MutableStateFlow<UndoRequest?>(null)

    val pending: StateFlow<UndoRequest?> = _pending.asStateFlow()

    fun offer(@StringRes messageRes: Int, subject: String, action: suspend () -> Unit) {
        _pending.value = UndoRequest(messageRes, subject, clock.instant(), action)
    }

    /** Hands the pending offer to the surface that shows it, leaving nothing behind. */
    fun take(): UndoRequest? = _pending
        .getAndUpdate { null }
        ?.takeIf { Duration.between(it.offeredAt, clock.instant()) <= MAX_AGE }

    private companion object {
        val MAX_AGE: Duration = Duration.ofSeconds(30)
    }
}
