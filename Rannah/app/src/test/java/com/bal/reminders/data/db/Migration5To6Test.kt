package com.bal.reminders.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v6 changes no table. It exists to normalise data that older builds could write
 * and 1.1 no longer can, so a database carried forward from a development
 * install reads the same way a fresh one does.
 */
class Migration5To6Test {

    private companion object {
        const val CREATED = 1_700_000_000_000L
        const val OCCURRENCE_A = 1_757_000_000_000L
        const val OCCURRENCE_B = 1_757_086_400_000L
        const val OCCURRENCE_C = 1_757_172_800_000L
    }

    private fun seed(db: java.sql.Connection) {
        fun reminder(id: Long, type: String, snooze: Int) = db.exec(
            "INSERT INTO reminders (id, title, notes, categoryId, priority, recurrenceType, " +
                "calendar, timeMinutes, date, year, month, daysOfWeek, dayOfMonth, enabled, " +
                "alertMode, soundEnabled, vibrationEnabled, snoozeMinutes, ringtoneUri, " +
                "alarmTimeoutMinutes, alarmGradualVolume, alarmRepeatIfIgnored, " +
                "followUntilComplete, followUpIntervalMinutes, followUpMaxRepeats, " +
                "completionLabel, snoozedUntilMillis, snoozedOccurrenceAtMillis, " +
                "nextTriggerAtMillis, createdAtMillis, completedAtMillis) " +
                "VALUES ($id, 'تذكير $id', NULL, 'personal', 0, '$type', 'gregorian', 540, " +
                "NULL, NULL, NULL, 0, NULL, 1, 'alarm', 1, 1, $snooze, NULL, 3, 1, 0, 0, 5, 3, " +
                "NULL, NULL, NULL, NULL, $CREATED, NULL)",
        )
        fun record(id: Long, reminderId: Long, occurrence: Long, status: String) = db.exec(
            "INSERT INTO completions (id, reminderId, reminderTitle, categoryId, " +
                "occurrenceAtMillis, completedAtMillis, status) " +
                "VALUES ($id, $reminderId, 'سجل', 'personal', $occurrence, $occurrence, '$status')",
        )

        reminder(1, "daily", snooze = 45)
        reminder(2, "once", snooze = 5)

        // The contradiction older builds could race into: one occurrence holding
        // both answers. The unique index allowed it — it is per status.
        record(1, 1, OCCURRENCE_A, "completed")
        record(2, 1, OCCURRENCE_A, "skipped")
        // A ring that was missed and later answered: two claims about one moment.
        record(3, 1, OCCURRENCE_B, "missed")
        record(4, 1, OCCURRENCE_B, "completed")
        // An honest, untouched pair that must survive intact.
        record(5, 1, OCCURRENCE_C, "missed")
        record(6, 2, OCCURRENCE_A, "skipped")
    }

    @Test
    fun `an occurrence keeps one answer, and completion wins`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            val answers = db.select(
                "SELECT status FROM completions WHERE reminderId = 1 " +
                    "AND occurrenceAtMillis = $OCCURRENCE_A",
            ) { it.getString(1) }
            assertEquals(listOf("completed"), answers)
        }
    }

    @Test
    fun `a missed note is dropped once the occurrence has a real answer`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            val forB = db.select(
                "SELECT status FROM completions WHERE reminderId = 1 " +
                    "AND occurrenceAtMillis = $OCCURRENCE_B",
            ) { it.getString(1) }
            assertEquals(listOf("completed"), forB)
        }
    }

    @Test
    fun `an unanswered missed note survives`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            assertEquals(
                1L,
                db.count(
                    "SELECT COUNT(*) FROM completions WHERE reminderId = 1 " +
                        "AND occurrenceAtMillis = $OCCURRENCE_C AND status = 'missed'",
                ),
            )
        }
    }

    @Test
    fun `another reminder's records are untouched`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            assertEquals(1L, db.count("SELECT COUNT(*) FROM completions WHERE reminderId = 2"))
        }
    }

    @Test
    fun `per-reminder snooze values are normalised to the one default`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            val values = db.select("SELECT snoozeMinutes FROM reminders ORDER BY id") { it.getInt(1) }
            assertEquals(
                "how long «تأجيل» lasts is one setting now; no row may carry a stale answer",
                listOf(10, 10),
                values,
            )
        }
    }

    @Test
    fun `no reminder is lost and no table is changed`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            assertEquals(listOf(1L, 2L), db.select("SELECT id FROM reminders ORDER BY id") { it.getLong(1) })
            assertEquals(listOf("completions", "reminders"), db.tableNames())
        }
    }

    @Test
    fun `the result is exactly the exported v6 schema`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())

            Schemas.openAt(6).use { fresh ->
                assertEquals(fresh.describeSchema(), db.describeSchema())
            }
        }
    }

    @Test
    fun `re-running the migration changes nothing further`() {
        Schemas.openAt(5).use { db ->
            seed(db)

            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())
            val once = db.select("SELECT id, status FROM completions ORDER BY id") { "${it.getLong(1)}:${it.getString(2)}" }
            BalDatabase.MIGRATION_5_6.migrate(db.asSupportDatabase())
            val twice = db.select("SELECT id, status FROM completions ORDER BY id") { "${it.getLong(1)}:${it.getString(2)}" }

            assertTrue(once.isNotEmpty())
            assertEquals(once, twice)
        }
    }
}
