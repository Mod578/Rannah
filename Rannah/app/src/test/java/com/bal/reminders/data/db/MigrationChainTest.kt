package com.bal.reminders.data.db

import java.sql.Connection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The oldest database رَنّة can meet. A user who installed the first build and
 * never updated until today walks the whole ladder in one go, so the four
 * migrations are tested as a sequence and not only one at a time.
 *
 * This also pins the rule that keeps the ladder honest: there is a migration
 * for every step between every exported schema, and therefore no version pair
 * that could ever fall through to a destructive rebuild.
 */
class MigrationChainTest {

    private companion object {
        const val CREATED = 1_700_000_000_000L
        const val ENDED_AT = 1_755_000_000_000L
        const val DONE_AT = 1_756_000_000_000L
        const val FIRST_OCCURRENCE = 1_757_000_000_000L
        const val SECOND_OCCURRENCE = 1_757_086_400_000L
    }

    private val migrations = listOf(
        BalDatabase.MIGRATION_1_2,
        BalDatabase.MIGRATION_2_3,
        BalDatabase.MIGRATION_3_4,
        BalDatabase.MIGRATION_4_5,
    )

    @Test
    fun `every step between exported schemas has a migration`() {
        val exported = Schemas.exportedVersions()

        assertEquals("schemas must be exported without gaps", (1..exported.size).toList(), exported)
        assertEquals(
            "a missing step would leave Room with no way forward but to destroy data",
            exported.zipWithNext(),
            migrations.map { it.startVersion to it.endVersion },
        )
    }

    @Test
    fun `a version one database reaches version five with its data intact`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            // Identity: nothing gained, nothing lost, nothing renumbered.
            assertEquals(
                (1L..4L).toList(),
                db.select("SELECT id FROM reminders ORDER BY id") { it.getLong(1) },
            )
            assertEquals(
                listOf("صيام الأيام البيض", "تمرين الصباح", "تجديد الرخصة", "قيام الليل"),
                db.select("SELECT title FROM reminders ORDER BY id") { it.getString(1) },
            )
        }
    }

    @Test
    fun `legacy hijri reminders become monthly reminders in the hijri calendar`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            val hijri = db.select(
                "SELECT id, recurrenceType, calendar, dayOfMonth FROM reminders " +
                    "WHERE calendar = 'hijri' ORDER BY id",
            ) { "${it.getLong(1)}:${it.getString(2)}:${it.getString(3)}:${it.getInt(4)}" }

            assertEquals(listOf("1:monthly:hijri:13", "4:monthly:hijri:1"), hijri)
            assertEquals(
                "no reminder may keep the retired recurrence name",
                0L,
                db.count("SELECT COUNT(*) FROM reminders WHERE recurrenceType = 'hijri_monthly'"),
            )
        }
    }

    @Test
    fun `stopping a series is normalised no matter which version wrote it`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            // 2 and 4 were recurring reminders holding a completion; both are
            // now plainly paused and resumable.
            assertEquals(
                listOf("2:0:null", "4:0:null"),
                db.select(
                    "SELECT id, enabled, completedAtMillis FROM reminders " +
                        "WHERE id IN (2, 4) ORDER BY id",
                ) { "${it.getLong(1)}:${it.getInt(2)}:${it.getObject(3)}" },
            )
            // 3 was a one-time errand that really was finished.
            assertEquals(
                listOf("3:1:$DONE_AT"),
                db.select(
                    "SELECT id, enabled, completedAtMillis FROM reminders WHERE id = 3",
                ) { "${it.getLong(1)}:${it.getInt(2)}:${it.getObject(3)}" },
            )
        }
    }

    @Test
    fun `duplicate occurrence records are collapsed to the first one recorded`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            // Records 1 and 2 described the same occurrence; the unique index
            // v2 introduced needs one of them gone, and it must be the later.
            assertEquals(
                listOf(1L, 3L, 4L),
                db.select("SELECT id FROM completions ORDER BY id") { it.getLong(1) },
            )
            assertEquals(
                "the orphaned record belongs to no reminder and is still not the migration's to delete",
                1L,
                db.count("SELECT COUNT(*) FROM completions WHERE reminderId = 999"),
            )
        }
    }

    @Test
    fun `the retired stop-marks-completed column is gone and nothing else is`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            val columns = db.select("PRAGMA table_info(`reminders`)") { it.getString("name") }
            assertFalse(columns.contains("stopMarksCompleted"))
            assertTrue(columns.contains("snoozedOccurrenceAtMillis"))
            assertEquals(listOf("completions", "reminders"), db.tableNames())
        }
    }

    @Test
    fun `the whole chain lands on exactly the exported v5 schema`() {
        Schemas.openAt(1).use { db ->
            db.seedVersionOneData()

            migrations.forEach { it.migrate(db.asSupportDatabase()) }

            Schemas.openAt(5).use { fresh ->
                assertEquals(fresh.describeSchema(), db.describeSchema())
            }
        }
    }

    /** A first-build database: no calendar column, no statuses, no alert mode. */
    private fun Connection.seedVersionOneData() {
        fun reminder(
            id: Long,
            title: String,
            recurrenceType: String,
            dayOfMonth: Int?,
            enabled: Int = 1,
            completedAtMillis: Long? = null,
        ) = prepareStatement(
            "INSERT INTO reminders (id, title, notes, categoryId, priority, recurrenceType, " +
                "timeMinutes, date, daysOfWeek, dayOfMonth, enabled, soundEnabled, " +
                "vibrationEnabled, snoozeMinutes, snoozedUntilMillis, nextTriggerAtMillis, " +
                "createdAtMillis, completedAtMillis) " +
                "VALUES (?, ?, NULL, 'general', 1, ?, 480, NULL, 0, ?, ?, 1, 1, 10, NULL, NULL, ?, ?)",
        ).use {
            it.setObject(1, id)
            it.setObject(2, title)
            it.setObject(3, recurrenceType)
            it.setObject(4, dayOfMonth)
            it.setObject(5, enabled)
            it.setObject(6, CREATED)
            it.setObject(7, completedAtMillis)
            it.executeUpdate()
        }

        reminder(1, "صيام الأيام البيض", "hijri_monthly", dayOfMonth = 13)
        reminder(2, "تمرين الصباح", "daily", dayOfMonth = null, completedAtMillis = ENDED_AT)
        reminder(3, "تجديد الرخصة", "once", dayOfMonth = null, completedAtMillis = DONE_AT)
        reminder(4, "قيام الليل", "hijri_monthly", dayOfMonth = 1, completedAtMillis = ENDED_AT)

        fun record(id: Long, reminderId: Long, occurrenceAtMillis: Long) = exec(
            "INSERT INTO completions (id, reminderId, reminderTitle, categoryId, " +
                "occurrenceAtMillis, completedAtMillis) " +
                "VALUES ($id, $reminderId, 'سجل', 'general', $occurrenceAtMillis, $occurrenceAtMillis)",
        )

        record(1, reminderId = 1, occurrenceAtMillis = FIRST_OCCURRENCE)
        // v1 had no unique index, so the same occurrence could be logged twice.
        record(2, reminderId = 1, occurrenceAtMillis = FIRST_OCCURRENCE)
        record(3, reminderId = 1, occurrenceAtMillis = SECOND_OCCURRENCE)
        record(4, reminderId = 999, occurrenceAtMillis = FIRST_OCCURRENCE)
    }
}
