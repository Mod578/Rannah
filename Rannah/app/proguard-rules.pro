# رَنّة — release shrinking rules.
#
# Room, Hilt, Compose, WorkManager and DataStore all ship consumer rules inside
# their artifacts, and every manifest-declared component is kept automatically
# from the merged manifest. What follows is only what those do not cover, plus
# the things this app must never trade away for a smaller APK.

# Readable crash reports from a minified build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generated Room and Hilt code reads the signatures of what it wraps.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisible*Annotations

# ── Scheduling: never sacrifice a ring to save bytes ────────────────────────
# WorkManager persists a worker's *class name* in its own database at enqueue
# time, and ReconcileWorker's periodic request outlives app updates. An update
# that renamed the class would leave stored work pointing at a name that no
# longer exists, and the daily alarm reconciliation would silently stop.
-keep class com.bal.reminders.scheduling.ReconcileWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Receivers and the ringer service are entry points reached from PendingIntents
# and system broadcasts. Keeping them by name says so, rather than relying on
# the manifest-derived rules to happen to save them.
-keep class com.bal.reminders.scheduling.AlarmReceiver { *; }
-keep class com.bal.reminders.scheduling.NotificationActionReceiver { *; }
-keep class com.bal.reminders.scheduling.SystemEventsReceiver { *; }
-keep class com.bal.reminders.scheduling.AlarmRingerService { *; }
-keep class com.bal.reminders.widget.RannaWidgetProvider { *; }

# ── Room ───────────────────────────────────────────────────────────────────
# The database class is looked up reflectively as "<name>_Impl", and the four
# migrations live in its companion, so both must survive under their own names.
-keep class com.bal.reminders.data.db.BalDatabase { *; }
-keep class com.bal.reminders.data.db.BalDatabase$Companion { *; }
-keep class com.bal.reminders.data.db.BalDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class com.bal.reminders.data.db.** { *; }
-dontwarn androidx.room.paging.**

# ── Hilt ───────────────────────────────────────────────────────────────────
# The application class is named in the manifest and holds the WorkerFactory.
-keep class com.bal.reminders.BalApp { *; }

# ── Noise from optional compile-time-only references ───────────────────────
-dontwarn java.lang.invoke.**
-dontwarn org.jetbrains.annotations.**
