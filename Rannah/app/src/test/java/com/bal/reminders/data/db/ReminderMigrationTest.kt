package com.bal.reminders.data.db

import java.sql.Connection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a رَنّة user's database looks like on the day they update, and what must
 * still be true the moment after.
 *
 * The v4 fixture is deliberately awkward: it holds every legacy shape v5 was
 * written to normalise, plus the shapes it must leave completely alone, plus a
 * record whose reminder no longer exists. If the migration is too eager it
 * loses a completed appointment or an orphaned record; if it is too timid a
 * stopped series stays unreachable. Both failures are asserted against here.
 */
class ReminderMigrationTest {

    private lateinit var db: Connection

    /** Instants chosen only to be distinguishable in a failure message. */
    private companion object {
        const val CREATED = 1_760_000_000_000L
        const val ENDED_AT = 1_762_000_000_000L
        const val APPOINTMENT_DONE_AT = 1_763_000_000_000L
        const val BILL_ENDED_AT = 1_764_000_000_000L
        const val OCCURRENCE = 1_765_000_000_000L
        const val SNOOZED_UNTIL = 1_765_000_600_000L
        const val NEXT_TRIGGER = 1_766_000_000_000L
    }

    @Before
    fun openLegacyDatabase() {
        db = Schemas.openAt(4)
        seedRepresentativeV4Data()
    }

    @After
    fun close() = db.close()

    // ── The legacy fixture ─────────────────────────────────────────────────

    private fun seedRepresentativeV4Data() {
        // 1 — an ordinary active daily reminder: the control.
        insertReminder(id = 1, title = "دواء الضغط", recurrenceType = "daily", timeMinutes = 480)
        // 2 — a recurring series stopped by the removed «إنهاء التكرار», which
        //     recorded itself as a completion while staying enabled.
        insertReminder(
            id = 2,
            title = "اجتماع الفريق",
            recurrenceType = "weekly",
            daysOfWeek = 0b0010101,
            timeMinutes = 600,
            completedAtMillis = ENDED_AT,
        )
        // 3 — a reminder the user paused the other way, with enabled = 0.
        insertReminder(
            id = 3,
            title = "قراءة الورد",
            recurrenceType = "daily",
            timeMinutes = 1_140,
            enabled = false,
        )
        // 4 — a one-time appointment that genuinely happened. Its completion is
        //     a fact about the world, not a legacy encoding of "stopped".
        insertReminder(
            id = 4,
            title = "موعد الطبيب",
            recurrenceType = "once",
            date = "2026-02-11",
            timeMinutes = 555,
            completedAtMillis = APPOINTMENT_DONE_AT,
        )
        // 5 — snoozed at the moment of the update.
        insertReminder(
            id = 5,
            title = "صلاة الاستخارة",
            recurrenceType = "daily",
            timeMinutes = 300,
            snoozedUntilMillis = SNOOZED_UNTIL,
        )
        // 6 — Hijri reminder data written before v5.
        insertReminder(
            id = 6,
            title = "زكاة الشهر",
            recurrenceType = "monthly",
            calendar = "hijri",
            dayOfMonth = 15,
            timeMinutes = 720,
        )
        // 7 — both legacy stop encodings at once.
        insertReminder(
            id = 7,
            title = "فاتورة الكهرباء",
            recurrenceType = "monthly",
            dayOfMonth = 28,
            timeMinutes = 1_020,
            enabled = false,
            completedAtMillis = BILL_ENDED_AT,
        )
        // 8 — every optional field populated, in Arabic, so "content preserved"
        //     means something stronger than "the row survived".
        insertReminder(
            id = 8,
            title = "ذكرى الزواج",
            recurrenceType = "yearly",
            calendar = "hijri",
            month = 10,
            dayOfMonth = 3,
            timeMinutes = 1_200,
            notes = "اتصل بأهلها أولًا، ثم احجز المطعم",
            categoryId = "family",
            priority = 2,
            alertMode = "alarm",
            ringtoneUri = "content://media/internal/audio/media/42",
            alarmTimeoutMinutes = 7,
            alarmGradualVolume = false,
            alarmRepeatIfIgnored = true,
            followUntilComplete = true,
            followUpIntervalMinutes = 15,
            followUpMaxRepeats = 4,
            completionLabel = "هل هنّأتها؟",
            snoozeMinutes = 20,
        )
        // 9 — a one-time reminder that was disabled but never completed.
        insertReminder(
            id = 9,
            title = "تسليم التقرير",
            recurrenceType = "once",
            date = "2026-03-01",
            timeMinutes = 900,
            enabled = false,
        )

        insertRecord(id = 1, reminderId = 1, occurrenceAtMillis = OCCURRENCE, status = "completed")
        insertRecord(id = 2, reminderId = 3, occurrenceAtMillis = OCCURRENCE, status = "skipped")
        // A legacy record for a state رَنّة no longer writes.
        insertRecord(id = 3, reminderId = 2, occurrenceAtMillis = OCCURRENCE, status = "missed")
        // Orphan-prone: the reminder behind this record is already gone.
        insertRecord(id = 4, reminderId = 999, occurrenceAtMillis = OCCURRENCE, status = "completed")
        // The occurrence a snooze is postponing, recorded before v5 could name it.
        insertRecord(id = 5, reminderId = 5, occurrenceAtMillis = OCCURRENCE, status = "completed")
        insertRecord(id = 6, reminderId = 1, occurrenceAtMillis = OCCURRENCE, status = "skipped")

        db.exec(
            "INSERT INTO pending_confirmations " +
                "(id, reminderId, occurrenceAtMillis, sinceMillis, nudgesSent, deadlineAtMillis) " +
                "VALUES (1, 1, $OCCURRENCE, $OCCURRENCE, 2, $NEXT_TRIGGER), " +
                "(2, 5, $OCCURRENCE, $OCCURRENCE, 0, $NEXT_TRIGGER)",
        )
    }

    private fun migrateToV5() = BalDatabase.MIGRATION_4_5.migrate(db.asSupportDatabase())

    // ── Normalisation: only what v5 set out to change ──────────────────────

    @Test
    fun `ended series become plain paused reminders`() {
        migrateToV5()

        assertEquals(false to null, enabledAndCompletion(2))
        assertEquals(false to null, enabledAndCompletion(7))
    }

    @Test
    fun `a completed one-time reminder stays completed`() {
        migrateToV5()

        assertEquals(true to APPOINTMENT_DONE_AT, enabledAndCompletion(4))
        assertEquals(false to null, enabledAndCompletion(9))
    }

    @Test
    fun `active and already-paused reminders are left untouched`() {
        val before = listOf(1L, 3L, 5L, 6L, 8L).associateWith(::enabledAndCompletion)

        migrateToV5()

        assertEquals(before, before.keys.associateWith(::enabledAndCompletion))
    }

    @Test
    fun `no recurring reminder is left carrying a completion`() {
        migrateToV5()

        assertEquals(
            0L,
            db.count(
                "SELECT COUNT(*) FROM reminders " +
                    "WHERE recurrenceType <> 'once' AND completedAtMillis IS NOT NULL",
            ),
        )
    }

    // ── Preservation: identity, content, schedules ─────────────────────────

    @Test
    fun `every reminder id survives, exactly once`() {
        val before = reminderIds()

        migrateToV5()

        assertEquals((1L..9L).toList(), before)
        assertEquals(before, reminderIds())
        assertEquals(9L, db.count("SELECT COUNT(DISTINCT id) FROM reminders"))
    }

    @Test
    fun `user-authored content is preserved verbatim`() {
        val before = userContent()

        migrateToV5()

        assertEquals(before, userContent())
        // Guard the guard: the fixture must actually carry Arabic prose.
        assertTrue(before.getValue(8L).contains("اتصل بأهلها أولًا، ثم احجز المطعم"))
    }

    @Test
    fun `recurring schedules stay valid across the migration`() {
        val before = schedules()

        migrateToV5()

        assertEquals(before, schedules())
        // The Hijri rows are the ones most likely to be quietly rewritten.
        assertTrue(before.getValue(6L).startsWith("monthly/hijri"))
        assertTrue(before.getValue(8L).startsWith("yearly/hijri"))
    }

    // ── The new occurrence identity ────────────────────────────────────────

    @Test
    fun `a snooze keeps its deadline and starts with no claimed occurrence`() {
        migrateToV5()

        val snoozed = db.select(
            "SELECT snoozedUntilMillis, snoozedOccurrenceAtMillis FROM reminders WHERE id = 5",
        ) { it.getLong(1) to it.getObject(2) }.single()

        assertEquals(SNOOZED_UNTIL, snoozed.first)
        assertNull(
            "A migrated snooze has not yet named its occurrence; v5 fills it on the next «تأجيل».",
            snoozed.second,
        )
        assertEquals(
            9L,
            db.count("SELECT COUNT(*) FROM reminders WHERE snoozedOccurrenceAtMillis IS NULL"),
        )
    }

    // ── Occurrence records ─────────────────────────────────────────────────

    @Test
    fun `occurrence records survive, including skipped, legacy and orphaned ones`() {
        val before = records()

        migrateToV5()

        assertEquals(before, records())
        assertEquals(6, before.size)
        assertTrue("the orphaned record must not be collected", before.containsKey(4L))
        assertEquals("completed@999", before.getValue(4L))
        assertEquals("skipped@3", before.getValue(2L))
        assertEquals("missed@2", before.getValue(3L))
    }

    @Test
    fun `nothing is duplicated`() {
        migrateToV5()

        assertEquals(9L, db.count("SELECT COUNT(*) FROM reminders"))
        assertEquals(6L, db.count("SELECT COUNT(*) FROM completions"))
        assertEquals(
            0L,
            db.count(
                "SELECT COUNT(*) FROM (SELECT reminderId, occurrenceAtMillis, status " +
                    "FROM completions GROUP BY 1, 2, 3 HAVING COUNT(*) > 1)",
            ),
        )
    }

    // ── Removal: only what was meant to go ─────────────────────────────────

    @Test
    fun `only the pending confirmations table is dropped`() {
        assertEquals(listOf("completions", "pending_confirmations", "reminders"), db.tableNames())

        migrateToV5()

        assertEquals(listOf("completions", "reminders"), db.tableNames())
    }

    // ── The shape Room will validate at startup ────────────────────────────

    @Test
    fun `the migrated schema is exactly the exported v5 schema`() {
        migrateToV5()

        Schemas.openAt(5).use { fresh ->
            assertEquals(fresh.describeSchema(), db.describeSchema())
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun enabledAndCompletion(id: Long): Pair<Boolean, Long?> =
        db.select("SELECT enabled, completedAtMillis FROM reminders WHERE id = $id") {
            (it.getInt(1) == 1) to it.getObject(2)?.let { value -> (value as Number).toLong() }
        }.single()

    private fun reminderIds(): List<Long> =
        db.select("SELECT id FROM reminders ORDER BY id") { it.getLong(1) }

    private fun userContent(): Map<Long, String> =
        db.select(
            "SELECT id, title, notes, categoryId, priority, completionLabel, ringtoneUri, " +
                "snoozeMinutes, alertMode, createdAtMillis FROM reminders ORDER BY id",
        ) {
            it.getLong(1) to (2..10).joinToString("|") { column -> "${it.getObject(column)}" }
        }.toMap()

    private fun schedules(): Map<Long, String> =
        db.select(
            "SELECT id, recurrenceType, calendar, timeMinutes, date, year, month, " +
                "daysOfWeek, dayOfMonth FROM reminders ORDER BY id",
        ) {
            it.getLong(1) to "${it.getString(2)}/${it.getString(3)}/" +
                (4..9).joinToString("|") { column -> "${it.getObject(column)}" }
        }.toMap()

    private fun records(): Map<Long, String> =
        db.select(
            "SELECT id, status, reminderId FROM completions ORDER BY id",
        ) { it.getLong(1) to "${it.getString(2)}@${it.getLong(3)}" }.toMap()

    @Suppress("LongParameterList")
    private fun insertReminder(
        id: Long,
        title: String,
        recurrenceType: String,
        timeMinutes: Int,
        notes: String? = null,
        categoryId: String = "general",
        priority: Int = 1,
        calendar: String = "gregorian",
        date: String? = null,
        year: Int? = null,
        month: Int? = null,
        daysOfWeek: Int = 0,
        dayOfMonth: Int? = null,
        enabled: Boolean = true,
        alertMode: String = "standard",
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        snoozeMinutes: Int = 10,
        ringtoneUri: String? = null,
        alarmTimeoutMinutes: Int = 3,
        alarmGradualVolume: Boolean = true,
        alarmRepeatIfIgnored: Boolean = false,
        followUntilComplete: Boolean = false,
        followUpIntervalMinutes: Int = 5,
        followUpMaxRepeats: Int = 3,
        completionLabel: String? = null,
        snoozedUntilMillis: Long? = null,
        nextTriggerAtMillis: Long? = NEXT_TRIGGER,
        createdAtMillis: Long = CREATED,
        completedAtMillis: Long? = null,
    ) {
        db.prepareStatement(
            "INSERT INTO reminders (id, title, notes, categoryId, priority, recurrenceType, " +
                "calendar, timeMinutes, date, year, month, daysOfWeek, dayOfMonth, enabled, " +
                "alertMode, soundEnabled, vibrationEnabled, snoozeMinutes, ringtoneUri, " +
                "alarmTimeoutMinutes, alarmGradualVolume, alarmRepeatIfIgnored, " +
                "followUntilComplete, followUpIntervalMinutes, followUpMaxRepeats, " +
                "completionLabel, snoozedUntilMillis, nextTriggerAtMillis, createdAtMillis, " +
                "completedAtMillis) VALUES (${"?, ".repeat(29)}?)",
        ).use { statement ->
            val values = listOf(
                id, title, notes, categoryId, priority, recurrenceType, calendar, timeMinutes,
                date, year, month, daysOfWeek, dayOfMonth, enabled.toInt(), alertMode,
                soundEnabled.toInt(), vibrationEnabled.toInt(), snoozeMinutes, ringtoneUri,
                alarmTimeoutMinutes, alarmGradualVolume.toInt(), alarmRepeatIfIgnored.toInt(),
                followUntilComplete.toInt(), followUpIntervalMinutes, followUpMaxRepeats,
                completionLabel, snoozedUntilMillis, nextTriggerAtMillis, createdAtMillis,
                completedAtMillis,
            )
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun insertRecord(id: Long, reminderId: Long, occurrenceAtMillis: Long, status: String) {
        db.exec(
            "INSERT INTO completions (id, reminderId, reminderTitle, categoryId, " +
                "occurrenceAtMillis, completedAtMillis, status) VALUES " +
                "($id, $reminderId, 'سجل', 'general', $occurrenceAtMillis, $occurrenceAtMillis, " +
                "'$status')",
        )
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}
