# Architecture

Developer notes for رَنّة. The user facing description lives in the [README](../../README.md); this file covers how the app is put together and why.

## Shape

One Gradle module, `:app`, package `com.bal.reminders`. Kotlin with Jetpack Compose for the UI, Room for storage, Hilt for injection, and AlarmManager for delivery. Minimum SDK 26, target and compile SDK 35.

```
app/src/main/java/com/bal/reminders/
├── ui/            Compose screens and ViewModels, one package per screen
├── domain/        models, recurrence, occurrence state, repository interface
├── data/          Room database, DAO, repository implementation, settings
├── scheduling/    the lifecycle owner, alarm gateway, receivers, notifications
├── parser/        Arabic text to a reminder schedule
├── format/        date and number formatting, Gregorian and Hijri
├── widget/        home screen widget
└── di/            Hilt module
```

## The rule that holds it together

**The database is the source of truth, and every alarm is derivable from it.**

Nothing about a reminder lives only in AlarmManager. `ReminderScheduler` writes the row first, then asks `AlarmGateway` to set the alarm. If the process dies, the device reboots, the clock jumps, or the app is reinstalled, the alarms can be rebuilt from the tables without losing anything. That is what `SystemEventsReceiver` does on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_CHANGED` and `TIMEZONE_CHANGED`, and what the periodic `ReconcileWorker` does as a safety net.

## Layers

| Layer | Responsibility | Depends on |
| --- | --- | --- |
| `ui` | Compose screens, ViewModels holding screen state | domain, scheduling |
| `scheduling` | the reminder lifecycle: fire, snooze, complete, skip, reschedule | domain, data |
| `domain` | models, recurrence maths, occurrence state, repository interface | nothing Android specific |
| `data` | Room entities and DAO, repository implementation, DataStore settings | domain |

`domain` holds the interface, `data` holds the implementation, and Hilt binds one to the other in `di/AppModule.kt`. The recurrence and occurrence logic is plain Kotlin with an injected `Clock`, which is what makes it testable on the JVM without an emulator.

## Scheduling

`ReminderScheduler` is the single owner of the lifecycle. Every action goes through it, and every action is idempotent, so a replayed broadcast or a double tap cannot produce a second outcome for the same occurrence.

- **تأجيل (snooze)** postpones the current occurrence and can repeat; the occurrence keeps its identity across postponements.
- **تم (complete)** records the occurrence as done and never touches the series.
- **إيقاف مؤقت / استئناف (pause and resume)** and **حذف (delete)** act on the series and are explicit and separate. Delete returns a snapshot that restore can put back.

`complete` and `skipOccurrence` both write through the repository's transactional terminal write, so one occurrence can never end up both completed and skipped.

`AlarmGateway` is an interface with one Android implementation. Tests substitute a fake and assert on what would have been scheduled, without touching the platform.

Delivery has two modes. A normal reminder posts a notification. An alarm mode reminder («منبّه مهم») shows a full screen activity over the lock screen and runs `AlarmRingerService` as a foreground service of type `systemExempted`, which is the type Android reserves for a continuing exact alarm.

## Storage

Room, database version 6, two tables:

- `reminders`, the series with its schedule
- `completions`, the per occurrence outcome log

Schemas are exported to `app/schemas/` and the migration tests build the legacy databases from those exported files, so changing a schema re-runs them. Settings that are not reminders (theme, default snooze, onboarding state) live in a DataStore preferences file rather than in Room.

## Arabic parsing

`ArabicReminderParser` is rule based, not a model. It normalises the text, consumes every date and time expression it recognises, and what is left becomes the title. It handles one time, daily, weekly by weekday, monthly by day number, and relative offsets. Times without an AM or PM marker use a daytime heuristic. Schedules are Gregorian; Hijri dates are a display layer on top, with a user adjustment offset.

## Testing

JVM unit tests under `app/src/test`, run with `./gradlew testDebugUnitTest`. They cover recurrence, occurrence state, the scheduler lifecycle against a fake gateway, snooze limits, Room migrations, Hijri formatting, Arabic parsing, and checklist grouping. There are no instrumented tests in the repository; anything that needs a real device, such as OEM battery behaviour or alarm audio, is verified by hand.

## Privacy by construction

The app declares no `INTERNET` permission and has no network dependency, so reminder data cannot leave the device through the app. It can still leave through Android's own backup, because `allowBackup` is true, which the [privacy statement](PRIVACY.md) says plainly.

## Release builds

Signing credentials are never in the repository. `app/build.gradle.kts` reads them from a properties file outside the tree, by default `~/.keystores/rannah/keystore.properties`, overridable with `-Prannah.keystoreProperties` or the `RANNAH_KEYSTORE_PROPERTIES` environment variable. When that file is absent the release variant still assembles, unsigned, so a machine without the private key can verify compilation, shrinking and lint.
