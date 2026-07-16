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
    version = 3,
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
    }
}
