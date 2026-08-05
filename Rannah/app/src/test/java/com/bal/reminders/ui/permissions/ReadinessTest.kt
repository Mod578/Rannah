package com.bal.reminders.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Readiness reports only what is actionable, worst first, and every blocking
 * issue it reports must have somewhere to go.
 *
 * `ALARM_CHANNEL` used to fail that second rule: the summary named it in red,
 * the home banner linked to the permissions screen, and that screen had no card
 * for it. The app told the user their alarms were silent and then offered
 * nothing that could fix it.
 */
class ReadinessTest {

    private fun status(
        notifications: Boolean = true,
        exact: Boolean = true,
        battery: Boolean = true,
        fullScreen: Boolean = true,
        volumeMuted: Boolean = false,
        channelBlocked: Boolean = false,
    ) = PermissionsStatus(
        notificationsGranted = notifications,
        exactAlarmsGranted = exact,
        batteryUnrestricted = battery,
        fullScreenAlarmGranted = fullScreen,
        alarmVolumeMuted = volumeMuted,
        alarmChannelBlocked = channelBlocked,
    )

    @Test
    fun `a healthy device reports nothing`() {
        assertTrue(status().issues().isEmpty())
    }

    @Test
    fun `a blocked alarm channel is reported and is blocking`() {
        val issues = status(channelBlocked = true).issues()

        assertEquals(listOf(ReadinessIssue.ALARM_CHANNEL), issues)
        assertTrue(issues.single().blocking)
    }

    @Test
    fun `issues arrive worst first`() {
        val issues = status(
            notifications = false,
            exact = false,
            channelBlocked = true,
            fullScreen = false,
            volumeMuted = true,
            battery = false,
        ).issues()

        assertEquals(
            listOf(
                ReadinessIssue.NOTIFICATIONS,
                ReadinessIssue.EXACT_ALARMS,
                ReadinessIssue.ALARM_CHANNEL,
                ReadinessIssue.FULL_SCREEN,
                ReadinessIssue.ALARM_VOLUME,
                ReadinessIssue.BATTERY,
            ),
            issues,
        )
        // The banner shows the worst blocking one; it must be the first.
        assertEquals(ReadinessIssue.NOTIFICATIONS, issues.first { it.blocking })
    }

    @Test
    fun `optional issues never block`() {
        assertFalse(ReadinessIssue.FULL_SCREEN.blocking)
        assertFalse(ReadinessIssue.ALARM_VOLUME.blocking)
        assertFalse(ReadinessIssue.BATTERY.blocking)
    }

    @Test
    fun `essentials are notifications and exact alarms`() {
        assertFalse(status(notifications = false).essentialsGranted)
        assertFalse(status(exact = false).essentialsGranted)
        // A muted channel is blocking for the user, but it is not a permission
        // grant: the settings row must not claim everything is broken over it.
        assertTrue(status(channelBlocked = true).essentialsGranted)
    }
}
