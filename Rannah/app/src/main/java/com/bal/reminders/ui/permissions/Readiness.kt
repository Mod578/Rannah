package com.bal.reminders.ui.permissions

import com.bal.reminders.R

/**
 * What is actually stopping رَنّة from alerting on time, worst first.
 *
 * Only real problems appear. A checklist of green ticks teaches people to
 * ignore the list, and then the one red line that matters gets ignored too.
 *
 * The wording deliberately avoids Android's vocabulary: a person who cannot
 * hear their alarm does not need the phrase "full-screen intent", they need to
 * know their clock-in reminder will not wake the screen.
 */
enum class ReadinessIssue(val titleRes: Int, val bodyRes: Int, val blocking: Boolean) {
    /** Nothing can be shown at all: the one that breaks the whole product. */
    NOTIFICATIONS(
        R.string.readiness_notifications_title,
        R.string.readiness_notifications_body,
        blocking = true,
    ),

    /** Reminders still arrive, but late, which for a time-critical reminder is failure. */
    EXACT_ALARMS(
        R.string.readiness_exact_title,
        R.string.readiness_exact_body,
        blocking = true,
    ),

    /** Notifications allowed, alarm channel muted: rings silently and looks broken. */
    ALARM_CHANNEL(
        R.string.readiness_alarm_channel_title,
        R.string.readiness_alarm_channel_body,
        blocking = true,
    ),

    FULL_SCREEN(
        R.string.readiness_fullscreen_title,
        R.string.readiness_fullscreen_body,
        blocking = false,
    ),

    ALARM_VOLUME(
        R.string.readiness_volume_title,
        R.string.readiness_volume_body,
        blocking = false,
    ),

    BATTERY(
        R.string.readiness_battery_title,
        R.string.readiness_battery_body,
        blocking = false,
    ),
}

/** The issues worth telling the user about, most severe first. */
fun PermissionsStatus.issues(): List<ReadinessIssue> = buildList {
    if (!notificationsGranted) add(ReadinessIssue.NOTIFICATIONS)
    if (!exactAlarmsGranted) add(ReadinessIssue.EXACT_ALARMS)
    if (alarmChannelBlocked) add(ReadinessIssue.ALARM_CHANNEL)
    if (!fullScreenAlarmGranted) add(ReadinessIssue.FULL_SCREEN)
    if (alarmVolumeMuted) add(ReadinessIssue.ALARM_VOLUME)
    if (!batteryUnrestricted) add(ReadinessIssue.BATTERY)
}

val PermissionsStatus.ready: Boolean get() = issues().isEmpty()
