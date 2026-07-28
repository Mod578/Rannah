# رَنّة 1.1.0 — Completion Report

Phase: the post-release product-truth, reminder-model, snooze, identity, UX and
release-quality pass driven by [`POST_RELEASE_PRODUCT_AUDIT.md`](POST_RELEASE_PRODUCT_AUDIT.md).

`versionName 1.1.0` · `versionCode 2` · `applicationId com.bal.reminders`

---

## 1. What this release is about

1.0.0 was well built and described itself inaccurately. Three of its claims were
not true of the code, one visible setting silently did nothing, one list could
hide a reminder it was about to ring for, and the logo shared no colour with the
product it belonged to.

1.1.0 does not add features on top of that. It makes the product say only what it
does, gives the reminder model a shape a person can hold in their head — **مرة
واحدة / يومي / متكرر** — and finishes the one piece of flexibility that was
missing: a postponement you can actually choose.

The app is smaller in concept than it was, not larger.

---

## 2. Audit findings verified, and what happened to each

Every finding was re-checked against the code before acting. Two were **rejected
as written** and solved differently; both are explained below.

| ID | Finding | Verified? | Outcome |
|---|---|---|---|
| C1 | «مدة التأجيل» changed nothing for existing reminders | Yes | **Fixed.** `Reminder.snoozeMinutes` deleted from the domain; the setting is read at snooze time |
| C2 | README advertised Hijri reminders that cannot be created | Yes | **Fixed.** Claim removed; Hijri restated as display-only |
| C3 | README advertised a snooze duration the user did not choose | Yes | **Fixed** twice over: the claim is corrected *and* the choice now exists |
| C4 | A reminder could read «مكتمل · القادمة اليوم» while hiding a ring | Yes | **Fixed.** A today occurrence is always listed; pinned by test |
| C5 | Overdue one-timers sat under «اليوم» with no date, forever | Yes | **Fixed.** New `OVERDUE` phase, «متأخرة» section, real date |
| C6 | Blocked alarm channel reported with no fix path | Yes | **Fixed.** The card exists and calls the intent that had no caller |
| C7 | One occurrence could be both completed and skipped | Yes (race-only) | **Fixed** transactionally — see §7 for why *not* with an index |
| C8 | Settings said «٥ دقيقة»; notifications said «٥ دقائق» | Yes | **Fixed.** One plurals resource for both |
| 5.1 | A long snooze would swallow the next occurrence | Yes (latent) | **Fixed before it could ship** — the cap is now load-bearing |
| 5.2 | An unanswered recurring ring vanished at midnight | Yes | **Fixed.** A ring that times out is recorded as missed |
| 5.3 | Snooze could race stale snooze-cleanup | Yes | **Fixed.** The snooze is cleared before the surface appears |
| 5.4 | `complete()` had a dangerous nullable-occurrence default | Yes | **Fixed.** Occurrence is now required |
| 5.6 | Undo offers overwrite each other | Yes | **Not fixed.** Recoverable (the closed row keeps its own «تراجع»); left alone deliberately |
| §8 | «تخطي اليوم» behind an overflow menu of one item | Yes | **Fixed.** Labelled button, one tap |
| §17 | `SlideToConfirm` clipped at 200% | Yes | **Fixed**, plus previews at 100/150/200% and 320dp |
| §17 | Details action pair clipped at 200% | Yes | **Fixed.** Actions stack |
| §21 | Dead code | Partly | See §8 — some removed, some **kept on purpose** |

---

## 3. The reminder model

One question first: **ما نوع التذكير؟**

### مرة واحدة
A Gregorian date and time. Completing it finishes it — it moves to «انتهت اليوم»,
stays undoable for the rest of the local day, then is deleted with its records.
It never offers «تخطي اليوم» (there is no tomorrow to keep) and it no longer
offers «إيقاف مؤقت» either: a one-time reminder has a single date, and moving
that date is «تعديل». Pausing it created a state whose only exit was an unrelated
action.

If its moment passes unanswered it becomes **overdue**: it moves to «متأخرة»
above the day, shows the date it was actually due, and waits. It is never
silently deleted.

### يومي
A named preset over `Schedule.Daily`. Completing or skipping closes today's
occurrence only; tomorrow is scheduled immediately and the row says when.
«إيقاف مؤقت» stops the whole thing; «حذف» removes it and every future ring.

Selecting all seven weekdays under «متكرر» is normalised to `Daily` on save, so
"every day" has one representation and reads back as «يومي».

### متكرر
Weekly days, monthly, or yearly — the pattern is a second question asked only
here. Behaviour matches «يومي» exactly, because it is the same engine: one
`RecurrenceCalculator`, one `ReminderScheduler`, one `OccurrenceStateResolver`.
The kind is a **label**, not a code path, and `ReminderKindTest` pins that.

The next occurrence is always computed. There is no hardcoded «يعود غدًا»
anywhere: a Saturday-only reminder completed on Saturday says «القادمة السبت».

---

## 4. The two scopes

| هذه الرنّة فقط | التذكير كله |
|---|---|
| تم · تخطي اليوم · تأجيل · تغيير وقت التأجيل · إلغاء التأجيل · تراجع | تعديل · إيقاف مؤقت · استئناف · حذف |

Kept apart by layout, by heading, and by wording. Undo messages name the scope
out loud — «تم تخطي «الدواء» لليوم فقط»، «حُذف «الدواء» بكل رنّاته» — because the
one thing a person needs certainty about is whether they just ended a series.

A today-action cannot pause, edit or delete a reminder; `ReminderLifecycleTest`
asserts the series survives every occurrence action.

---

## 5. The snooze model

**One global default, one temporary override, one hard safety rule.**

- **`مدة التأجيل الافتراضية`** — the only persisted setting. Read at the moment
  «تأجيل» is pressed, through `SnoozeDefaultProvider`, so changing it changes
  every existing reminder immediately. `Reminder.snoozeMinutes` is gone from the
  domain; the column stays and is written as the constant default.
- **The alarm screen keeps two answers**: «تأجيل ١٠ دقائق» (large, reading the
  live setting) and «تم» (still a deliberate slide). Beneath them sits one quiet
  **«مدة أخرى»** — an ordinary focusable `TextButton`, *not* a long-press, which
  TalkBack and switch access cannot reach and nobody discovers.
- **The sheet** offers ٥ / ١٥ / ٣٠ / ٦٠ دقيقة and «حتى وقت محدد». The choice
  applies to **this occurrence only** and is never remembered — a duration
  silently inherited from a decision made at 3am is worse than no flexibility,
  because the button's label stops being true.
- **«حتى وقت محدد»** resolves the instant and spells it out before confirming:
  «سيرنّ اليوم، ٨:٣٠ مساءً» or «سيرنّ غدًا، ٦:٠٠ صباحًا». Crossing midnight is
  stated, never inferred.
- **The cap.** A reminder has one alarm and one trigger, so a postponement past
  its own next occurrence would swallow that occurrence. The maximum is
  `min(12 hours, next natural occurrence − 1 minute)`. An **explicit** choice past
  it is refused with the reason and the latest possible time; only the plain
  default button is clamped, and by at most a moment.
- **In details**, a live postponement can be moved («تغيير وقت التأجيل») or
  withdrawn («إلغاء التأجيل» — the occurrence returns unresolved, neither
  completed nor skipped nor paused).
- Occurrence identity survives every postponement, as it did before.

The notification's inline action is now just «تأجيل», with no duration printed.
A notification is built once and can sit on a lock screen while the setting
changes; a number baked into it would become a promise the button no longer keeps.

---

## 6. Identity

The old mark was a notification bell plus two wi-fi arcs, in indigo `#151436`
and coral `#FE5A5F` — a palette that appeared on no screen in the app, as
`Theme.kt` stated outright. The arcs were 2.5% of the icon wide and were the
first thing to vanish at 24dp, at 48dp, and under a themed-icon tint. The clapper
floated free below the rim. A notch was cut out of the bell's shoulder to make
room for the arcs.

**The new mark is one closed silhouette**: a bell leaning 12°, its clapper
lagging the swing the other way. Ringing is carried by **posture**, which
survives 16dp, one flat tint, and a notification mask. The crown and the clapper
overlap the body, so there is nothing that can come apart and no second colour to
lose. No arcs, no notch, no detached parts.

The geometry was generated and verified numerically (bounds, optical centroid,
clearances), then rendered and reviewed at 16/24/48/512dp on light and dark
before being committed as one path string.

| Asset | What changed |
|---|---|
| Primary mark | Container-free, single colour; `docs/assets/rannah-mark-{teal,cream}.svg` |
| Launcher | Flat teal background, mark at 56dp inside the 66dp safe zone; **no tile drawn inside the icon** — the system mask decides the shape |
| Monochrome | Its **own** drawable, no longer pointing at the colour foreground |
| Notification | Same single shape, nothing to lose under the mask |
| Splash | Mark at 157dp of 288; background is now the app's own stone/coal, not indigo |
| In-app | `AppIconTile`'s 23% rounded square deleted; the mark stands alone |
| Widget | Day/night aware, like the rest of the app |

**Palette:** teal `#0B6B5F`, cream `#FEF9EE`, brass `#9A6B1E`, stone `#F4F1EA`,
coal `#1A1915`. The indigo and the coral are retired. The three different darks
(`#151436` / `#1A1915` / `#232219`) are collapsed into one dark system.

---

## 7. Two audit recommendations that were rejected

**A unique index for "one answer per occurrence" (audit P2.7).**
Room cannot express a partial unique index on an entity, and a full unique index
on `(reminderId, occurrenceAtMillis)` would forbid the `missed` row that
legitimately precedes a late answer. Creating one only inside a migration would
also desynchronise the schema Room validates against at runtime. The invariant is
enforced instead in `ReminderDao.insertTerminalRecord`, a single `@Transaction`
that reads, decides and writes — the persistence layer, without a schema risk
taken for a race that has never been observed. `MIGRATION_5_6` normalises any
contradictory pair that an older build could have written, and completion wins.

**Deleting migrations v1–v5 as unreachable (audit P2.1).**
The audit's reasoning was correct — `versionCode 1` means no shipped device can
hold an older database — and the instruction for this phase was explicit that
development installs and personal backups predate the public tag. They stay,
along with legacy Hijri decoding and the exported schemas. A migration that never
runs costs bytes; a missing one destroys data.

---

## 8. Removed, and deliberately kept

**Removed** (each verified to have zero reachable callers first):
per-reminder snooze plumbing · `ParseResult.Incomplete` / `Draft` / `MissingPart`
and the parser's partial-schedule branch · `resolveDateOnly` ·
`NotificationPresenter.canUseFullScreenIntent` (duplicate of `Permissions`) ·
`HijriDates.yearOf` · `HijriDates.supportedYears` · `DetailsState.todayOpen` ·
`PermissionsStatus.ready` · `TodayMenu` (the overflow wrapping one action) ·
`AppIconTile`'s tile · `BrandInk` / `BrandBell` / `BrandRing` · the duplicate RTL
placement helper · strings `action_more`, `editor_repeat`, `editor_snooze_option`,
`schedule_days_all`, `repeat_*` · the indigo and coral colours.

**Kept on purpose:** all six migrations and the exported schemas · legacy Hijri
schedule decoding · `OccurrenceStatus.IGNORED` (a v1–v5 database can contain
`'ignored'` rows; dropping the enum value would make them unreadable) · the
Arabic parser · release signing configuration · the licence notices.

One safety change while there: `OccurrenceStatus.fromId` used to fall back to
`COMPLETED` for an unrecognised value, so a row from a build this one does not
know about would be read as *the user asserting the task was done*. It now falls
back to `MISSED`. Guessing wrong towards "unanswered" costs an extra ring;
guessing wrong towards "done" loses the reminder.

---

## 9. Verification

| Check | Result |
|---|---|
| `:app:test` | **210 tests, all passing** (was 143) |
| `:app:lint` | **No issues found** |
| `:app:assembleDebug` | success |
| `:app:assembleRelease` | success — R8, resource shrinking, not debuggable |
| `:app:bundleRelease` | success |
| Signing | v2 + v3 verified, v1 disabled by design; same 4096-bit key as 1.0.0 |
| Migration ladder | v1→v6 walked with seeded data; result equals the exported v6 schema |

New test coverage: the global snooze setting reaching existing reminders · the
temporary override and its non-persistence · snooze until a time · the occurrence
cap and its refusal · cancel and change · completion and skip after a snooze ·
the full one-time / daily / recurring lifecycles · a completed occurrence
followed by another occurrence today · overdue dating and ordering · one terminal
answer per occurrence in both directions · a ring that times out · readiness
ordering and the alarm-channel issue · Arabic kind classification · the v6
migration.

**No emulator or connected-device task was run.**

---

## 10. Release

| | |
|---|---|
| applicationId | `com.bal.reminders` |
| versionName / versionCode | `1.1.0` / `2` |
| minSdk / targetSdk | 26 / 35 |
| APK | 2,146,378 bytes · `sha256 50dd63ef5a5a98d367f79fbd79f02b29ebb9284af79edf3bfae93244dc7c6ee7` |
| AAB | 4,478,743 bytes · `sha256 d831f15a380026432430d689ec87682bae7c35bb57694a4117e9cf263ad4c116` |
| Signer | `CN=Rannah, OU=Rannah, O=Mohammed Almutairi, L=Riyadh, C=SA` |
| Certificate SHA-256 | `70ff2a39e29485fbfb1f08bb917c55fada6bf4ae765d571813c0d4022e46dd50` |

Built from this tree with the Gradle build cache and configuration cache
disabled, so the artifacts are the product of a real full compile rather than a
restored one. APK/AAB packaging is not bit-reproducible (archive timestamps, R8,
signing salt), so a later rebuild will hash differently while containing the same
code.

Upgrading from 1.0.0 keeps all data: the schema gains no column, and
`MIGRATION_5_6` only normalises rows older builds could have written.

---

## 11. What still cannot be confirmed without a device

1. Full-screen intent behaviour on Android 14+ and on OEM skins.
2. Foreground-service start from a broadcast on aggressive OEMs (the fallback
   path exists but cannot be exercised here).
3. Whether `getQuantityString` renders Arabic-Indic digits on-device as ICU
   implies — the JVM and Android disagree, so no unit test can settle it.
4. The exact screen width at which `SlideToConfirm` begins to wrap; the previews
   show it holding at 320dp / 200%, but only a device settles rendering.
5. Doze and battery-manager behaviour over multi-day idle — exactly what
   `ReconcileWorker` defends against and exactly what cannot be simulated.
6. Perceived icon quality on a real home screen, in themed-icon mode, and in the
   status bar. The geometry is measured and was reviewed as rendered PNGs; the
   final judgement is visual.
7. That R8 preserved every reflective entry point — the rules are unchanged and
   reasoned, but only a signed release run on a device proves it.
