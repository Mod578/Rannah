package com.bal.reminders.di

import android.content.Context
import androidx.room.Room
import com.bal.reminders.data.ReminderRepositoryImpl
import com.bal.reminders.data.db.BalDatabase
import com.bal.reminders.data.db.ReminderDao
import com.bal.reminders.domain.HijriAdjustmentProvider
import com.bal.reminders.domain.ReminderRepository
import com.bal.reminders.parser.ArabicReminderParser
import com.bal.reminders.parser.ReminderParser
import com.bal.reminders.scheduling.AlarmGateway
import com.bal.reminders.scheduling.AndroidAlarmGateway
import com.bal.reminders.scheduling.NotificationPresenter
import com.bal.reminders.scheduling.ReminderNotifications
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun reminderParser(impl: ArabicReminderParser): ReminderParser

    @Binds
    abstract fun alarmGateway(impl: AndroidAlarmGateway): AlarmGateway

    @Binds
    abstract fun reminderNotifications(impl: NotificationPresenter): ReminderNotifications

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): BalDatabase =
            Room.databaseBuilder(context, BalDatabase::class.java, "bal.db")
                .addMigrations(
                    BalDatabase.MIGRATION_1_2,
                    BalDatabase.MIGRATION_2_3,
                    BalDatabase.MIGRATION_3_4,
                    BalDatabase.MIGRATION_4_5,
                )
                .build()

        @Provides
        fun reminderDao(db: BalDatabase): ReminderDao = db.reminderDao()

        @Provides
        @Singleton
        fun clock(): Clock = Clock.systemDefaultZone()

        /**
         * Scheduling is Gregorian; the Hijri calendar is informational only, so
         * no sighting adjustment is applied. Reminders saved on Hijri dates
         * before the switch still fire, using the computed Umm al-Qura tables.
         */
        @Provides
        @Singleton
        fun hijriAdjustment(): HijriAdjustmentProvider = HijriAdjustmentProvider { 0 }
    }
}
