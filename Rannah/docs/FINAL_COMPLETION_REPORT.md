# رَنّة — Final Completion Report

## Part I — Release 1.0.0

**Phase: release preparation and hardening.** No product decision was reopened
and no feature was added. What changed is everything that stands between a green
build and an installable, privately signed application.

**Verdict: ready to release.** `com.bal.reminders` · **1.0.0 (versionCode 1)` ·
143 unit tests green · lint 0 errors · release APK signed with a private 4096-bit
RSA key, non-debuggable, shrunk to 2.0 MB.

---

### 1. Application identity — kept

**`com.bal.reminders` stays.** It is not a branding decision and it was not made
on aesthetics: the application ID is the only thing Android uses to decide
whether an APK updates an existing install or becomes a second, unrelated app.
رَنّة has already been installed under this ID, with real reminders in
`bal.db`. Renaming it to something that reads better in a package list would
strand every one of those reminders on a version that could never be updated
again — the user would keep an orphaned app and start from an empty database.
The name the user sees is `@string/app_name` = **رَنّة**, and that is the only
name that was ever theirs to read.

Identity is internally consistent and was verified in the *shipped* manifest,
not just the source: `namespace` and `applicationId` agree, the launcher label
resolves to رَنّة in every packaged locale, the one manifest authority is
`${applicationId}.androidx-startup` (derived, so it moves with the ID), and
there are no deep links, no custom providers and no custom permissions.

**Consequence to know about.** Every build installed until now was a *debug*
build signed with the Android debug key. This release is signed with رَنّة's own
key, and Android refuses to update an app when the signing certificate changes.
The first install of 1.0.0 therefore requires removing the debug build, and that
removes its database with it. Every release from 1.0.0 onward updates in place
and keeps its data, because the key never changes again.

### 2. Version

`versionName = 1.0.0`, `versionCode = 1` — kept, because this is genuinely the
first *distributed* release; everything before it was a development build. Both
live in `defaultConfig` as plain literals, so future releases increment
`versionCode` on its own line and the application ID is never touched.

### 3. Signing — private, and outside this repository

A 4096-bit RSA key (`SHA256withRSA`, valid to 2056-07-18) was generated locally
in a PKCS#12 keystore. The keystore and its credential file live at
`~/.keystores/rannah/`, both `0600` inside a `0700` directory, **outside the
repository**. Nothing secret is tracked: `app/build.gradle.kts` only *names*
where to look, defaulting to `~/.keystores/rannah/keystore.properties` and
overridable with `-Prannah.keystoreProperties=…` or `RANNAH_KEYSTORE_PROPERTIES`.
When that file is absent the release build still assembles, unsigned, so a
machine without the key can still verify compilation, shrinking and lint. The
repository's `.gitignore` already excludes `*.jks`, `*.keystore` and
`keystore.properties`.

Certificate SHA-256:
`70:FF:2A:39:E2:94:85:FB:FB:1F:08:BB:91:7C:55:FA:DA:6B:F4:AE:76:5D:57:18:13:C0:D4:02:2E:46:DD:50`

Signature schemes: **v2 and v3 enabled and verified; v1 deliberately off.**
v2/v3 protect the whole APK including the `META-INF` entries that v1's per-entry
digests structurally cannot cover, and every device رَنّة supports (minSdk 26)
verifies them. v1 was measured first — it signed and verified correctly at
`--min-sdk-version 21` — and then turned off as the weaker scheme that no
supported device would use.

**The R8 mapping file is not optional.** `app/build/outputs/mapping/release/mapping.txt`
is what makes a crash report from this exact APK readable. It is regenerated on
every build and is not tracked; archive it with the release or crash reports from
1.0.0 will be unreadable stack traces of one-letter class names.

### 4. Migration hardening — v4 → v5 now has automated proof

The previous report's open risk was that «the v5 migration has still not been run
against a real pre-v5 device database». That risk is now covered by **19
automated tests** that run on the JVM with no device involved
(`app/src/test/java/com/bal/reminders/data/db/`).

Two things make them proof rather than rehearsal. The legacy database is built
from the **exported schema JSON** in `app/schemas`, so a v4 database in a test is
the v4 database Room shipped, not SQL retyped by hand. And the migration under
test is the **production `Migration` object itself** — it receives a
`SupportSQLiteDatabase` proxy that forwards `execSQL` to a real SQLite engine and
throws on anything else, so no statement is reinterpreted and a future migration
that reaches for a query API cannot pass by accident.

The v4 fixture carries every legacy shape at once: a recurring series stopped by
the removed «إنهاء التكرار» (a completion on an enabled recurring row), a
reminder paused with `enabled = 0`, both encodings on one row, a snoozed
reminder, a genuinely completed one-time appointment, Hijri monthly and Hijri
yearly rows, a fully populated row with Arabic prose in `notes` and
`completionLabel`, a disabled-but-never-completed one-time reminder, and
occurrence records that are completed, skipped, legacy `missed`, and **orphaned**
— pointing at a reminder id that no longer exists.

Verified after `MIGRATION_4_5`:

| Claim | How it is checked |
|---|---|
| reminder IDs preserved | ids are exactly `1..9` before and after, and `COUNT(DISTINCT id) = 9` |
| user content preserved | title, notes, category, priority, completion label, ringtone, snooze, alert mode and creation time compared verbatim, including Arabic prose |
| legacy states normalised | both "ended series" encodings become `enabled = 0, completedAtMillis = NULL`; **zero** recurring rows are left carrying a completion |
| only what was meant to change | a completed *one-time* reminder keeps its completion; active and already-paused rows are compared field-for-field and are untouched |
| recurring schedules stay valid | recurrence type, calendar, time, date, year, month, weekday mask and day-of-month compared verbatim, Hijri rows included |
| snooze occurrence identity | `snoozedUntilMillis` survives, `snoozedOccurrenceAtMillis` is added and starts `NULL` on every row — v5 claims it on the next «تأجيل» |
| no duplicates | row counts unchanged, and no `(reminderId, occurrenceAtMillis, status)` triple appears twice |
| removal is exactly scoped | `pending_confirmations` is dropped, `completions` and `reminders` remain, the orphaned record is still there |
| the result is what Room expects | the migrated schema is compared **column-for-column and index-for-index against a database freshly created from `5.json`** |

A second suite walks the whole ladder — a version 1 database through all four
migrations — and additionally proves that legacy `hijri_monthly` rows become
`monthly` + `calendar = 'hijri'`, that duplicate v1 completion rows collapse to
the *earliest* record so v2's unique index can exist, that `stopMarksCompleted`
is gone and nothing else is, and that the chain lands on exactly the exported v5
schema. One test asserts there is a `Migration` for **every step between every
exported schema**, so adding a v6 schema without writing its migration fails the
build rather than falling through to a destructive rebuild.

**There is no `fallbackToDestructiveMigration` anywhere** — `AppModule` registers
all four migrations and nothing else.

*Honest limit:* the engine is JVM SQLite, not the device's. These tests prove the
SQL and the data transformations; they do not prove the binary behaviour of one
OEM's SQLite build.

**The migration tests have teeth, and that was measured.** Deliberately removing
the `recurrenceType <> 'once'` guard failed exactly the two tests that defend
completed appointments; deliberately omitting the `ALTER TABLE ADD COLUMN` failed
four, including both schema-equivalence tests. Both mutations were reverted.

### 5. Security and privacy

Reviewed against the shipped APK's manifest, not the source:

- **No `INTERNET` permission** — the app cannot open a socket, which is a
  stronger guarantee than any cleartext-traffic policy would be. No analytics, no
  telemetry, no crash reporter, no ad SDK is on the dependency list.
- **No logging at all.** Zero `Log.*`, `println` or `printStackTrace` calls in
  `app/src/main`, so no reminder title can reach logcat.
- **Every PendingIntent is `FLAG_IMMUTABLE`** — all eight of them.
- **Exported components: four, and only one is ours.** `MainActivity`, because a
  launcher must be. It reads a single `long` extra and, at worst, opens the
  user's own reminder on their own screen. The other three are library entry
  points the platform requires and each is permission-guarded:
  WorkManager's `SystemJobService` (`BIND_JOB_SERVICE`), WorkManager's
  `DiagnosticsReceiver` and ProfileInstaller's `ProfileInstallReceiver` (both
  `DUMP`). Every رَنّة activity, service and receiver — the alarm screen, the
  ringer, the alarm and notification receivers, the widget, the boot receiver —
  is `exported="false"`.
- **`ACCESS_NETWORK_STATE` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` are
  merged in by libraries, and both are kept deliberately.** The first comes from
  WorkManager, whose constraint trackers query connectivity on startup; removing
  it would risk a `SecurityException` in the very component that re-derives
  dropped alarms, and reading connectivity state without `INTERNET` cannot send
  anything anywhere. The second is androidx.core's own signature-level
  self-protection for non-exported dynamic receivers.
- **`SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.** The revocable one, requested
  through the settings screen, is the correct choice for a general reminder app;
  `USE_EXACT_ALARM` is reserved for alarm-clock and calendar apps.
- **Not debuggable.** The shipped manifest has no `android:debuggable` attribute
  at all, verified with `aapt2 dump xmltree`.
- **No file exposure.** No `FileProvider`, no exported provider, no
  `grantUriPermission`, nothing written to external storage.

**Android system backup may include رَنّة's data — this is true and the app says
so.** `allowBackup="true"` with an empty `<full-backup-content/>` and empty
`<cloud-backup/>` / `<device-transfer/>` rules means the default applies: the app's
data directory, **including `bal.db` and therefore every reminder's title and
notes**, is eligible for Android's cloud backup and device-to-device transfer.
The privacy screen states exactly that — «قد ينسخ أندرويد بياناتك إلى حسابك في
Google، وهي ميزة من النظام يمكنك إيقافها من إعداداته» — and its code comment
records why the comfortable sentence was rejected. WorkManager's own database is
not affected; it lives in the no-backup directory by design.

The other two privacy claims were re-checked against this build and both hold:
«لا تطلب رَنّة إذن الإنترنت، ولا ترسل عنك شيئًا» (no `INTERNET` permission in the
packaged manifest) and «لا حساب، ولا إعلانات، ولا تتبّع» (no account, ad or
analytics dependency). No privacy wording needed changing.

*Lock screen:* alarm notifications carry the platform default visibility, so a
secure lock screen redacts them according to the user's own setting. The
full-screen alarm screen does show the reminder's title over the lock screen —
that is what an alarm is for, and it is a product decision, not an oversight.

### 6. Release optimization

R8 code shrinking and resource shrinking are on. **18,797,183 → 2,092,670 bytes,
an 89 % reduction**, with `material-icons-extended` and unused Compose surface
stripped out.

`proguard-rules.pro` was rewritten around one rule: *never trade a ring for
bytes.* The important keep is `ReconcileWorker`. WorkManager persists a worker's
**class name** in its own database at enqueue time, and that periodic request
outlives app updates — so a future release that renamed the class would leave
stored work pointing at a name that no longer exists, and the daily alarm
reconciliation would silently stop. The alarm receivers, the ringer service, the
widget provider, `BalDatabase` and its companion (which holds the migrations, and
whose `_Impl` is looked up reflectively) are kept by name for the same reason.
`DebugProbesKt.bin`, the coroutines debug-agent metadata, is the one thing
excluded from packaging.

Assets were audited rather than assumed: all four Tajawal weights are referenced
in `Theme.kt`, all four drawables (`ic_launcher_foreground`, `ic_notification`,
`ic_splash`, `widget_bg`) are referenced, lint reports **no unused resources**,
and language splits stay disabled in the AAB because the app forces Arabic at
runtime. The adaptive icon carries a `<monochrome>` layer. The two licence texts
in `assets/licenses/` are kept: the OFL requires shipping Tajawal's licence
whether or not a screen displays it. `design/logo/rannah-logo-concept.png` stays
as the source artwork the launcher icon was traced from; it is not packaged.

Nothing was removed as "obsolete" that could not be proven unused. Two dead
`Modifier` defaults on `AppMark` and `AppIconTile` were the only deletions —
every call site already passed its own size — which also cleared lint's one
warning about app code.

### 7. Automated verification

No emulator, no connected-device task, no manual testing.

| Check | Result |
|---|---|
| `:app:test` | **143 tests, 0 failures** (19 of them new migration tests) |
| `:app:lint` | **0 errors, 0 fatal, 54 warnings** |
| `:app:lintVitalRelease` | passed (release-blocking lint) |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleRelease` | BUILD SUCCESSFUL |
| `:app:bundleRelease` | BUILD SUCCESSFUL |
| `apksigner verify` | verified, v2 + v3, 1 signer |
| `zipalign -c 4` | 4-byte aligned |
| `jarsigner -verify` (AAB) | jar verified |
| shipped manifest | inspected with `aapt2 dump badging` / `xmltree` |

**Nothing was suppressed.** All 54 lint warnings are accounted for: 51 are
`GradleDependency` / `AndroidGradlePluginVersion` notices that a newer version
exists (AGP 8.7.3 → 9.3.1, Compose BOM 2024.12 → 2026.06, Room 2.6.1 → 2.8.4 and
so on). Upgrading the whole toolchain on the eve of a release would change the
Compose runtime, the Room compiler and R8 all at once, and the only verification
available here is a build — not a device. They are left for a 1.0.1 with time to
verify. The remaining 3 are `VectorPath`: the traced bell is a 911-character path
repeated in the launcher, notification and splash drawables. Reducing its
precision would alter the supplied mark, which is not a trade worth making for a
one-off parse of three vectors.

### 8. Artifacts

| File | Bytes | SHA-256 |
|---|---|---|
| `~/Desktop/rannah-release.apk` | 2,092,670 | `4010b8bd2935f27cf2e0ac3754338a486701cc28e714a7c99ddc7cb1bf48d5ee` |
| `~/Downloads/rannah-release.apk` | 2,092,670 | `4010b8bd2935f27cf2e0ac3754338a486701cc28e714a7c99ddc7cb1bf48d5ee` |
| `~/Desktop/rannah-release.aab` | 4,402,722 | `f4c9335b685026a9362d552e735b13c55070a068d1ae1f2024716f93fc0910ab` |

The two APKs are byte-identical (`cmp` verified). The earlier **debug** APK at
`~/Downloads/rannah.apk` (18.8 MB) is untouched and is **not** the release.

- Application ID `com.bal.reminders` · versionName `1.0.0` · versionCode `1`
- minSdk **26** · targetSdk **35** · compileSdk 35
- Build type **release** · debuggable **no** · shrinking **yes** (code + resources)
- Signature schemes **v2 + v3** · certificate SHA-256
  `70FF2A39E29485FBFB1F08BB917C55FADA6BF4AE765D571813C0D4022E46DD50`

### 9. Remaining runtime-only risks

These are device-dependent and cannot be closed without a device. No claim is
made about any of them:

- **The first install of 1.0.0 cannot update the existing debug build.** The
  signing certificate changed; the debug build must be uninstalled, and its data
  goes with it. Every release after this one updates in place.
- The v4→v5 migration is proven against JVM SQLite, not against a real device
  database on a real OEM SQLite build.
- Alarm delivery under OEM battery managers, full-screen-intent behaviour over
  the lock screen, and Do Not Disturb interaction remain device-dependent. No
  promise is made that a ring always arrives.
- R8 changes class names; a crash in the field is only readable with the
  `mapping.txt` from *this* build.
- Layout at 200 % font scale, RTL mirroring of the swipe reveal, and menu
  placement near a screen edge were reasoned about, not observed.
- The undo offer is in-memory by design: process death while the snackbar shows
  loses it, and the action stands.
- `versionCode 1` means no update path has ever been exercised end to end on a
  device.

---

## Part II — Previous phase: coherence and refinement

The product work that produced the 1.0.0 feature set. Unchanged by the release
phase and kept here as the record of why the app looks the way it does.

Unit tests **124 passed, 0 failed** · lint **0 errors, 0 warnings** in app
sources · debug and release builds green. No emulator, no previews.

---

## 1. Home structure — four sections, each hidden when empty

| Section | Holds | Row actions |
|---|---|---|
| **اليوم** | everything due today: waiting, postponed, still to come | completion ring · «تخطي اليوم» in a menu (repeating only) |
| **قادم** | later days | none — an occurrence that has not arrived cannot be answered |
| **انتهت اليوم** | today's completed **and** skipped occurrences, collapsed | تراجع |
| **متوقفة مؤقتًا** | paused reminders | استئناف |

Every section renders only when it has rows. When reminders exist but the day is
clear, a single quiet line replaces the list: «لا شيء اليوم». When nothing exists
at all, the bell empty state.

**The previous report said "six sections became three". That was wrong — the
implementation has four, and this table is the correction.**

### The label «انتهت اليوم»

It was reconsidered and kept. The section holds two different outcomes, so the
heading has to be the one thing they share — that رَنّة is done asking about them
today — and «انتهت اليوم» says exactly that in two words. Splitting it into
«مكتملة» and «متخطّاة» would put two near-empty headings on a screen whose whole
point is that finished work stays quiet.

The distinction moved to where it belongs, the row, and is carried three ways at
once: **the state word leads the row's second line** («مكتمل» / «تم تخطيه»), the
mark differs (check / skip arrow), and the mark's fill differs (solid teal /
plain). The rows are also physically quieter than live ones — 24 dp mark, small
type, tighter padding, collapsed by default.

## 2. Next occurrence — one formatter, no fixed phrases

`BalFormats.dateTime()` is now the single formatter for "when", used by the home
rows, the closed rows, the details status lines, the history and the widget. It
takes the instant that was actually scheduled and picks the shortest form that is
still exact:

| Case | Form |
|---|---|
| today | «اليوم، ٩:٠٠ صباحًا» |
| tomorrow | «غدًا، ٦:٠٠ صباحًا» |
| within the coming week | «الأحد، ٦:٠٠ صباحًا» |
| this year | «١ أغسطس، ٨:٠٠ صباحًا» |
| another year | «١ أغسطس ٢٠٢٧، ٨:٠٠ صباحًا» |

The weekday form is used **only** inside the next seven days, where «الأحد» can
mean one Sunday and no other; anything past or further out is named outright. The
old «يعود غدًا» phrasing is gone, and with it the class of bug where a snooze, a
skip or a timezone change left a relative day that was no longer true. The
formatter takes `now` from the screen's minute tick, so a row that is open across
midnight re-reads correctly.

Rows say «القادمة غدًا، ٦:٠٠ صباحًا»; details says «الرنّة القادمة: …». Same
phrase, shortened where the section header already supplies the context.

## 3. Row density — one visible action

The completion ring is the row's only visible action, and the row body opens
details. «تخطي اليوم» moved into a standard "more" menu on the trailing edge:
48 dp target, ordinary ⋮ affordance, and the action **names itself in full**
inside the menu rather than hiding behind an invented glyph. Completion and skip
no longer read as equals: one is a teal ring on the leading edge, the other is a
grey menu entry.

The menu appears only where the action is legal — a repeating reminder whose
occurrence belongs to today and is still open. Nothing destructive is reachable
from any row.

## 4. «تخطي اليوم» — the exact contract

`ReminderScheduler.skipOccurrence(id, occurrenceAt)`:

- **Appears** only for a recurring reminder with today's occurrence unanswered —
  before its time or after it, snoozed or not. Never for one-time reminders
  (refused in the domain as well as hidden in the UI), never for paused ones,
  never in «قادم».
- **Records** exactly one `SKIPPED` occurrence record, under the persisted
  occurrence identity (so an occurrence postponed by «تأجيل» is still the 6:00
  one, not the 6:10 one).
- **Cancels** the live snooze and any ringing alarm or notification for that
  occurrence, then schedules the next valid occurrence exactly once — the alarm
  is keyed by reminder id, so registering the next one replaces the old.
- **Never** pauses, edits or deletes anything.
- **Cannot run twice**: the unique `(reminder, occurrence, status)` index makes a
  replayed tap return null and change nothing.
- **Undoable** the same day, from the row or from the details screen; undo
  removes the record and re-derives the alarm, restoring today's occurrence when
  it has not yet passed.

## 5. Alarm — untouched

Two answers, تأجيل and تم, and «تم» still opens the deliberate slide. «تخطي
اليوم» was **not** added: a third button on a full-screen alarm at six in the
morning buys clarity nowhere and costs mis-taps where they hurt most. Leaving the
screen, dismissing the notification or stopping the sound still never counts as
completion.

## 6. Details — two scopes, only valid actions

**اليوم** — «تم» and, for repeating reminders, «تخطي اليوم». Once today has an
answer, both are replaced by a panel that states it («اكتمل اليوم» / «تم تخطيه
اليوم») with a button that names what it reverses: «تراجع عن الإنجاز» or «تراجع
عن التخطي».

**التذكير كله** — تعديل · إيقاف مؤقت/استئناف, then the history, then, alone at
the bottom past a gap, deletion. A repeating reminder's button reads «حذف
التذكير المتكرر» and asks: «سيُحذف «X» بكل رنّاته القادمة. إن أردت إيقافه لفترة
فقط، استخدم الإيقاف المؤقت.» A one-time reminder reads «حذف التذكير», deletes at
once with «تراجع» behind it, and never sees recurring language. A completed
one-time reminder shows neither zone's management actions — only «تراجع» and
delete. Deletion exists in exactly one place; the editor has none.

## 7. One-time lifecycle

Upcoming → due (waiting, ring available) → completed: the reminder is marked
done, its alarm cancelled, and it sits under «انتهت اليوم» as «مكتمل · ٩:٠٠
صباحًا», undoable. When the local day changes, the reminder and its records are
deleted in one transaction. It never offers «تخطي اليوم», never becomes paused by
completing, and never leaves an orphan record: cleanup runs at process start, on
the daily reconcile, **and** on the minute tick when the date changes, so an app
left open across midnight cleans up too.

## 8. Recurring lifecycle

Completing or skipping today closes that occurrence only. The reminder stays
enabled, the next occurrence is computed and scheduled immediately, and the row
says so on the spot: «مكتمل · القادمة غدًا، ٦:٠٠ صباحًا». Nothing has to be
resumed by hand — resuming exists only for «إيقاف مؤقت», which is a separate,
explicit act. A closed row has no navigation at all, so a checkmark can never
become a path into deleting a series.

## 9. Refinements in this pass

- One state vocabulary everywhere: actions are verbs («تم», «تخطي اليوم»,
  «تراجع»), states are adjectives («مكتمل», «تم تخطيه», «تم تجاهله», «متوقف
  مؤقتًا»). The history and the closed rows now share one set of labels.
- `Google` in the privacy line is wrapped in bidi isolates; the version number
  already was. Every time, date and number is Arabic-Indic.
- Closed rows: smaller mark, tighter padding, `labelLarge` undo, contrast checked
  in both themes.
- 48 dp minimum on every target (ring, menu, undo, resume) with smaller visuals
  inside them.
- Chips space on both axes; RTL chevrons point the way the next screen arrives;
  long titles wrap; buttons use `heightIn` so 200 % text grows them instead of
  clipping.

## 10. Removed

- The user-facing toolchain credit («مبنيّة على خط Tajawal وأدوات مفتوحة
  المصدر») — implementation detail nobody opened About to read. The licence texts
  still ship in the APK's assets, which is what the licences actually require.
- The row's «تخطي» text button, replaced by the menu.
- Three strings folded into one state vocabulary (`state_completed_at`,
  `state_skipped_today`, `state_returns_at` → `next_short` + the status labels).
- `OccurrenceStatus.IGNORED` is now documented as legacy-read-only: nothing has
  written it since the follow-up feature was removed, but old databases hold such
  rows and they stay readable rather than being re-read as something else.

## 11. The logo

The supplied concept (`design/logo/rannah-logo-concept.png`) replaced the drawn
bell. It was **traced from the artwork**, not re-approximated: the cream and red
regions were isolated, vectorised, and normalised onto the app's 0..24 grid, so
the shipped icon is the concept — the tilt, the notch carved out of the right
shoulder, the free-hanging clapper and the two asymmetric arcs — rather than a
hand-drawn lookalike.

The result is two path strings, `PATH_BELL` and `PATH_RING`, held once in
`Components.kt` and copied verbatim into the three vectors. Every surface wears
the same geometry: launcher, splash, status-bar glyph, home header, editor
summary, empty state, welcome and About. The notch is carved out of the bell path
itself, so the gap between bell and sound survives even the one-colour
notification mask.

Colour: the tile is the concept's ink `#151436`, the bell its cream `#FEF9EE`,
the arcs its red `#FE5A5F`. The logo keeps its own palette and does not become
the UI's — teal still means "act", brass still means "accent", and red stays out
of the interface, where it would collide with the destructive colour. The one
constant across surfaces is the red ring: inline, the bell takes the surface's
ink or cream and the arcs stay brand red. «عن رَنّة» and the welcome screen show
the tile itself, so the icon on the home screen and the mark inside the app are
visibly one object.

## 12. Data integrity

Room stays at **v5** — skipping needed no schema change, because `SKIPPED` was
already part of the status column. Reviewed and unchanged: snoozed-occurrence
identity, alarm cancellation, next-trigger scheduling, duplicate-intent
idempotency, transaction boundaries (delete-with-records, restore, prune), 180-day
record retention on both the record and occurrence axes, midnight cleanup, and
`ReconcileWorker`. No archive, trash screen or history feature was added.

## 13. Build

- `:app:testDebugUnitTest` — **124 tests, 0 failures**.
- `:app:lintDebug` — **0 errors, 0 warnings** in app sources.
- `:app:assembleDebug` · `:app:assembleRelease` — **BUILD SUCCESSFUL**.
- APK: `~/Desktop/rannah.apk` and `~/Downloads/rannah.apk`, identical copies,
  19,074,638 bytes,
  SHA-256 `6650f33fadc057e48cd8dec343f3117e73c78e271229214d955cc39ea9e55900`.
- `com.bal.reminders` · 1.0.0 (versionCode 1) · debug build, signed with the
  Android debug keystore, APK Signature Scheme **v2 verified**. The release build
  has no `signingConfigs` and is therefore unsigned and not installable.

## 14. Remaining runtime-only risks

- Layout at 200 % font scale, RTL mirroring of the swipe reveal, and the new row
  density were designed and reasoned about, not observed on a device.
- The undo offer is in-memory by design: a process death or an activity
  recreation while the snackbar is showing loses it, and the action stands.
- The v5 migration has still not been run against a real pre-v5 device database.
- OEM battery managers, full-screen-intent behaviour over the lock screen and DND
  interaction remain device-dependent.
- A dropdown menu anchored to a row near the screen edge relies on Compose's own
  placement; it was not observed on a narrow device.
