# رَنّة — Post-Release Product, UX, Logic, Architecture and Brand Audit

> **Status: acted on in 1.1.0.** This document is kept exactly as it was written
> against `v1.0.0` — it is the evidence, and evidence that gets edited after the
> fact is worth nothing. What was done about each finding, and which two
> recommendations were **rejected and solved differently**, is recorded in
> [`FINAL_COMPLETION_REPORT.md`](FINAL_COMPLETION_REPORT.md).
>
> Quick map of the headline findings:
>
> | Finding | 1.1.0 |
> |---|---|
> | C1 per-reminder snooze made the setting a lie | fixed — field deleted, setting read at snooze time |
> | C2 Hijri reminders advertised, not buildable | fixed — claim removed, Hijri is display-only |
> | C3 snooze duration advertised, not choosable | fixed — and the choice now exists |
> | C4 a ring hidden behind a completed record | fixed — a today occurrence is always listed |
> | C5 undated overdue one-timers under «اليوم» | fixed — `OVERDUE` phase and a «متأخرة» section |
> | C6 blocked alarm channel with no fix path | fixed — the card now calls the unused intent |
> | C7 an occurrence could be completed *and* skipped | fixed transactionally, **not** with an index (see report §7) |
> | C8 «٥ دقيقة» vs «٥ دقائق» | fixed — one plurals resource |
> | 5.1 a long snooze could swallow the next occurrence | fixed before it could ship — the cap is load-bearing |
> | 5.2 unanswered rings vanished at midnight | fixed — a timed-out ring is recorded as missed |
> | §16 identity | rebuilt — one shape, one palette, full asset set |
> | P2.1 delete migrations v1–v5 | **rejected** — kept for development installs and backups |

Scope: the repository at `master` / tag `v1.0.0` (`versionCode 1`, `versionName 1.0.0`).
Method: full read of source, Room schemas (`app/schemas/1..5.json`), migrations, unit
tests, git history, `README.md`, `docs/PRIVACY.md`, release configuration, the merged
release manifest, and every branding asset. No emulator, no runtime testing. Every
claim below is cited to a file and line and was checked against the implementation,
not against a comment or a commit message.

---

## 1. Executive verdict

**رَنّة is a well-built app with a clear idea, shipped with a product description
it does not fully honour, one invisible setting that lies to the user, and an
identity that belongs to a different product than the one it ships with.**

The engineering is genuinely above average for a solo v1. The lifecycle has one
owner (`ReminderScheduler`), one source of truth (the database), one place where
display state is derived (`OccurrenceStateResolver`), idempotent actions backed by
a real unique index, a migration ladder proven against exported schemas on the JVM,
and a privacy statement whose every sentence matches the merged manifest. That is
rare and it should not be undone.

Three things are wrong at the product level:

1. **The README sells two features the app does not have.** Hijri monthly/yearly
   reminders cannot be created (`EditorViewModel` is Gregorian-only), and «تأجيل بمدة
   تختارها أنت» is false — at ring time the user chooses nothing.
2. **`snoozeMinutes` is per-reminder, frozen at creation, and unreachable.** The
   settings control «مدة التأجيل» changes nothing for any reminder that already
   exists. This is the single clearest broken promise in the app.
3. **The logo is two generic symbols in a palette the app never uses.** A bell (the
   most-used glyph in software) plus wi-fi arcs, in indigo/coral, next to a product
   built in teal/brass/stone. The code says so out loud (`Theme.kt:117-118`).

Against the eight goals asked about:

| Goal | Verdict |
|---|---|
| Logically coherent | **Yes, with two contradictions** (§23) |
| Understandable without documentation | **Mostly** — «تخطي اليوم» and snooze are the weak points |
| Flexible without complexity | **Under-flexible on snooze**, correctly restrained elsewhere |
| Safe against accidental actions | **Yes** — the best-executed part of the app |
| Reliable across one-time and recurring | **Yes**, with one silent gap at midnight (§5.2) |
| Visually and linguistically consistent | **No** — three darks, two snooze grammars, two icon shapes |
| Maintainable | **Yes**, with ~15% provably dead code |
| Professionally branded | **No.** This is the weakest dimension of the product |

**Ship-blocking for a 1.1:** nothing crashes or loses data. Everything below is a
correctness-of-promise, clarity, or identity problem.

---

## 2. Current product definition

What the code actually implements:

> A single-list Arabic reminder app in which **every** reminder is a full-screen
> alarm. A reminder is one-time or recurring (daily / weekly / monthly / yearly,
> Gregorian). When it rings you may **postpone** it (one tap, a fixed duration) or
> **confirm it done** (a deliberate slide). Separately, and never from a ring, you may
> **skip today**, **pause**, **resume**, **edit**, or **delete** the reminder. Everything
> reversible is reversible.

That is a coherent, defensible product. The restraint is real and mostly earned:
there is no per-reminder ringtone, no alert style, no priority, no category, no
follow-up policy — all of these existed and were deliberately removed (`Mappers.kt:24-34`,
`BalDatabase.kt:95-181`). The app is better for it.

Two definitional problems:

- **`README.md:33` claims Hijri monthly and yearly reminders.** `EditorViewModel`
  builds only `Schedule.Once/Daily/Weekly/Monthly/Yearly` (`EditorViewModel.kt:53-59`);
  `ScheduleType` has no Hijri member (`EditorViewModel.kt:28`). The Hijri schedule
  types (`Schedule.OnceHijri/HijriMonthly/HijriYearly`) are constructed **only** in
  `Mappers.kt:44-62`, i.e. only when reading a pre-existing database row. Since
  `versionCode = 1` is the first and only release, **no such row exists on any device
  in the world.** The feature is unreachable, and the README advertises it in both
  Arabic and English (`README.md:33`, `README.md:156`).
- **`README.md:36` claims «تأجيل بمدة تختارها أنت».** See §9.

---

## 3. What is working well

These are load-bearing and must not be undone:

- **One lifecycle owner.** Every state change routes through `ReminderScheduler`
  (`ReminderScheduler.kt:39-329`). Alarms are always derivable from the database,
  which is precisely why reboot, process death, app update and clock change are safe.
- **Occurrence identity survives snooze.** `snoozedOccurrenceAt` (`Reminder.kt:27`,
  `ReminderScheduler.kt:95`, `:214-216`) means completing after a postponement resolves
  *the occurrence that rang*, not the postponement. This is subtle and correct, and
  it is covered by a test that actually exercises the sequence
  (`ReminderSchedulerTest.kt:516-538`).
- **Idempotency is real, not aspirational.** The unique index on
  `(reminderId, occurrenceAtMillis, status)` (`Entities.kt:63`) plus
  `OnConflictStrategy.IGNORE` (`ReminderDao.kt:64`) means replayed PendingIntents
  and double taps cannot double-log. `addRecord` returning `false` is what makes
  `complete`/`skipOccurrence` return `null` and do nothing (`ReminderScheduler.kt:123`, `:152`).
- **Completion is genuinely hard to do by accident.** `SlideToConfirm` on the alarm
  screen with a latch (`SlideToConfirm.kt:86`, `:157`), plus `resolving` in the view
  model (`AlarmViewModel.kt:43`, `:88-96`), plus a 50 %-of-width drag threshold on
  home rows (`Components.kt:316`). Three independent guards on the one irreversible-
  feeling act. This is the best-designed part of the product.
- **The ringer stops meaning nothing.** `AlarmRingerService.onTimeout` explicitly
  refuses to decide for the user (`AlarmRingerService.kt:197-200`); the occurrence just
  stays unresolved. The removal of `stopMarksCompleted` in `MIGRATION_3_4` and its
  written justification (`BalDatabase.kt:95-107`) is the single best product decision
  in the history.
- **Migrations are proven, not assumed.** `MigrationChainTest` walks v1→v5 with seeded
  data and asserts the end state equals the exported v5 schema
  (`MigrationChainTest.kt:148-159`), and asserts there is no gap in the ladder
  (`:35-45`). This is stronger than most commercial Android apps.
- **The privacy statement is true.** The merged release manifest contains exactly the
  ten permissions `docs/PRIVACY.md:38-48` lists, `INTERNET` is absent, and the
  `allowBackup` caveat is stated plainly instead of being hidden. Verified against
  `app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml`.
- **`setAlarmClock`, not `setExactAndAllowWhileIdle`.** (`AlarmGateway.kt:47-50`) The
  correct API for an app whose promise is "it rings"; it also surfaces in the system's
  next-alarm indicator with a working tap target.
- **Typography discipline.** `letterSpacing = 0.sp` on every style (`Theme.kt:181`) with
  the reason written down. Tracking Arabic apart is the most common way an Arabic UI
  looks subtly broken, and it is avoided deliberately.

---

## 4. Critical defects

### C1 — «مدة التأجيل» in settings does nothing for existing reminders
**Severity: high. Reachable by every user.**

`Reminder.snoozeMinutes` (`Reminder.kt:18`) is a per-reminder column. It is seeded from
settings **once**, at creation (`EditorViewModel.kt:86`, `:153`). When editing, it is
re-read from the reminder itself (`EditorViewModel.kt:107`) and written back unchanged
(`EditorViewModel.kt:253`). The editor exposes **no** control for it. `ReminderScheduler.snooze`
reads `reminder.snoozeMinutes` (`ReminderScheduler.kt:211`), and both production callers
pass `minutes = null` (`AlarmViewModel.kt:80`, `Receivers.kt:81`).

Consequence: a user who sets «مدة التأجيل» to ٣٠ sees every existing reminder still
say «تأجيل ١٠ دقائق». There is no UI anywhere that explains this, and no way to fix
it except deleting and recreating the reminder. The settings label says «مدة التأجيل»
— not "for new reminders" — so the screen is making a false statement.

### C2 — The README advertises Hijri reminders that cannot be created
**Severity: high. Public product claim.**
See §2. `README.md:33` and `README.md:156`.

### C3 — The README advertises a snooze duration the user does not choose
**Severity: high. Public product claim.**
`README.md:36`: «**تأجيل** بمدة تختارها أنت.» At ring time there is exactly one
button with one baked-in duration (`AlarmActivity.kt:282-292`). The only choice is a
global default that (per C1) does not reach existing reminders.

### C4 — A reminder completed today disappears from «اليوم» even when it will ring again today
**Severity: medium. Reachable: complete, then edit the time.**

`ChecklistViewModel.build` drops any `UPCOMING` reminder whose id appears in
`closedIds` (`ChecklistViewModel.kt:133-140`). `closedIds` is *"has any terminal record
recorded today"* (`:114-117`) — it is not tied to the occurrence.

Sequence: daily reminder at 09:00 → user completes it at 08:00 → user edits it to
20:00. The 20:00 occurrence is unresolved, an alarm is armed for it, but the row is
suppressed from «اليوم» and appears only under «انتهت اليوم» reading
«مكتمل · القادمة اليوم، ٨:٠٠ مساءً» — a row that says "completed" and "next: today"
at the same time, and whose only action is «تراجع». The app will ring for something
it is telling the user is finished.

### C5 — Overdue one-time reminders live under a heading called «اليوم», with no date
**Severity: medium. Reachable and permanent.**

A one-time reminder whose moment passed unresolved stays `NEEDS_CONFIRMATION` forever
(`OccurrenceStateResolver.kt:119-123`). `NEEDS_CONFIRMATION` is unconditionally routed
into `todayList` (`ChecklistViewModel.kt:132`), and its second line renders as
`state_waiting_at` = «٩:٠٠ صباحًا · ينتظر تأكيدك» — **time only, no date**
(`ChecklistScreen.kt:328`, `strings.xml:78`).

So a reminder from three weeks ago sits at the top of a section titled "Today",
claiming a time that is not today's. There is no pruning path for it (`pruneCompletedOnceBefore`
only removes *completed* one-timers, `ReminderDao.kt:111-137`), so the list accumulates
permanently. This is the answer to "can a reminder be permanently retained": yes, and
it is also mislabelled while it sits there.

### C6 — The alarm channel can be blocked with no way to fix it
**Severity: medium.**

`ReadinessIssue.ALARM_CHANNEL` is declared `blocking = true` (`Readiness.kt:31-35`) and
surfaces as a red line in the readiness summary (`PermissionsScreen.kt:284-293`) and as
the home banner (`ChecklistScreen.kt:132`, `:174-176`). But `PermissionsScreen` has **no
card** for it — the four cards are notifications, exact alarms, full-screen, battery
(`PermissionsScreen.kt:105-168`). `Permissions.channelSettingsIntent` exists and has
**zero call sites** (`Permissions.kt:57`).

Result: the app tells the user «الرنّات صامتة» in red, links them to a screen, and
that screen offers nothing that fixes it.

### C7 — The database cannot represent "one answer per occurrence"
**Severity: medium (latent). Race-reachable.**

The unique index is `(reminderId, occurrenceAtMillis, status)` (`Entities.kt:63`). A
`COMPLETED` and a `SKIPPED` record for the *same* occurrence are both legal. Nothing in
`ReminderScheduler.complete` or `.skipOccurrence` checks for the other status
(`ReminderScheduler.kt:115-160`). If both ever exist, «انتهت اليوم» shows two rows for
one occurrence (`ChecklistViewModel.kt:144-156`), the details screen shows only whichever
sorts first (`DetailsViewModel.kt:84-87`), and undoing one leaves the other.

Reaching it requires racing the swipe against the overflow menu on the same row
(`ChecklistScreen.kt:190`, `:194`), which is unlikely but not impossible. The point is
that the invariant is not expressed anywhere, so it is one careless future feature
away from being routine.

### C8 — Settings and notifications disagree about Arabic grammar for the same number
**Severity: low, but it is on a settings screen.**

`SettingsScreen.kt:115-123` formats snooze chips with `editor_snooze_option` =
«%1$s دقيقة» (`strings.xml:106`) → «٥ دقيقة», «١٠ دقيقة». Both are wrong: 3–10
takes «دقائق». The notification and alarm screen do it correctly through
`R.plurals.notification_snooze_minutes` (`strings.xml:214-221`), whose CLDR categories
are right (`few` for 3–10, `many` for 11–99). Two surfaces, same number, two grammars.

---

## 5. High-risk hidden edge cases

**5.1 — A custom snooze longer than the gap to the next occurrence would silently eat it.**
There is exactly one alarm slot per reminder (request code = reminder id,
`AlarmGateway.kt:73-78`) and one `nextTriggerAt`. `snooze` overwrites both
(`ReminderScheduler.kt:217-219`). Today this is harmless because the maximum offered
snooze is 30 minutes. **It becomes a real defect the moment a custom duration or a
"snooze until a time" feature ships.** This is a hard constraint on §10, not a
theoretical worry.

**5.2 — An unanswered recurring occurrence vanishes at midnight, unrecorded.**
`onAlarmFired` advances `nextTriggerAt` to tomorrow immediately (`ReminderScheduler.kt:100-107`).
The ringer times out after 3 minutes and records nothing (`AlarmRingerService.kt:197-200`).
`rescheduleAll` only writes `MISSED` when `nextTriggerAt` is in the past beyond the grace
window (`ReminderScheduler.kt:280-288`) — which it never is after a normal fire. And the
resolver recomputes "today's occurrence" from `startOfToday` (`OccurrenceStateResolver.kt:102-104`),
so yesterday's unanswered ring is simply gone at 00:00.

Consequence: `OccurrenceStatus.MISSED` is only ever written when the *device was off*.
An occurrence the user heard and ignored leaves **no trace at all** — not in the row,
not in the history, not in the widget. The «فات موعده» state exists in the code
(`strings.xml:137`) and is nearly unreachable in normal use.

**5.3 — Race between `startAlarm` and clearing the snooze.**
`onAlarmFired` calls `notifications.startAlarm` *before* `setSnooze(null, null)`
(`ReminderScheduler.kt:96-99`). `snooze` returns early when `occurrenceAt != null &&
snoozedUntil != null` (`ReminderScheduler.kt:210`). A user who taps «تأجيل» in the
milliseconds between the two writes gets a silent no-op and a closed screen. Narrow,
but the fix is a one-line reorder.

**5.4 — `complete(id)` with a null occurrence resolves the wrong occurrence.**
`complete` falls back to `reminder.nextTriggerAt` (`ReminderScheduler.kt:118-121`), which
for a recurring reminder *after it has fired* is **tomorrow**. No production caller does
this (all three pass an explicit occurrence: `ChecklistViewModel.kt:178`,
`DetailsViewModel.kt:110`, `AlarmViewModel.kt:85`), but the default parameter is a live
footgun in the one API everything else routes through.

**5.5 — `Schedule.Weekly` with an empty day set is a permanently silent, unanswerable row.**
`nextOccurrence` returns `null` for empty days (`RecurrenceCalculator.kt:54`), the resolver
falls through to `UPCOMING` with a null instant (`OccurrenceStateResolver.kt:124`), and
`metaFor` renders an empty string (`ChecklistScreen.kt:323`). The editor blocks it
(`EditorViewModel.kt:220-223`) and no legacy database exists, so it is currently
unreachable — but there is no guard in the domain, only in one screen.

**5.6 — Undo offers overwrite each other.**
`UndoCoordinator.offer` assigns unconditionally (`UndoCoordinator.kt:45-47`) while the
home collector is suspended inside `showSnackbar` (`ChecklistScreen.kt:108-119`).
Completing two rows in quick succession drops the first undo silently. The user has a
second path (the «انتهت اليوم» row's own «تراجع»), so this is recoverable, not lost.

**5.7 — The home's resolved-set is capped at 500 records.**
`observeRecords` is `LIMIT 500` (`ReminderDao.kt:57`) and drives both `resolved` and
`closedRecords` (`ChecklistViewModel.kt:107-117`). With 180-day retention
(`ReminderScheduler.kt:327`) and ~5 daily reminders, the cap is reached in ~100 days.
Today's records are always newest so correctness holds — but the invariant "the newest
500 always contain everything relevant to today" is undocumented and unguarded.

**5.8 — Manual clock changes silently drop a live snooze.**
`ACTION_TIME_CHANGED` → `rescheduleAll(fireMissed = false)` (`Receivers.kt:112-118`) →
`staleSnooze` clears a `snoozedUntil` now in the past (`ReminderScheduler.kt:291-294`)
without re-surfacing the occurrence. Rare; worth one line of handling.

---

## 6. One-time reminder analysis

**Correct:**
- Created with a past-time guard (`EditorViewModel.kt:228-233`).
- Completing sets `completedAt`, cancels the alarm, dismisses the surface
  (`ReminderScheduler.kt:124-127`).
- Shown for the rest of its day under «انتهت اليوم», then pruned with its records the
  next day, transactionally and idempotently (`ReminderDao.kt:129-137`, tested at
  `ReminderSchedulerTest.kt:403-420`).
- Cannot be skipped — `skipOccurrence` refuses (`ReminderScheduler.kt:150`) and the UI
  never offers it (`ChecklistScreen.kt:194`, `DetailsScreen.kt:189`). Correct: skipping a
  one-timer is deletion wearing another word.
- Missed while the device was off rings late within a 24 h grace (`ReminderScheduler.kt:283`).

**Wrong:** the overdue state (C5). It is permanent, undated, and filed under "Today".

**Missing:** nothing. Resist adding a "snooze to tomorrow" for one-timers; editing the
date is the honest action and it already exists.

---

## 7. Recurring reminder analysis

**Correct:**
- Firing advances the schedule strictly past the trigger that fired, which defends
  against AlarmManager delivering a few milliseconds early (`ReminderScheduler.kt:100-105`).
- `expectedOccurrence` rejects a broadcast left over from an edit (`:91`), tested
  (`ReminderSchedulerTest.kt:244-257`).
- Pausing has **exactly one** representation (`enabled = false`); the old second
  representation was migrated away and the resolver still reads a legacy one correctly
  (`OccurrenceStateResolver.kt:78-89`, `BalDatabase.kt:172-181`).
- Deleting a recurring reminder is behind a dialog that names the alternative
  («إن أردت إيقافه لفترة فقط، استخدم الإيقاف المؤقت», `strings.xml:117`); deleting a
  one-timer is immediate with undo (`DetailsScreen.kt:263`). Correctly asymmetric.
- Completing early ("done already") skips the completed occurrence rather than
  double-firing (`ReminderScheduler.kt:129-133`).

**Wrong:**
- C4 (hidden after completion + edit).
- 5.2 (unanswered occurrences vanish at midnight with no record).
- The `IGNORED` status is dead. Nothing writes it (`OccurrenceStatus.kt:16-24` says so),
  yet `labelRes`/`icon`/`color` still branch on it (`OccurrenceStatusUi.kt:25`, `:33`, `:47`)
  and `status_ignored` still ships (`strings.xml:136`).

**Cannot happen (verified):** a recurring series cannot be ended by an occurrence
action. `complete` never touches `enabled` and never sets `completedAt` for a recurring
reminder (`ReminderScheduler.kt:124-134`); `skipOccurrence` touches only the record and
the next trigger (`:148-160`); the closed row deliberately offers no route into the
reminder (`Components.kt:426-434`). This was clearly designed for and it holds.

---

## 8. Today-only action analysis («تخطي اليوم»)

**Semantics are right.** One occurrence, no series effect, undoable, recurring-only,
and it silences a live ringer on the way out (`ReminderScheduler.kt:158`, tested
`ReminderSchedulerTest.kt:362-374`).

**Discoverability is the weak point, and the control is the wrong one.** On home it
lives behind a `MoreVert` overflow whose menu contains **exactly one item**
(`Components.kt:506-536`). A menu of one is the classic sign that a menu is the wrong
control: it costs two taps and one guess for one action.

The app already has the right pattern in the same slot — paused rows carry a labelled
`TextButton` («استئناف», `ChecklistScreen.kt:364-377`). Making «تخطي» a labelled
`TextButton` in the trailing slot would be more discoverable, one tap instead of two,
and *more* internally consistent, at no cost in visual weight.

On the details screen it is already correct: a full-width outlined button beside «تم»,
with a one-line note explaining the scope (`DetailsScreen.kt:189-205`, `strings.xml:124`).

---

## 9. Snooze and custom-duration analysis

### 9.1 What exists today

| Fact | Evidence |
|---|---|
| Duration is a per-reminder column | `Reminder.kt:18`, `Entities.kt:36` |
| Seeded from settings **once**, at creation | `EditorViewModel.kt:86`, `:153` |
| Never editable afterwards — no UI at all | `EditorScreen.kt` (no control); `EditorViewModel.kt:253` writes it back unchanged |
| Global setting offers 5/10/15/30 | `Reminder.kt:45`, `SettingsScreen.kt:115` |
| Repository accepts 1..120 (unused range) | `SettingsRepository.kt:43-45` |
| Alarm screen: one button, baked-in duration | `AlarmActivity.kt:282-292` |
| Notification: one action, same duration | `NotificationPresenter.kt:102-106` |
| Both production callers pass `minutes = null` | `AlarmViewModel.kt:80`, `Receivers.kt:81` |
| Repeated snoozes allowed, unbounded | `ReminderScheduler.kt:204-221` |
| Occurrence identity preserved across snoozes | `ReminderScheduler.kt:214-216` — **correct, keep** |
| No way to cancel or edit a snooze afterwards | `DetailsScreen.kt:161-207` offers only تم / تخطي |

So: the duration is *global in practice, per-reminder in storage, and unreachable in
both.* Three models' worth of machinery producing zero user choice.

### 9.2 Options, judged

| Option | Verdict |
|---|---|
| Quick chips 5/10/15/30/60 **on the alarm screen** | **Reject as primary.** Five targets on the one screen read half-asleep in the dark destroys the current hierarchy (one big safe button). The alarm screen's job is to be answerable without thinking. |
| Custom number entry on the alarm screen | **Reject.** Typing a number while a bell rings. |
| Snooze until a specific time as the *default* interaction | **Reject as primary, accept as secondary.** Semantically the strongest ("after the meeting", "after Maghrib") but needs a time picker at ring time. |
| Remember the last used duration | **Reject.** Invisible state. The button label would change between rings for reasons the user cannot see, which makes the label untrustworthy — the opposite of what an alarm needs. |
| Global default + temporary per-ring override | **Accept.** This is the right shape. |
| Per-reminder snooze configuration | **Reject and delete.** It already exists, invisibly, and it is the direct cause of C1. Two reminders that ring identically should postpone identically; a "how long does this one snooze" question in the editor is a fourth question in a deliberately three-question editor. |
| Editing the snooze after postponing | **Accept.** Cheapest, safest flexibility: the user is awake, in the app, in daylight, with a full screen. |

**Duration or target time?** Duration for the ring; target time as an option inside the
override sheet. "10 more minutes" is what a person means at 6am; "at 2pm" is what they
mean when they are awake and looking at a list. Both, in the right place.

**Global, per-reminder, or temporary?** Global default (persistent, one place),
temporary override (this occurrence only, never persisted). Not per-reminder.

**Is custom snooze worth adding?** Yes — but as a *secondary* affordance, and only
alongside deleting the per-reminder field. Adding custom snooze on top of the current
model would make C1 worse, not better.

### 9.3 Interactions that must hold

- **«تخطي اليوم» while snoozed** — already correct: skipping clears the snooze and
  reschedules (`ReminderScheduler.kt:153-157`, tested `ReminderSchedulerTest.kt:386-399`).
- **Completion while snoozed** — already correct: resolves the occurrence that rang
  (`ReminderScheduler.kt:119`, tested `:516-538`).
- **Pause while snoozed** — already correct: the snooze is dropped so resuming never
  restores a stale instant (`ReminderScheduler.kt:263-264`, tested `:474-487`).
- **Repeated snoozes** — correct; each resets from `clock.instant()`.
- **Duplicate notification intents** — correct; guarded (`ReminderScheduler.kt:210`).
- **Reboot / process death** — correct; a future snooze survives `rescheduleAll`
  (tested `:650-657`), a stale one is cleared (`:291-294`).
- **Timezone / DST** — correct by construction: a snooze is an absolute instant, so it
  cannot be moved by a wall-clock change.
- **Midnight crossing** — correct today (a 30-minute snooze at 23:50 fires at 00:20 for
  yesterday's occurrence, and `scheduleNext` then advances normally). **A "snooze until
  06:00" feature must state explicitly, in the sheet, that it means tomorrow.**
- **Duplicate scheduling** — impossible: one PendingIntent per reminder id
  (`AlarmGateway.kt:73-78`).
- **The hard constraint** — see 5.1: a snooze must never exceed the gap to the next
  natural occurrence.
- **Accessibility** — the current single button is a proper `Button` with a text label;
  any override affordance must be a real focusable control, **not** a long-press.
- **RTL** — unaffected; both existing controls are layout-direction-agnostic.

**Does flexibility create cognitive load?** Only if it is put on the alarm screen. The
proposal below adds exactly one subordinate text button there and puts everything else
one layer down.

---

## 10. Recommended final snooze model

**"One tap by default, one more tap when you need it, nothing to remember."**

1. **Delete `Reminder.snoozeMinutes` from the domain.** Keep the column (write the
   default; the schema already tolerates it, `Mappers.kt:120`), remove it from
   `Reminder`, `EditorState` and `EditorViewModel`. `ReminderScheduler.snooze` reads the
   **global** default at snooze time. This single change makes «مدة التأجيل» true and
   removes one entire class of invisible per-row state. **Do this first; everything else
   depends on it.**

2. **Alarm screen keeps its shape.** Primary button «تأجيل ١٠ دقائق» — unchanged, one
   tap, largest thing on screen. «تم» — unchanged, reveal + slide.

3. **Add one quiet secondary text button beneath it: «مدة أخرى».** Not a long-press
   (undiscoverable, and hostile to TalkBack and switch access). A real `TextButton`,
   clearly subordinate, `heightIn(min = 48.dp)`, matching `action_back` in the confirm
   state so the layout already accommodates it.

4. **It opens a bottom sheet with five choices and nothing else:**
   `٥ · ١٥ · ٣٠ · ٦٠ دقيقة` as `ChoiceChips` (the component already wraps and survives
   200 % text, `ChoiceChips.kt:27-51`), plus one row: «حتى وقت محدد…».

5. **The override is temporary and never persisted.** It postpones this occurrence
   only. The next ring shows the default again. The button label must always tell the
   truth without the user recalling a 3am decision.

6. **«حتى وقت محدد»** opens the existing `TimePicker` seeded at `now + 1h` rounded to
   5 minutes, with a live line beneath it reading the resolved instant through
   `BalFormats.dateTime` — so «٦:٠٠ صباحًا — غدًا» is *stated*, never inferred.
   Midnight crossing is thereby explicit by construction.

7. **Bounds: minimum 1 minute; maximum `min(12 hours, time until the next natural
   occurrence − 1 minute)`.** The second half is not optional — it is the constraint
   from 5.1. When the cap bites, say so in one line rather than silently clamping.

8. **Snoozes become editable and cancellable from details.** In the «اليوم» zone, when
   `phase == SNOOZED`, show two quiet actions beside the existing status line
   («مؤجل حتى …», `DetailsScreen.kt:436-437`): «تغيير وقت التأجيل» and «إلغاء التأجيل»
   (the latter clears the snooze and returns the occurrence to «ينتظر تأكيدك»). This is
   where flexibility belongs.

9. **Fix C8 while touching this**: replace `editor_snooze_option` with the existing
   plurals resource in the settings chips.

**Total scope:** one bottom sheet, one secondary button, two details actions, one field
deleted, one string deleted. Net complexity in the domain goes *down*.

**Explicitly not recommended:** per-reminder snooze in the editor; remembering the last
used duration; snooze buttons in the notification beyond the existing single action;
any snooze on the home row.

---

## 11. Home and navigation audit

Sections: «اليوم» / «قادم» / «انتهت اليوم» (collapsed) / «متوقفة مؤقتًا»
(`ChecklistScreen.kt:178-257`).

**This is close to the simplest possible structure and should not be reduced further.**
Each section answers a different question, none is decorative, and «انتهت اليوم» is
collapsed by default so a finished day does not crowd a live one.

Sorting is right: waiting items above merely-scheduled ones, clock order within each
(`ChecklistViewModel.kt:161-163`).

Issues:
- C4 and C5 both land here.
- The row's second line is written in one place (`ChecklistScreen.kt:317-336`) — good —
  but `closedMeta` builds its separator by string concatenation outside `strings.xml`
  (`:360`). Minor.
- The overflow-menu-of-one (§8).
- The header's brand lockup (`ChecklistScreen.kt:435-445`) uses `AppMark` at 24 dp with
  `body = primary` (teal) and `ring = BrandRing` (coral). Teal + coral appears nowhere
  else in the palette and the coral is 1 dp wide at that size. See §16.

Navigation is a flat `NavHost` with five pushed screens and no nested graphs
(`BalRoot.kt:53-118`). Correct for this size. Transitions are RTL-aware
(`SlideDirection.Start/End`, `:30-35`).

---

## 12. Creation and editing audit

Three questions — what, repeat, time — plus a confirming sentence before saving
(`EditorScreen.kt:118-211`). The confirming sentence is the best idea in the editor:
«كل يوم، الساعة ٦:٠٠ صباحًا» removes the need to infer a reminder's kind from a chip.

**Correct:** past-time guard for one-timers only (`EditorViewModel.kt:228-233`); weekly
requires ≥1 day (`:220-223`); editing preserves `enabled` so a paused reminder does not
silently start ringing (`:249`); day-31 clamping is explained in-line rather than
discovered (`EditorScreen.kt:284-286`, `strings.xml:99`).

**Problems:**
- **The Arabic parser is 437 lines + `ConsumableText` + 310 lines of test, powering one
  optional chip.** `EditorViewModel.setTitle` handles **only** `ParseResult.Success`
  and discards everything else (`EditorViewModel.kt:162-165`). That makes
  `ParseResult.Incomplete`, `Draft`, `MissingPart` (`ReminderParser.kt:21-32`) and the
  whole partial-schedule branch (`ArabicReminderParser.kt:79-98`) dead. There is no
  quick-add bar, no share-target, no voice entry — the parser's only reachable output
  is a suggestion chip that appears after you have already opened the editor and started
  typing, at which point the three pickers below it are two taps away.
- **`Stepper` for day-of-month steps 1..31 one tap at a time** (`EditorScreen.kt:278-287`).
  Choosing the 28th is 27 taps. It is also the only control in the app that cannot be
  reached in bounded time.
- **The `TimePicker` is `is24Hour = false`** (`EditorScreen.kt:321`) while every
  displayed time uses Arabic dayparts (`BalFormats.time`, `:42-54`). Consistent enough,
  but the picker's AM/PM labels come from the system, not from the app's own
  فجرًا/صباحًا vocabulary.
- **No Hijri entry point at all** — see C2.

---

## 13. Settings and customization audit

Four things: theme, snooze duration, permissions link, about link (`SettingsScreen.kt:101-145`).
**The restraint is correct. Do not add to this screen.**

Problems: C1 (the snooze setting is a false statement), C8 (its labels are
ungrammatical), and the Hijri sighting adjustment that the domain still carries
machinery for — `HijriAdjustmentProvider` is bound to a constant `0`
(`AppModule.kt:66-68`) and threaded through every Hijri branch of
`RecurrenceCalculator` (`:29`, `:40`, `:80`, `:118`) for no reachable purpose.

---

## 14. Alarm and notification audit

**Strong.** `setAlarmClock` (`AlarmGateway.kt:47`), a silent channel so the ringer owns
the sound (`NotificationPresenter.kt:44-54`), `CATEGORY_ALARM` + `PRIORITY_MAX` +
`setOngoing` (`:94-96`), a full-screen intent with heads-up fallback (`:100`), a
`systemExempted` foreground service (`AlarmRingerService.kt:114-120`), audio focus, the
alarm stream, a 30-second volume ramp (`:163-170`), a wake lock bounded to the timeout
plus a minute (`:193`), and a generation counter that makes duplicate service starts
no-ops (`:76`, `:96`). Ringtone fallback chain alarm → ringtone → notification
(`:139-159`). Vibration continues even when no sound is playable (`:160`).

`AlarmActivity` shows over the lock screen correctly for both API levels
(`AlarmActivity.kt:147-161`), forces Arabic and RTL in `attachBaseContext` (`:92-99`),
scrolls (`:202`) — which is what makes it survive large text — and refuses to close
mid-confirmation when the ringer stops elsewhere (`:84-90`).

**Issues:**
- The alarm screen's «تم» hushes the ringer *before* the slide is completed
  (`AlarmActivity.kt:296-299`). Deliberate and defensible (it is postpone-safe), but it
  means a user who taps «تم» and then walks away has silenced the alarm without
  answering it, and 5.2 then erases the occurrence at midnight.
- The notification's only inline action is تأجيل (`NotificationPresenter.kt:102-106`).
  **Correct — keep.** Completion must not be a notification tap.
- `NotificationPresenter` duplicates `canUseFullScreenIntent` (`:59-63`) with
  `Permissions.canUseFullScreenIntent` (`Permissions.kt:62-66`); the former has no
  callers. Two implementations of one system check across two layers.
- `alarmNotificationId(reminderId) = 3_000_000 + reminderId.toInt()`
  (`NotificationPresenter.kt:175`) and PendingIntent request codes are
  `reminderId.toInt()` (`AlarmGateway.kt:76`). Safe in practice (ids come from
  `AUTOINCREMENT`), but the narrowing is unguarded.

---

## 15. Arabic, RTL, and typography audit

**Strong.** RTL is forced at the theme level rather than depending on the system locale
(`Theme.kt:221`), the locale is pinned in `attachBaseContext` on both activities
(`MainActivity.kt:28-37`, `AlarmActivity.kt:92-99`) and on the notification context
(`NotificationPresenter.kt:34-38`). Arabic-Indic digits everywhere via `arabicDigits`.
Dayparts are natural (فجرًا/صباحًا/ظهرًا/عصرًا/مساءً) rather than ص/م. Saturday-first
week ordering (`BalFormats.kt:127-130`). Common week shapes are *named* («أيام العمل»,
«نهاية الأسبوع») instead of listing five day names (`:139-144`) — a genuinely good call.
Bidi isolates for Latin runs (`:35-39`), used on the version string. Auto-mirrored icons
where direction carries meaning, with the reason written down (`SettingsScreen.kt:208-210`).

**Issues:**
- C8 (the settings snooze grammar).
- `Locale("ar")` resolves Arabic-Indic digits for `%d` through ICU on Android but
  **ASCII on the JVM**, so `getQuantityString` output differs between the app and any
  unit test that formats it. No test covers it today; one would be misleading if written.
- `closedMeta` and `SectionTitle` compose strings with literal separators in Kotlin
  (`ChecklistScreen.kt:360`, `Components.kt:380`) rather than through resources.
- Two different offset mechanisms for the same RTL problem: `place` + manual sign flip
  (`Components.kt:539-546`) vs `placeRelative` (`SlideToConfirm.kt:192-201`). Both
  correct; one should win.

Typography: four Tajawal weights mapped to a five-step scale with zero tracking
(`Theme.kt:155-199`). Body sizes deliberately a step above Material's. This is
considered work.

---

## 16. Visual identity, logo, and app icon audit

The mark is one canonical pair of paths on a 0..24 grid (`Components.kt:137-156`),
copied verbatim into three XML vectors. **The single-source discipline is right; the
artwork it distributes is the problem.**

### 16.1 What the mark actually is

Measured from the path data:

- Bell body + clapper: x ∈ [2.31, 20.1], y ∈ [2.0, 22.1]
- Ring arcs: x ∈ [15.3, 21.8], y ∈ [1.5, 10.0]
- Full bounding box: 19.5 × 20.5 units
- Arc ribbon width: ≈ 0.55–0.7 units — **2.3–2.9 % of the icon**

### 16.2 Weaknesses, specifically

1. **The silhouette is the single most-used glyph in software.** It is a notification
   bell, near-identical to `Icons.Rounded.Notifications`. There is nothing in it that
   is only رَنّة. Beside Google Clock, Samsung Clock, Alarmy or any reminders app, it
   is the shape they all either use or deliberately avoid *because* it is used.
2. **Two clichés stacked.** Bell (notification) + concentric arcs (wi-fi / signal).
   Stacking two generic symbols does not produce distinctiveness; it produces crowding.
3. **The clapper is detached.** There is a visible gap between the bell mouth and the
   clapper. At ≥48 dp it reads as a rendering error; below 32 dp it becomes a stray dot
   that looks like a badge or an artefact. It is the only element with no structural
   connection to anything.
4. **The notch is damage.** A circular bite is carved out of the bell's upper-right
   shoulder purely to clear the arcs (`Components.kt:142-143`). It breaks the silhouette
   at exactly the point where the eye expects continuity, and at small sizes it closes
   up and the arcs merge into the bell anyway.
5. **The composition is off-balance.** The bell's mass centres at x ≈ 11.2 on a
   24-grid whose centre is 12; the arcs occupy the top-right; the bottom-right is empty.
   The result is heavy bottom-left, thin top-right, hollow bottom-right. On a circular
   launcher mask the arcs sit near the clip edge.
6. **The brand's only distinguishing element is its most fragile one.** At 2.5 % of the
   icon, the arcs are ~1.2 dp on a 48 dp launcher grid and under 1 dp in the 24 dp
   status bar. They are the first thing to disappear at every size that matters.
7. **The palette is divorced from the product.** Logo: ink `#151436`, cream `#FEF9EE`,
   coral `#FE5A5F` (`Theme.kt:120-127`). Product: teal `#0B6B5F`, brass `#9A6B1E`,
   stone `#F4F1EA` (`Theme.kt:29-39`). The code states the separation as an intention
   («ألوان الشعار وحدها … لا تدخل في واجهة التطبيق», `Theme.kt:117-118`). **This is the
   core brand failure.** A logo that shares no colour with its product is not an
   identity; it is a second identity. The coral is additionally the generic
   "productivity app" accent, and it sits within a hair of the app's own error colour
   `#A5342A`.
8. **Three different darks ship in one product.** `#151436` (logo, splash),
   `#1A1915` (app night background), `#232219` (widget card, `colors.xml:10`). Placed on
   one screen they read as three different apps.
9. **There is no background-free primary logo.** The only shipped asset
   (`docs/assets/rannah-logo.png`, used as the README masthead) is the tile. In-app,
   `AppMark` recolours the bell to whatever the surface needs — teal, ink, outline,
   `onBackground` (`ChecklistScreen.kt:436`, `:387`, `AlarmActivity.kt:219`,
   `EditorScreen.kt:200`) — so there is no single object you could hand to a store
   listing or a press kit and call "the logo".
10. **The in-app tile and the launcher tile are different shapes.** `AppIconTile` draws
    its own `RoundedCornerShape(percent = 23)` (`Components.kt:118`) while the launcher
    is an adaptive icon clipped by the *system* mask (`ic_launcher.xml`). On any device
    whose mask is a circle or a squircle, the icon in «عن رَنّة» is visibly not the icon
    on the home screen.
11. **The monochrome layer is the colour foreground.**
    `<monochrome android:drawable="@drawable/ic_launcher_foreground"/>` (`ic_launcher.xml:5`)
    points at a two-colour drawable with hard-coded fills. Under themed icons the whole
    mark flattens to one tint: the accent — the only thing that distinguishes the mark —
    disappears entirely, and the notch becomes the sole separator.
12. **The launcher mark is conservative.** 47.8 × 50.2 dp inside a 66 dp safe zone and a
    72 dp visible area (group scale 2.45, translate 24.6, `ic_launcher_foreground.xml:10-11`).
    That is ~72 % of the safe zone where most icons fill it; combined with the
    off-balance composition it reads smaller than its neighbours.
13. **The splash mark is small and the colour cut is hard.** `ic_splash` places the
    24-unit mark at ×5 = 120 dp on a 288 dp canvas — 41.7 %, against the ~66 % the
    splash spec expects (`ic_splash.xml:7-8`). Its background is ink indigo
    (`themes.xml:11`) while the app's first frame is stone or coal (`themes.xml:4`,
    `values-night/themes.xml:4`). The launch is: small bell on indigo → cut to cream.
14. **The wordmark does no work.** «رَنّة» is set in unmodified Tajawal ExtraBold. A
    three-letter Arabic word carrying a shadda and a fatha is an unusually strong
    candidate for a drawn wordmark, and it is currently just text.

### 16.3 What is right and must be kept

- One canonical geometry, one source, every surface wearing it (`Components.kt:74-107`).
  The *discipline* is correct even though the *artwork* is not.
- Colour-splitting the mark by meaning (body vs. sound) is a good idea, executed with
  the wrong colours.
- `MadeInSaudi` set as a typographic colophon rather than borrowing the registered
  «صنع في السعودية» programme mark, with the legal reasoning written down
  (`Components.kt:160-173`). Correct and honest.

---

## 17. Accessibility audit

**Done well:** `SlideToConfirm` exposes a single ordinary click action so TalkBack and
switch access confirm with one activation instead of emulating a drag
(`SlideToConfirm.kt:102-112`) — and the label carries the whole meaning, so it survives
animations-off and colour-blindness. The completion ring has a 48 dp target around a
30 dp visual (`Components.kt:402-424`). Row actions carry
`"$action: $title"` descriptions (`:403`, `:492`, `:515`, `ChecklistScreen.kt:371`).
Closed rows distinguish completed from skipped three ways at once — mark, word, fill
(`Components.kt:426-434`). The alarm screen scrolls (`AlarmActivity.kt:202`).

**Risks at 200 % font scale:**

- **`SlideToConfirm` will clip.** The track is a fixed `76.dp` (`SlideToConfirm.kt:80`,
  `:101`) and the label is `titleLarge` (20 sp → 40 sp) inside `padding(horizontal = 64.dp)`
  (`:131`). On a 360 dp screen that leaves ~190 dp for «اسحب للتأكيد» at 40 sp; it wraps
  to two or three lines inside a 76 dp box. **This is on the one screen where confirmation
  must not be ambiguous.**
- **The details «اليوم» button pair will clip.** Two `weight(1f)` buttons with
  `heightIn(min = 56.dp)` (`DetailsScreen.kt:177-200`); «تخطي اليوم» at 30 sp inside
  ~150 dp. Material buttons are single-line.
- **`Stepper` is marginal**: two fixed 48 dp icon buttons plus an unweighted `Text`
  (`EditorScreen.kt:406-426`); a long month name at 36 sp may overflow.
- **`ClosedRow`** squeezes a two-line column against a «تراجع» button in one row
  (`Components.kt:447-496`).

**Not covered anywhere:** there is no `fontScale` or large-font test, and no
`@Preview` at increased scale. This is the clearest gap in an otherwise careful app.

---

## 18. Data, migration, and cleanup audit

**The ladder is correct and proven.** Five exported schemas, four migrations, no gaps
(asserted at `MigrationChainTest.kt:35-45`), the whole chain landing on exactly the
exported v5 schema (`:148-159`), duplicates collapsed before the unique index is created
(`BalDatabase.kt:44-52`), and a table rebuild instead of `DROP COLUMN` because minSdk 26
ships pre-3.35 SQLite (`:108-153`).

**But every one of those migrations is dead in the field.** `versionCode = 1`
(`build.gradle.kts:39`) and `v1.0.0` is the only tag. No device carries a database at
version < 5. `MIGRATION_1_2` … `MIGRATION_4_5` and the ~200 lines of legacy handling they
imply — the `hijri_monthly` rewrite, `stopMarksCompleted`, `pending_confirmations`,
the completed-recurring normalisation — can never run. They are excellent work that
protects nobody.

**Retention and cleanup:**
- Completed one-timers pruned the next local day (`ReminderDao.kt:129-137`), driven from
  three places: app start (`BalApp.kt:36`), the daily worker (`ReconcileWorker.kt:27`),
  and a midnight tick while the app is open (`ChecklistViewModel.kt:79-88`). Correct.
- Records pruned at 180 days, on **both** axes so an early completion's record is not
  removed while its occurrence is still ahead (`ReminderDao.kt:139-149`) — a genuinely
  subtle correctness point, and it is tested (`ReminderSchedulerTest.kt:435-455`).
- **Orphan records are deliberately kept.** `MigrationChainTest.kt:125-131` asserts a
  record whose reminder no longer exists survives migration. Nothing ever removes it
  except the 180-day sweep. Moot today (no legacy databases) but documented as a leak.
- Deletion is transactional and returns a snapshot for undo (`ReminderDao.kt:86-93`),
  with restore idempotent under the unique index (`:100-104`).

**Legacy columns still written on every insert:** `categoryId`, `priority`, `alertMode`,
`soundEnabled`, `vibrationEnabled`, `ringtoneUri`, `alarmTimeoutMinutes`,
`alarmGradualVolume`, `alarmRepeatIfIgnored`, `followUntilComplete`,
`followUpIntervalMinutes`, `followUpMaxRepeats`, `completionLabel` (`Mappers.kt:88-128`).
Thirteen columns, none read.

---

## 19. Architecture and maintainability audit

**Boundaries are clean.** Domain (`Reminder`, `Schedule`, `OccurrenceStatus`,
`RecurrenceCalculator`, `OccurrenceStateResolver`) is pure and JVM-testable. Data is
Room behind a repository interface. Scheduling is behind two seams (`AlarmGateway`,
`ReminderNotifications`) that exist specifically so the scheduler is unit-testable —
and 34 tests prove that paid off. UI is Compose + Hilt view models with no Android
dependency leaking into the domain.

**Sources of truth are singular and correct:**
- Reminder state → the database.
- Display state → `OccurrenceStateResolver`, read by home, details and (via the same
  instants) the alarm surface.
- The bell geometry → `PATH_BELL` / `PATH_RING`.
- Spacing → `Space`.
- "When" → `BalFormats.dateTime`.

**Duplication that survives:**
- `canUseFullScreenIntent` implemented twice (`NotificationPresenter.kt:59`,
  `Permissions.kt:62`); the first is unused.
- Two RTL offset mechanisms (§15).
- `BalFormats.date` and `BalFormats.dateTime` overlap substantially.

**Maintainability risk:** the comment density is unusually high and unusually *good* —
several comments record the reasoning behind a decision (`BalDatabase.kt:95-107` is the
best example in the repository). But a handful now describe intentions the code no
longer serves: `Mappers.kt:24-34` justifies legacy columns "so old databases migrate
losslessly" when no old database exists; `Schedule.kt:23-30` describes a sighting
adjustment applied "at scheduling time" that is hard-wired to zero.

---

## 20. Privacy and release audit

**Privacy claims are accurate.** Verified against the merged release manifest: ten
permissions, no `INTERNET`, `ACCESS_NETWORK_STATE` and the AndroidX dynamic-receiver
permission present exactly as `docs/PRIVACY.md:47-48` says. No analytics or crash
reporting in `libs.versions.toml`. No logging: the only `catch` blocks swallow silently
rather than logging (`NotificationPresenter.kt:130`, `AlarmRingerService.kt:145`), so
the "nothing reaches logcat" claim holds.

**The `allowBackup` disclosure is exemplary.** `AndroidManifest.xml:19` is
`allowBackup="true"` with `<full-backup-content />` empty (`backup_rules.xml`) and
`<cloud-backup/><device-transfer/>` unrestricted (`data_extraction_rules.xml`) — i.e.
everything is backed up including reminder titles and notes. Both the README
(`README.md:73-74`), the privacy doc (`docs/PRIVACY.md:24-34`) and the in-app screen
(`strings.xml:164`) say so plainly instead of claiming "everything stays on your device".
That is the right call and it should not change.

**Release configuration is sound.** R8 with resource shrinking, `isDebuggable = false`,
v2+v3 signing with v1 explicitly disabled and the reasoning recorded
(`build.gradle.kts:52-58`), signing credentials read from outside the repository with a
graceful unsigned fallback (`:22-29`), keystore patterns in `.gitignore:27-33`, language
splits disabled because the app forces Arabic (`:92-97`), and a ProGuard file that keeps
exactly the entry points reached reflectively or by name — with `ReconcileWorker` kept
because WorkManager persists the class name across updates (`proguard-rules.pro:15-23`),
which is a real bug most apps ship.

**Release risks not covered by tests:**
- No instrumented tests at all. `testInstrumentationRunner` is configured
  (`build.gradle.kts:42`) but `androidTest/` does not exist. Everything touching
  AlarmManager, notifications, the foreground service, the lock screen and the widget is
  verified only by reading.
- No Compose UI tests, so `ChecklistViewModel.build` (where C4 lives) and
  `DetailsViewModel`'s state derivation are entirely untested.
- No `fontScale` coverage (§17).
- The README's install instructions reference `SHA256SUMS.txt` and an `.aab` attached to
  the GitHub release (`README.md:62-66`); neither is produced by anything in the
  repository, so both are manual steps with no guard.

---

## 21. Unnecessary features and code to remove

Ordered by confidence. All are provable from the source.

| # | What | Evidence | Why remove |
|---|---|---|---|
| 1 | `Reminder.snoozeMinutes` (domain field + editor plumbing) | `Reminder.kt:18`, `EditorViewModel.kt:41,107,153,253` | Unreachable, and the direct cause of C1 |
| 2 | All four migrations + legacy read paths | `BalDatabase.kt:26-181`, `Mappers.kt:21-34,44-65` | `versionCode 1`; no device can carry v1–v4 |
| 3 | Hijri **scheduling** (`OnceHijri`, `HijriMonthly`, `HijriYearly` + their `RecurrenceCalculator` branches + `HijriAdjustmentProvider`) | `Schedule.kt:41-74`, `RecurrenceCalculator.kt:37-42,76-98,115-131`, `AppModule.kt:66-68` | Constructible only from legacy rows that do not exist. **Keep Hijri *display*** (`hijriFull`) — it is used and valued |
| 4 | `OccurrenceStatus.IGNORED` + `status_ignored` | `OccurrenceStatus.kt:16-24`, `strings.xml:136`, `OccurrenceStatusUi.kt:25,33,47` | Nothing writes it; the enum says so |
| 5 | `ParseResult.Incomplete` / `Draft` / `MissingPart` + the parser's partial-schedule branch | `ReminderParser.kt:21-32`, `ArabicReminderParser.kt:79-98`, `EditorViewModel.kt:162-165` | Only `Success` is ever handled |
| 6 | Thirteen unread legacy columns | `Mappers.kt:88-128` | Written on every insert, read by nothing |
| 7 | `NotificationPresenter.canUseFullScreenIntent` | `NotificationPresenter.kt:59-63` | Duplicate of `Permissions.canUseFullScreenIntent`; zero callers |
| 8 | `HijriDates.yearOf`, `HijriDates.supportedYears` | `HijriDates.kt:40,44` | Zero callers |
| 9 | `DetailsState.todayOpen` | `DetailsViewModel.kt:44` | Zero callers |
| 10 | `PermissionsStatus.ready` | `Readiness.kt:67` | Zero callers |
| 11 | `AppIconTile`'s 23 % rounded square | `Components.kt:115-131` | Contradicts the launcher mask (§16.10); the mark should stand alone |
| 12 | The overflow menu wrapper around a single action | `Components.kt:506-536` | A menu of one (§8) |

**Deliberately *not* on this list:** `Permissions.channelSettingsIntent` — it is
unused, but the fix is to *call* it (C6), not to delete it.

**The parser question.** The parser itself (`ArabicReminderParser` + `ConsumableText`,
~540 lines, 37 tests) is good work and genuinely hard to rebuild. But it currently
returns value through one suggestion chip inside a screen that already has the pickers.
Two honest options: **give it a real entry point** (a quick-add field on home, or a
`ACTION_SEND`/assistant target) so it earns its size, or **retire it**. Keeping it as
decoration is the one choice that costs without paying.

---

## 22. Missing improvements worth adding

Ranked by practical value per unit of complexity.

1. **A real snooze override** (§10). Highest value: it closes the largest gap between
   promise and product.
2. **Fix C1 by deleting the per-reminder field.** Makes an existing setting true.
   Negative complexity.
3. **Date the overdue state and move it out of «اليوم»** (C5). Either a fourth section
   («متأخرة») or, cheaper and probably better, keep it in «اليوم» but render the date
   through `BalFormats.dateTime` instead of bare time. One line, removes a lie.
4. **Record an unanswered occurrence when its day ends** (5.2). Without it, «فات موعده»
   is a state the app claims and almost never reaches, and a user who ignored three
   rings has no way to see that they did.
5. **A fix path for the blocked alarm channel** (C6). `channelSettingsIntent` already
   exists; it needs a card.
6. **Wrap `SlideToConfirm`'s label / lift its fixed height** (§17). The alarm screen is
   the wrong place to clip a confirmation.
7. **A skip that is one labelled tap** (§8), matching the paused-row precedent.
8. **Cancel/edit a live snooze from details** (§10.8).

**Not worth adding, and worth refusing explicitly:** categories, tags, priorities,
per-reminder ringtones, location reminders, sub-tasks, statistics/streaks, a calendar
view, cloud sync, an accounts system, notes attachments, per-reminder alert styles. All
of these were either removed once already or are exactly the direction the product has
correctly refused. The app's value is that it does one thing.

---

## 23. Contradictions between screens or layers

| # | Contradiction | Where |
|---|---|---|
| 1 | Settings offers a snooze duration that existing reminders ignore | `SettingsScreen.kt:113` vs `EditorViewModel.kt:107` |
| 2 | README promises Hijri reminders the editor cannot create | `README.md:33,156` vs `EditorViewModel.kt:53-59` |
| 3 | README promises a user-chosen snooze duration | `README.md:36` vs `AlarmActivity.kt:282-292` |
| 4 | A row can read «مكتمل» and «القادمة اليوم» at once | `ChecklistViewModel.kt:133-140` (C4) |
| 5 | Section «اليوم» contains occurrences from weeks ago, shown without a date | `ChecklistViewModel.kt:132` + `ChecklistScreen.kt:328` (C5) |
| 6 | «الرنّات صامتة» is declared blocking, with no fix offered | `Readiness.kt:31` vs `PermissionsScreen.kt:105-168` |
| 7 | Same number, two Arabic grammars | `strings.xml:106` vs `strings.xml:214-221` |
| 8 | The icon in «عن رَنّة» is a different shape from the icon on the home screen | `Components.kt:118` vs `ic_launcher.xml` |
| 9 | Three darks: `#151436` / `#1A1915` / `#232219` | `colors.xml:7,6,10` |
| 10 | The logo's palette appears nowhere in the app, by written intention | `Theme.kt:117-127` |
| 11 | The widget is night-only while the app offers a light theme | `widget_bg.xml` + `colors.xml:10-13` vs `strings.xml:141-143` |
| 12 | Comments justify legacy handling for databases that cannot exist | `Mappers.kt:24-34` vs `build.gradle.kts:39` |

---

## 24. Recommended final product model

**Keep the model. Make it true.**

> One list. Every reminder rings. When it rings: **تأجيل** (one tap, default duration;
> «مدة أخرى» for this ring only) or **تم** (deliberate slide). When it is not ringing:
> **تخطي اليوم** (one labelled tap on the row), **إيقاف مؤقت**, **استئناف**, **تعديل**,
> **حذف**. Everything reversible. One global snooze default. Gregorian scheduling,
> Hijri displayed alongside.

Changes to the model itself: **two.**
1. Snooze duration becomes global-and-true, with a temporary per-ring override.
2. Hijri becomes explicitly display-only — in the code, and in the README.

Everything else is a fix, not a model change.

---

## 25. Recommended final logo and icon direction

### 25.1 The idea

**Own the sound, not the bell.** The name is رَنّة — the *ring*, not the object.
Every competitor owns the bell; nobody owns the ring. The current mark says
"notification" twice (a bell, then wi-fi arcs) and رَنّة zero times.

**Primary direction:** a single continuous form in which the bell's profile and the
motion of ringing are the *same* stroke — a tilted, open-mouthed bell whose lip carries
through into a swing arc — so "it is ringing" is communicated by **posture**, which
survives every size and every colour, rather than by two hairlines that do not. One
shape. One weight. No detached parts. No notch. If the ر of رَنّة can be found in that
profile without forcing it, take it; if it has to be forced, do not.

### 25.2 If a full redraw is out of scope, the minimum that fixes every measured defect

- **Reattach or delete the clapper.** A bell reads as a bell without one.
- **Delete the notch.** Move the arcs outboard so they clear the silhouette naturally.
- **Reduce two arcs to one**, thickened to ≥1.2 units on the 24 grid (5 %), so it
  survives 24 dp.
- **Tilt the whole bell 12–15°.** Posture carries the ringing.
- **Re-centre on the optical centre**, not the bounding box.
- **Grow the launcher mark** from 47.8 dp to ~60 dp within the 66 dp safe zone.

### 25.3 The colour decision — the important one

**Pick one palette and make the logo wear it.**

Recommended: **tile = deep teal `#0B6B5F`; bell = warm cream `#FEF9EE`; accent = brass
`#9A6B1E`.** Drop the coral and the indigo entirely.

Reasons: (a) the home-screen icon and the app's primary action become the same colour,
which is the cheapest way a product looks designed rather than assembled; (b) teal +
brass on stone is already the considered Saudi palette and the coral is imported from
nowhere; (c) coral-on-indigo is the generic productivity pairing *and* sits a hair from
the app's own error colour `#A5342A`.

Then collapse the three darks into one: the splash background becomes the app's own
background (stone in light, coal in dark), and the widget becomes theme-aware.

### 25.4 Should the primary logo exist without a background container?

**Yes — and it currently does not.** The only shipped asset is a tile; in-app the bell
is recoloured per surface; the tile shape does not match the launcher mask. Ship a
single-colour, container-free primary mark as the canonical logo, and let the tile be
one *application* of it rather than the thing itself.

### 25.5 The system to ship

| Asset | Specification |
|---|---|
| **Primary logo (no container)** | Single-colour mark on the 24 grid, drawn to survive 16 dp. Ships as SVG **and** as the same Kotlin path pair, so the single-source rule survives |
| **Launcher (adaptive)** | Foreground = mark at ~60 dp of the 108 canvas, optically centred; background = flat teal; **no tile inside the drawable** |
| **Monochrome** | Its **own** single-path drawable — merge bell and arc into one closed shape with a real, generous gap. Never the colour foreground |
| **Dark-background lockup** | Cream mark |
| **Light-background lockup** | Teal mark — never cream |
| **In-app brand mark** | `AppMark` unchanged in mechanism; delete `AppIconTile`'s rounded square so the mark stands alone |
| **Notification** | Simplified single shape at ~1.5× the current stroke weight, checked at 24 dp against the status-bar mask |
| **Splash** | Mark at ~192 dp of the 288 canvas; background = the app's own background colour |
| **Wordmark** | Draw «رَنّة» once — the shadda and fatha are an asset, not an obstacle. Optional, but it is where the remaining distinctiveness is |

---

## 26. Prioritized plan

### P0 — Critical (fix before the next public build)

| | Item | Ref |
|---|---|---|
| P0.1 | Delete `Reminder.snoozeMinutes`; read the global default at snooze time | C1 |
| P0.2 | Correct the README: remove the Hijri-reminders claim (AR + EN); restate snooze truthfully | C2, C3 |
| P0.3 | Stop hiding a reminder that will ring again today — key `closedIds` by occurrence, not by reminder | C4 |
| P0.4 | Date the overdue state; stop presenting a three-week-old occurrence as «اليوم، ٩:٠٠» | C5 |
| P0.5 | Add the alarm-channel card and wire `channelSettingsIntent` | C6 |
| P0.6 | Fix the settings snooze plurals | C8 |

### P1 — High value

| | Item | Ref |
|---|---|---|
| P1.1 | Ship the snooze override: «مدة أخرى» → sheet (٥/١٥/٣٠/٦٠ + «حتى وقت محدد») | §10 |
| P1.2 | Cap any snooze below the next natural occurrence | 5.1 |
| P1.3 | Cancel / edit a live snooze from details | §10.8 |
| P1.4 | New identity: palette unification first, then the mark, then the icon system | §25 |
| P1.5 | Own monochrome drawable; delete `AppIconTile`'s tile; splash on the app's own background | §25.5 |
| P1.6 | Fix `SlideToConfirm` and the details button pair at 200 % font scale | §17 |
| P1.7 | Record an unanswered occurrence at end of day so «فات موعده» becomes real | 5.2 |
| P1.8 | Replace the overflow-menu-of-one with a labelled «تخطي» button | §8 |
| P1.9 | Reorder `startAlarm` / `setSnooze`; remove `complete`'s null-occurrence default | 5.3, 5.4 |

### P2 — Optional

| | Item | Ref |
|---|---|---|
| P2.1 | Delete the four migrations and legacy read paths | §21.2 |
| P2.2 | Delete Hijri **scheduling**; keep Hijri **display** | §21.3 |
| P2.3 | Delete `IGNORED`, the dead parser result types, the unread columns, the four zero-caller symbols | §21.4-10 |
| P2.4 | Decide the parser's fate: give it a real entry point, or retire it | §21 |
| P2.5 | Theme-aware widget | §23.11 |
| P2.6 | Replace the day-of-month `Stepper` with a bounded control | §12 |
| P2.7 | Unique index on `(reminderId, occurrenceAtMillis)` for terminal statuses | C7 |
| P2.8 | Add `fontScale` previews / a large-text check to CI | §17 |
| P2.9 | Reconcile the two RTL offset mechanisms | §15 |

### Reject or defer

- Quick-duration chips on the alarm screen root — §9.2.
- Remembering the last-used snooze — §9.2.
- Per-reminder snooze configuration in the editor — §9.2.
- Completion from the notification — the current design is correct.
- Categories, tags, priorities, per-reminder ringtones, location reminders, sub-tasks,
  streaks, calendar view, sync, accounts — §22.
- Reintroducing «المتابعة حتى الإنجاز» in any form — it was removed for good reasons
  recorded in `BalDatabase.kt:56-60` and `:156-171`.
- Building Hijri reminder *creation* — unless it is a deliberate new feature with an
  editor, a sighting-adjustment setting and tests. Do not resurrect the dead code to
  claim the README's existing sentence; either build it properly or correct the sentence.

---

## 27. Explicit decisions that should remain unchanged

1. **Every reminder is an alarm.** No per-reminder alert styles. The name is the promise.
2. **Stopping a sound never records a completion.** (`BalDatabase.kt:95-107`) The single
   best decision in the repository.
3. **Completion is a deliberate slide, never a tap.** (`SlideToConfirm.kt:43-67`)
4. **The notification's only action is تأجيل.** Completion must never be a notification tap.
5. **The alarm ringer times out silently and decides nothing.** (`AlarmRingerService.kt:197-200`)
6. **One representation of "paused"** — `enabled = false`, nothing else.
7. **Occurrence identity survives snooze.** (`snoozedOccurrenceAt`)
8. **The database is the source of truth; alarms are always derivable from it.**
9. **`setAlarmClock`, not `setExactAndAllowWhileIdle`.**
10. **RTL and Arabic forced at the app level**, independent of the system locale.
11. **Zero letter-spacing on every Arabic style.**
12. **The `allowBackup` disclosure stays honest.** Do not replace it with "everything
    stays on your device".
13. **One-time reminders are pruned the next day; recurring ones never are.**
14. **`MadeInSaudi` stays a typographic colophon**, not the registered programme mark.
15. **The three-question editor with a confirming sentence.** Do not add a fourth question.
16. **Scheduling stays Gregorian.**
17. **Deleting a recurring reminder is confirmed; deleting a one-timer is undoable.**
18. **One canonical geometry for the mark, worn by every surface.** Replace the artwork,
    keep the discipline.

---

## 28. Risks that cannot be confirmed without runtime testing

Stated as unresolved, not as findings.

1. **Full-screen intent behaviour on Android 14+ and on OEM skins.** The permission is
   checked and the fallback exists, but whether the alarm screen actually surfaces over
   the lock screen on Xiaomi/Samsung/Huawei is unverifiable from source.
2. **Foreground-service start from a broadcast on aggressive OEMs.** There is a
   `catch (Exception)` fallback (`NotificationPresenter.kt:74-77`), but whether it is
   reached — and whether the notification alone is enough — needs a device.
3. **Whether `getQuantityString` renders Arabic-Indic digits on Android** as reasoned in
   §15. Trivial to confirm on device; misleading to unit-test on the JVM.
4. **The exact clipping point of `SlideToConfirm` at 200 %** — the geometry says it
   clips; the screen size at which it starts needs measurement.
5. **The widget's 30-minute update period vs. the date it displays** — the date can be
   up to 30 minutes stale across midnight. Behaviour depends on launcher scheduling.
6. **Doze and battery-manager behaviour** over multi-day idle periods, which is exactly
   what `ReconcileWorker` exists to defend against and exactly what cannot be simulated.
7. **`reminderId.toInt()` narrowing** in PendingIntent request codes and notification ids
   — safe by construction, unverified in a long-lived database.
8. **Perceived icon size and legibility on a real home screen**, in themed-icon mode, and
   in the status bar — the geometry is measured above, but the final judgement is visual.
9. **Whether the R8-minified release preserves every reflective entry point.** The
   ProGuard rules look right and are reasoned; only a signed release run confirms it.

---

# Closing summary

## The five most serious problems

1. **«مدة التأجيل» in settings changes nothing for any existing reminder.** A visible
   control that silently does not work. (C1)
2. **The README sells two features the app does not have** — Hijri reminders, and a
   snooze duration the user chooses. (C2, C3)
3. **The identity is two generic symbols in a palette the app never uses**, with a
   detached clapper, a notch that damages the silhouette, and an accent that is the
   first thing to vanish at every size that matters. (§16)
4. **A reminder can read «مكتمل» and «القادمة اليوم» simultaneously, and will ring for
   something the app says is finished.** (C4)
5. **Occurrences from weeks ago sit under a heading called «اليوم» with no date, forever.**
   (C5)

## The five highest-value improvements

1. **Delete the per-reminder snooze field** and read the global default at snooze time.
   Makes an existing setting true, removes a class of invisible state, and is a net
   *reduction* in code.
2. **Ship the snooze override** — «مدة أخرى» → one sheet — capped below the next
   occurrence.
3. **Unify the palette and rebuild the icon system** around it. The single largest
   perceived-quality gain available.
4. **Correct the public claims** in the README and the store text.
5. **Fix the two 200 %-font clipping points**, starting with `SlideToConfirm` — the one
   screen where an ambiguous confirmation is unacceptable.

## Recommended custom snooze behaviour

One global default, shown truthfully on the alarm button. One subordinate text button
«مدة أخرى» beneath it (never a long-press) opening a sheet with
`٥ · ١٥ · ٣٠ · ٦٠ دقيقة` plus «حتى وقت محدد…». The override applies to **this occurrence
only** and is never remembered. Any target time is echoed in full («٦:٠٠ صباحًا — غدًا»)
so midnight crossing is stated, never inferred. Minimum 1 minute; maximum
`min(12h, next occurrence − 1 min)`. Live snoozes become editable and cancellable from
the details screen. No per-reminder configuration, ever.

## Recommended final logo direction

Own **the ring**, not the bell. One continuous form in which the bell's profile and the
motion of ringing are the same stroke, tilted 12–15° so "ringing" is carried by posture —
which survives every size and every colour — instead of by two hairlines that do not.
No detached clapper, no notch, no wi-fi arcs. A single-colour, container-free primary
mark is the canonical logo; the tile becomes one application of it.

## Recommended app icon direction

Flat **teal `#0B6B5F`** background, **cream `#FEF9EE`** mark, **brass `#9A6B1E`** accent.
Coral and indigo retired. Mark grown to ~60 dp within the 66 dp safe zone and optically
centred. A **dedicated** single-path monochrome drawable — never the colour foreground.
Splash on the app's own background, mark at ~192 dp of 288. The result: the icon on the
home screen and the primary action inside the app are the same colour, and the three
different darks become one.

## What should be removed

The per-reminder snooze field. All four migrations and their legacy read paths
(`versionCode 1` — no device can carry them). Hijri **scheduling** (keep Hijri
**display**). `OccurrenceStatus.IGNORED`. The unused parser result types and the
partial-schedule branch. Thirteen unread legacy columns. The duplicate
`canUseFullScreenIntent`. Four zero-caller symbols. `AppIconTile`'s rounded square. The
overflow menu that contains one item. The coral and the indigo.

## What should remain

Every reminder is an alarm. Stopping a sound never records a completion. Completion is a
deliberate slide. The notification offers only تأجيل. The ringer times out deciding
nothing. One representation of "paused". Occurrence identity survives snooze. The
database is the source of truth. `setAlarmClock`. Forced Arabic and RTL. Zero letter-
spacing. The honest `allowBackup` disclosure. The three-question editor with its
confirming sentence. Gregorian scheduling. One canonical geometry worn by every surface.

## What should not be built

Quick-duration chips on the alarm screen root. A remembered last-used snooze. Per-reminder
snooze configuration. Completion from a notification. Categories, tags, priorities,
per-reminder ringtones, location reminders, sub-tasks, streaks, a calendar view, sync,
accounts. «المتابعة حتى الإنجاز» in any form. Hijri reminder *creation* — unless it is
built properly, with an editor, a sighting-adjustment setting and tests; not resurrected
to justify a sentence in the README.

## Recommended next implementation phase

**Phase A — "Make it true" (P0 only, no new features, no new screens).**
Delete `Reminder.snoozeMinutes`; correct the README; fix the `closedIds` key; date the
overdue state; add the alarm-channel card; fix the settings plurals. Every item removes
a false statement the product is currently making. Small, low-risk, and it clears the
ground.

**Phase B — Identity.** Palette unification first (it is the largest visible gain and
the cheapest), then the mark, then the full icon system in §25.5.

**Phase C — Snooze.** The override sheet, the occurrence cap, and details-screen
cancel/edit — on top of the field already deleted in Phase A.

Doing B or C before A means building new features on top of claims that are not yet true.
