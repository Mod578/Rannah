package com.bal.reminders.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReminderEntity::class,
        OccurrenceRecordEntity::class,
        PendingConfirmationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class BalDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        /**
         * v2: occurrence-level records (status column + idempotency index),
         * alert mode (تنبيه عادي / منبّه مهم) with its alarm options, and the
         * calendar system as real scheduling data. Legacy hijri_monthly rows
         * become monthly rows in the Hijri calendar; nothing is lost.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN calendar TEXT NOT NULL DEFAULT 'gregorian'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN year INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN month INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN alertMode TEXT NOT NULL DEFAULT 'standard'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN ringtoneUri TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN alarmTimeoutMinutes INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE reminders ADD COLUMN alarmGradualVolume INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reminders ADD COLUMN alarmRepeatIfIgnored INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN stopMarksCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE reminders SET recurrenceType = 'monthly', calendar = 'hijri' " +
                        "WHERE recurrenceType = 'hijri_monthly'",
                )

                db.execSQL("ALTER TABLE completions ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")
                // The unique idempotency index needs duplicates gone first.
                db.execSQL(
                    "DELETE FROM completions WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM completions GROUP BY reminderId, occurrenceAtMillis, status)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_completions_reminderId_occurrenceAtMillis_status` " +
                        "ON `completions` (`reminderId`, `occurrenceAtMillis`, `status`)",
                )
            }
        }

        /**
         * v3: «المتابعة حتى الإنجاز». Adds the opt-in and its bounded policy to
         * every reminder (off, so no existing reminder changes behaviour), the
         * contextual completion phrase, and the live pending-confirmation table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN followUntilComplete INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN followUpIntervalMinutes INTEGER NOT NULL DEFAULT 5",
                )
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN followUpMaxRepeats INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL("ALTER TABLE reminders ADD COLUMN completionLabel TEXT DEFAULT NULL")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_confirmations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, " +
                        "`occurrenceAtMillis` INTEGER NOT NULL, " +
                        "`sinceMillis` INTEGER NOT NULL, " +
                        "`nudgesSent` INTEGER NOT NULL, " +
                        "`deadlineAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_confirmations_reminderId` " +
                        "ON `pending_confirmations` (`reminderId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_pending_confirmations_reminderId_occurrenceAtMillis` " +
                        "ON `pending_confirmations` (`reminderId`, `occurrenceAtMillis`)",
                )
            }
        }

        /**
         * v4: drops `stopMarksCompleted`.
         *
         * The column let «إيقاف الصوت» record a completion. That made one verb
         * mean two things — silence a ringer, and assert that an obligation in
         * the world was met — and which one it meant depended on a switch buried
         * in customization. رَنّة now has exactly one rule: stopping a sound
         * stops a sound. Completion is always its own deliberate act.
         *
         * SQLite before 3.35 has no DROP COLUMN, and minSdk 26 ships older
         * engines, so the table is recreated. Every surviving column is copied
         * by name; no reminder loses a field it had.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `notes` TEXT, `categoryId` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, `recurrenceType` TEXT NOT NULL, " +
                        "`calendar` TEXT NOT NULL DEFAULT 'gregorian', " +
                        "`timeMinutes` INTEGER NOT NULL, `date` TEXT, `year` INTEGER, " +
                        "`month` INTEGER, `daysOfWeek` INTEGER NOT NULL, `dayOfMonth` INTEGER, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`alertMode` TEXT NOT NULL DEFAULT 'standard', " +
                        "`soundEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, " +
                        "`snoozeMinutes` INTEGER NOT NULL, `ringtoneUri` TEXT, " +
                        "`alarmTimeoutMinutes` INTEGER NOT NULL DEFAULT 3, " +
                        "`alarmGradualVolume` INTEGER NOT NULL DEFAULT 1, " +
                        "`alarmRepeatIfIgnored` INTEGER NOT NULL DEFAULT 0, " +
                        "`followUntilComplete` INTEGER NOT NULL DEFAULT 0, " +
                        "`followUpIntervalMinutes` INTEGER NOT NULL DEFAULT 5, " +
                        "`followUpMaxRepeats` INTEGER NOT NULL DEFAULT 3, " +
                        "`completionLabel` TEXT DEFAULT NULL, " +
                        "`snoozedUntilMillis` INTEGER, `nextTriggerAtMillis` INTEGER, " +
                        "`createdAtMillis` INTEGER NOT NULL, `completedAtMillis` INTEGER)",
                )
                db.execSQL(
                    "INSERT INTO `reminders_new` (" +
                        "`id`, `title`, `notes`, `categoryId`, `priority`, `recurrenceType`, " +
                        "`calendar`, `timeMinutes`, `date`, `year`, `month`, `daysOfWeek`, " +
                        "`dayOfMonth`, `enabled`, `alertMode`, `soundEnabled`, " +
                        "`vibrationEnabled`, `snoozeMinutes`, `ringtoneUri`, " +
                        "`alarmTimeoutMinutes`, `alarmGradualVolume`, `alarmRepeatIfIgnored`, " +
                        "`followUntilComplete`, `followUpIntervalMinutes`, `followUpMaxRepeats`, " +
                        "`completionLabel`, `snoozedUntilMillis`, `nextTriggerAtMillis`, " +
                        "`createdAtMillis`, `completedAtMillis`) " +
                        "SELECT `id`, `title`, `notes`, `categoryId`, `priority`, " +
                        "`recurrenceType`, `calendar`, `timeMinutes`, `date`, `year`, `month`, " +
                        "`daysOfWeek`, `dayOfMonth`, `enabled`, `alertMode`, `soundEnabled`, " +
                        "`vibrationEnabled`, `snoozeMinutes`, `ringtoneUri`, " +
                        "`alarmTimeoutMinutes`, `alarmGradualVolume`, `alarmRepeatIfIgnored`, " +
                        "`followUntilComplete`, `followUpIntervalMinutes`, `followUpMaxRepeats`, " +
                        "`completionLabel`, `snoozedUntilMillis`, `nextTriggerAtMillis`, " +
                        "`createdAtMillis`, `completedAtMillis` FROM `reminders`",
                )
                db.execSQL("DROP TABLE `reminders`")
                db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
            }
        }
    }
}
