package com.bal.reminders.data

import com.bal.reminders.domain.model.AlertMode
import com.bal.reminders.domain.model.Reminder
import com.bal.reminders.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    private val createdAt = Instant.parse("2026-07-16T08:00:00Z")
    private val time = LocalTime.of(9, 35)

    @Test
    fun `every calendar-bearing schedule survives database round trip`() {
        val schedules = listOf(
            Schedule.Once(LocalDate.of(2026, 8, 31), time),
            Schedule.OnceHijri(1448, 9, 30, time),
            Schedule.Monthly(31, time),
            Schedule.HijriMonthly(30, time),
            Schedule.Yearly(2, 29, time),
            Schedule.HijriYearly(12, 30, time),
        )

        schedules.forEach { schedule ->
            val restored = Reminder(
                id = 42,
                title = "موعد",
                schedule = schedule,
                alertMode = AlertMode.ALARM,
                ringtoneUri = "content://ringtone/7",
                alarmTimeoutMinutes = 8,
                alarmGradualVolume = false,
                alarmRepeatIfIgnored = true,
                createdAt = createdAt,
            ).toEntity().toDomain()

            assertEquals("calendar semantics changed for $schedule", schedule, restored.schedule)
            assertEquals(AlertMode.ALARM, restored.alertMode)
            assertEquals("content://ringtone/7", restored.ringtoneUri)
            assertEquals(8, restored.alarmTimeoutMinutes)
            assertEquals(false, restored.alarmGradualVolume)
            assertEquals(true, restored.alarmRepeatIfIgnored)
        }
    }
}
