# رَنّة — Deep Product & Logic Audit

Analysis-only. No production code was changed in this phase. Findings are verified
against the current working tree (all last-phase changes are present but uncommitted),
by static inspection of source, resources, and the data model. No emulator, device, or
manual UI testing was used.

Legend for confidence: **[verified]** = proven from code; **[inferred]** = strong
static reasoning, not runtime-proven; **[legacy]** = only reachable via pre-existing
migrated data.

---

## 1. Executive verdict

رَنّة is in good shape structurally after the last simplification pass: one reminder
model, one scheduler, one alarm behaviour, a clean Gregorian-first calendar, and a much
tighter string set. The alarm interaction (تأجيل / تم → slide) is correct and safe.

But it is **not yet stable**, and it is **not yet coherent about what it is**. Three
things block a final stabilization:

1. **A data-lifecycle hole:** completed one-time reminders become invisible *and*
   permanent — they cannot be deleted, cannot be seen, and accumulate in the database
   forever. This is the single most serious defect and it is a correctness/data problem,
   not a polish problem.
2. **A state-model contradiction:** the home checklist ignores `snoozedUntil`, so a
   *postponed* reminder is silently **mislabelled as «يحتاج تأكيدك»** with a "X ago"
   timestamp, while the Details screen shows the same reminder as «مؤجل حتى …». The home
   and details screens disagree about the same reminder.
3. **An identity drift:** the home reads as a **task-manager checklist** (tick boxes,
   strike-through, an accumulating «أنجزته اليوم» list) while the product's real centre
   is "reminders that ring." The two completion models (home swipe vs. alarm tap-then-
   slide) compound this.

None of these needs new features. All three are fixed by *deletion, correction, and
consolidation*. Verdict: **one focused P0 pass on data lifecycle + state truth + home
framing, then it is stabilizable.**

---

## 2. Current product definition

As built today, رَنّة is a **hybrid**: 70% "reminder app that rings," 30% "daily
checklist." Evidence:

- Ringing is the centre: every reminder is a full-screen alarm; the whole
  `AlarmRingerService` + `AlarmActivity` + full-screen-intent stack exists for it.
- But the **home** is a checklist: empty circular "complete" rings, horizontal
  swipe-to-complete, strike-through on done rows, and a persistent «أنجزته اليوم»
  section that grows as you tick things. That is checklist/▢-list vocabulary.

It is **not** an alarm clock (no alarm list/volume/tone management — correctly), **not**
a history app (the Log screen was removed), and **not** a task manager (no projects/
priorities/notes-as-tasks). The 30% checklist framing is the part fighting the other 70%.

---

## 3. What is working well

- **One canonical lifecycle.** `ReminderScheduler` is the single owner; every action is
  idempotent via the unique `(reminder, occurrence, status)` record index. `save`,
  `onAlarmFired`, `complete`, `snooze`, `endSeries`, `rescheduleAll` are coherent. **[verified]**
- **Alarm safety.** Nothing on the alarm screen completes by accident: تم only *reveals*
  the slide; only finishing the RTL-aware `SlideToConfirm` records completion; back /
  leaving / timeout never complete. `resolveOnce` latches against double-fire. **[verified]**
- **Reboot / process-death resilience.** `BalApp.onCreate` + `SystemEventsReceiver` +
  `ReconcileWorker` all call `rescheduleAll`; alarms are always re-derivable from the DB. **[verified]**
- **Gregorian-first calendar.** Scheduling is Gregorian; `RecurrenceCalculator` is pure
  and well-tested; Hijri is display-only in the main surfaces. **[verified]**
- **String hygiene.** Only **one** unused string key remains (`action_snooze`), and only
  **one** em dash slipped through. That is a clean string table. **[verified]**
- **Data compatibility.** DB stays v4 with all columns + migrations; legacy Hijri
  reminders still fire; new rows write neutral defaults. **[verified]**
- **Slide-to-confirm accessibility.** The slide exposes a single `onClick` semantics
  action for TalkBack/switch access, and the label carries the whole meaning. **[verified]**

---

## 4. Critical product problems

### 4.1 Completed one-time reminders are permanent and invisible — the #1 defect **[verified]**
- `ChecklistViewModel.build()` filters active reminders with `it.enabled && !it.isDone`.
  A completed one-time reminder has `isDone == true`, so it is **excluded from every
  live section**.
- The «أنجزته اليوم» list only includes `COMPLETED` records whose `recordedAt` is
  **today**. After midnight, a one-time reminder completed yesterday appears in **no**
  section at all.
- Nothing ever deletes it: `ReconcileWorker` only reschedules; there is no pruning.
- Result: the reminder row lives in the DB **forever**, unreachable → **undeletable**.
  `hasAnyReminder = reminders.any { !it.isDone }` means a DB full of finished one-timers
  even shows the *empty state*, hiding the junk completely.
- The user's report ("completed reminders cannot be deleted at all") is **correct in
  substance**: they are deletable via Details only on the same day; after that they are
  unreachable. See §7 for the recommendation.

### 4.2 The home checklist framing undermines the product centre **[inferred]**
Circular tick-rings + swipe-to-complete + strike-through + an ever-growing done list is
the visual grammar of a to-do app. The product is "reminders that ring." Every completed
row that stays on screen makes the home feel like an **alarm history / checklist** rather
than "what's coming and what still needs you." See §8 and §13.

### 4.3 Two different completion gestures **[verified]**
- Home: a single horizontal **swipe** (or tap the ring) completes immediately (with undo).
- Alarm: **tap تم → then slide** to confirm (deliberate, two-step).
Same outcome, two mental models. For older users this is a learnability tax. The
justification (the alarm is a half-awake context) is sound for the *alarm*; but the home
swipe being one-step-immediate while the alarm is two-step-deliberate is an inconsistency
worth a conscious decision, not an accident.

---

## 5. Alarm and completion logic problems

Traced end-to-end through `AlarmActivity`, `AlarmViewModel`, `AlarmRingerService`,
`ReminderScheduler`, `NotificationPresenter`.

- **Snooze occurrence-key divergence [verified].** `onAlarmFired` advances the recurring
  schedule to the *next* occurrence at fire time. If the user then presses تأجيل, `snooze`
  overwrites `nextTriggerAt` with the snooze instant. When the snoozed alarm re-fires,
  `complete(id, nextTriggerAt)` records completion against the **snooze instant**, whereas
  the home checklist completes against the **schedule occurrence** (`occToday`). These are
  different `occurrenceAt` keys, so in edge cases the same real occurrence can hold two
  different records (e.g. a COMPLETED at the snoozed key and remain "pending" at the
  schedule key on the home). Low frequency, real inconsistency.
- **Process death during confirmation [verified].** `confirming` lives only in the
  ViewModel (survives config change, not process death). If the process dies after تم
  (ring already hushed) and the activity is recreated from the intent, `load()` resets to
  the ringing screen showing «المنبّه يرنّ الآن» with **no sound playing**. Cosmetic but
  contradictory.
- **Timeout leaves a permanently-unresolved recurring occurrence at day boundary
  [verified].** If an alarm rings out unanswered, the occurrence stays «يحتاج تأكيدك»
  (derived). At midnight, `occToday` rolls to the next day and yesterday's unresolved
  occurrence **silently disappears** with no record. Acceptable for a daily reminder,
  but it means "يحتاج تأكيدك" is not durable — it evaporates rather than being resolved.
- **MISSED is written but effectively unseen [verified].** `rescheduleAll` records
  `MISSED` for recurring triggers missed beyond grace, but the only surface that renders
  records is the Details history. There is no home signal and no global history, so MISSED
  is write-mostly data.
- **No contradiction found in the safe paths [verified].** Background tap, back, notification
  behaviour, duplicate taps, and one-time completion are all correct. The alarm cannot
  complete a reminder incorrectly. The *opposite* risk (a reminder staying unresolved
  forever) is the real one, and it exists only for one-time reminders (§4.1) — a recurring
  one self-heals at the next occurrence.

---

## 6. Reminder lifecycle problems

- **Creation / editing / scheduling:** solid. Editor is Gregorian-only; legacy Hijri
  reminders convert on edit. **[verified]**
- **Snoozing:** works at the scheduler level, but is **invisible on the home** (§7/§4)
  and only labelled in Details. **[verified]**
- **Pausing (إيقاف مؤقت):** only reachable inside Details. There is no home affordance and
  no visual marker on the home for a paused reminder (a disabled reminder simply vanishes
  from all home sections because `build()` filters `enabled`). A paused reminder is as
  invisible as a completed one. **[verified]**
- **Deletion:** only from Details (top-bar trash, icon-only, guarded by a confirm dialog).
  For a *recurring* reminder, the «أنجزته اليوم» done-row taps straight into that same
  Details, so a user aiming to tidy today's checkmark can delete the entire series. **Footgun.** **[verified]**
- **History:** none. The Log screen was removed; per-reminder history exists in Details
  but there is no way to browse or clear all history, and records are hard-capped at 500
  rows in the DAO with no user control. **[verified]**
- **Recovery after restart:** good. **[verified]**
- **Cleanup of old occurrences:** none for completed one-time reminder *rows*; occurrence
  *records* self-cap at 500 (silent truncation of oldest). **[verified]**

---

## 7. Completed-item deletion & history recommendation

**Diagnosis:** the data model accidentally makes completed one-time reminders permanent
(§4.1), and the done-row → Details path makes recurring completion a series-delete footgun
(§6). Both must be fixed together.

**Recommended final behaviour (simplest + safest):**

1. **One-time completed:** show in «أنجزته اليوم» for the remainder of the day with undo,
   then **auto-prune the reminder row**. Concretely: in `ReconcileWorker` / on next launch,
   delete reminders where `isDone && schedule is one-time && completedAt older than ~24h`.
   The `COMPLETED` occurrence record (which already preserves the title) remains as the
   durable trace. Net effect for the user: "you did it → it shows today → it's gone."
   This eliminates the unreachable-undeletable state and the DB junk in one move.
2. **Recurring completion is never destructive to the series.** Change the «أنجزته اليوم»
   done-row so tapping it does **not** navigate into Details-with-delete. Give it only an
   **undo** affordance (or make it inert). Series deletion stays an explicit, clearly
   labelled Details action.
3. **Do not add an archive or a history screen.** For a personal reminder app it is weight
   without daily value, and the removed Log screen was the right call. If any history is
   ever wanted, it belongs behind Settings, not on the home.
4. **No bulk-cleanup UI needed** once auto-prune exists.

Rejected alternatives: a permanent "completed" list (turns the app into a to-do/history
app), manual-only deletion (leaves the unreachable state), keeping rows forever (DB rot).

---

## 8. Calendar & Hijri simplification recommendation

Most of the user's item #2 is **already done** in the current code — this must be stated
honestly rather than re-flagged as open:

- `BalFormats.hijriContext(date)` already returns **month + year only**: «يوافق شهر صفر
  ١٤٤٨ هـ تقريبًا». No calculated day number. **[verified]**
- Compact reminder cards already show **no Hijri at all** (row `whenLabel` is time /
  relative / date-time only). **[verified]**
- The home header shows the light Hijri line as a de-emphasized `bodySmall` secondary. **[verified]**

**What actually remains:**

- **Legacy day-precise Hijri [legacy].** `BalFormats.scheduleSummary` still renders
  `Schedule.OnceHijri` via `hijriDateText(year, month, day)` → «١٥ شعبان ١٤٤٨هـ», i.e. an
  authoritative-looking Hijri **day** number. This only appears for reminders migrated
  from before the Gregorian-only switch (the editor/parser can no longer create them), on
  the alarm/details schedule summary. **P2:** normalize these summaries to Gregorian +
  the month/year context line, or drop the Hijri day.
- **Editor Hijri hint:** the once-date editor shows `hijriContext` (month+year) under the
  date. This is fine as light context; keep.

**Recommended final Hijri display:** month + year context only («يوافق شهر … هـ تقريبًا»),
present on the home header (and optionally the editor date), **hidden on cards**, and the
day number removed from the one remaining legacy path. This is essentially the current
state plus fixing the legacy summary.

---

## 9. Arabic language audit

Overall the rewritten strings are simple, formal, and Saudi-appropriate. Specific findings:

- **Em dash present [verified].** `editor_apply_understood`: «فهمت موعدك: %s — اضغط
  للتطبيق» contains «—». Violates the no-em-dash rule. Replace (e.g. «فهمت موعدك: %s،
  اضغط للتطبيق» or split the sentence).
- **Greeting is the weakest copy [verified].** «صباح النور» / «مساء النور» is the *reply*
  form (the answer to «صباح الخير»), used here as an opening line, which reads slightly
  off, is conversational, repeats on every open, and carries no task value — yet it is the
  **largest** element in the header (`headlineMedium`). See §13 for the heading strategy.
- **Name repetition [verified].** «رَنّة» appears as app name, tagline, `about_description`
  ("رَنّة تطبيق…"), and philosophy in quick succession on About; trim one.
- **Tone/consistency:** good. No dialect in core actions; canonical terms (تأجيل، تم،
  اسحب للتأكيد، إيقاف مؤقت، إنهاء التكرار، يحتاج تأكيدك) are used consistently.
- **Developer name ordering [verified].** `about_developer_name` = «محمد المطيري»
  (correct, first-name-first). It is a standalone pure-Arabic run, so RTL renders it
  correctly; no Latin/digits to reverse.
- **Numerals/dates [verified].** Arabic-Indic numerals via `arabicDigits`; day-parts
  (فجرًا/صباحًا/ظهرًا/عصرًا/مساءً) are natural; the «تقريبًا» hedge on Hijri is well judged.
- **«يحتاج تأكيدك» is applied to snoozed items [verified].** A *language* symptom of the
  §4 logic bug: a postponed reminder is shown under the heading «يحتاج تأكيدك» with a
  «قبل X» (ago) time, which is simply wrong wording for a "coming in X minutes" state.

No ASCII commas/semicolons were found inside Arabic values. No administrative register.

---

## 10. Typography audit

- **Family:** IBM Plex Sans Arabic, 4 weights (Regular/Medium/SemiBold/Bold), bundled,
  SIL OFL 1.1 (correctly attributed on Licenses, not named on About). **[verified]**
- **Scale/line-height:** the last pass bumped body sizes a step (bodyLarge 17sp, bodyMedium
  15sp) with generous line-heights (Arabic needs it). Hierarchy is coherent. Large-font
  scaling is mostly safe because the alarm/editor/settings all scroll. **[verified]**
- **Honest critique:** IBM Plex Sans Arabic is **highly readable but visually neutral**. It
  does not give رَنّة a *distinctively Saudi* character — it reads as a competent corporate
  sans. It satisfies "readable, licensed, multi-weight, good numerals," but it does **not**
  meaningfully advance the "Saudi identity" goal; it mainly changes appearance from the
  previous font. A more characterful, OFL-licensed contemporary Arabic face (with equally
  clear numerals) would strengthen identity — but this is a **deliberate trade** (identity
  vs. proven neutrality/readability for older eyes), not a bug. **P2, optional.**
- **Mixed Arabic/Latin:** minimal Latin in-app (version string uses Arabic-Indic digits;
  license names are Latin proper nouns, acceptable). No clipping risk observed statically
  (chips use `FlowRow`).

---

## 11. About-screen audit

- Developer name is exactly **محمد المطيري** [verified].
- Order is sound: identity → description → philosophy → developer (label/name/role/note)
  → Privacy / Licenses links → version. Credible, non-promotional.
- **Privacy claim is verifiable [verified]:** the manifest declares **no INTERNET
  permission**, so «تعمل محليًا، بلا إنترنت» is truthful, not marketing.
- No font name, no framework/library names, no placeholder links, no fake contact actions
  — correct.
- **Minor:** name repetition (§9); the philosophy line slightly overlaps the description in
  meaning — could merge to one tighter paragraph.
- No excessive cards; hierarchy is clean.

Verdict: About is the **strongest screen** in the app. Leave it essentially as is; only
trim the repetition.

---

## 12. Settings audit

Current controls: theme (تلقائي/نهاري/ليلي), default snooze (5/10/15/30), permissions
link, about link. This is already minimal and correct. Findings:

- **Stale permission status [verified].** `SettingsViewModel.refreshPermissions()` exists
  but has **no caller**, and `SettingsScreen` has no `ON_RESUME` observer. So the
  «كل شيء جاهز» / «يحتاج انتباهك» subtitle is computed once at VM creation and never
  updates after the user grants a permission and returns. Either wire `refreshPermissions`
  to `ON_RESUME` (as `ChecklistScreen`/`PermissionsScreen` already do) or delete the dead
  method and compute inline. **Bug + dead code.**
- **Duplicated snooze choices [verified].** `Reminder.SNOOZE_CHOICES = listOf(5,10,15,30)`
  is unused; `SettingsScreen` hard-codes the same literal. Consolidate onto the constant.
- No dead options, no excessive explanation, nothing to move to "advanced." Settings is
  appropriately small.

---

## 13. Visual & interaction audit

- **Greeting dominates the header [verified].** The reply-form greeting is `headlineMedium`
  (26sp) above a `bodyMedium` date — the least useful text is the largest. Invert this:
  make the **date** (or a task summary) the hero and drop the greeting (§ summary).
- **Completed cards [verified].** Done rows use the same height/shape as active rows,
  line-through title in `onSurfaceVariant`, a filled primary ring with an 18dp check.
  Readability is fine; the problem is **quantity and permanence of framing**, not the
  single-row design. As you complete more, «أنجزته اليوم» grows and the home tips from
  "what's next" toward "what I did" — alarm-history/checklist feel. Recommend **collapsing
  «أنجزته اليوم» to a count line** («أنجزت ٣ اليوم») that expands on tap, or capping it,
  so the top of the list stays "قادم / يحتاج تأكيدك."
- **Strike-through on Arabic** is legible here because the row is already de-emphasized,
  but strike-through on Arabic script is generally lower-value than a check + muted color;
  consider relying on the filled ring + muting rather than the line.
- **Snoozed items render as «قبل X» [verified]** — visually a past event when it is a
  future one (§4/§9).
- **Paused reminders vanish [verified]** — no visual marker; a paused reminder is
  indistinguishable from a deleted one on the home.

---

## 14. Accessibility risks (static)

- **Icon-only destructive control [verified].** Delete in Details (and Editor) is an
  icon-only top-bar trash. It has a `contentDescription` and a confirm dialog, so it is
  *acceptable*, but "delete" as icon-only is the riskiest of the icon-only controls.
- **Gesture-only completion — mitigated [verified].** Home swipe-to-complete has a tappable
  `CompleteRing` fallback with a proper `contentDescription`/`Role.Button`; the alarm slide
  has an `onClick` semantics fallback. Good — no completion is gesture-only.
- **Touch targets [verified].** Chips and steppers use `heightIn(min = 48.dp)`; the
  complete ring is 30dp visual inside a larger row — the ring's own tap target is on the
  small side (30dp < 48dp) though the whole row is also tappable.
- **Contrast:** done text `onSurfaceVariant` on `surfaceContainer` — likely borderline for
  WCAG AA at `bodyMedium`; **cannot be confirmed without rendering** (see §22).
- **Large-font clipping:** low risk (scrollable screens, `FlowRow` chips), not confirmable
  statically for the alarm hero row at extreme scale.
- **RTL gestures [verified].** Both slide and swipe flip the drag sign for RTL and use
  `placeRelative`/layout mirroring correctly.

---

## 15. Code architecture & cleanup risks

- **Dead code [verified]:** `AlarmRingerService.isRingingFor` (no caller),
  `SettingsViewModel.refreshPermissions` (no caller), `Reminder.SNOOZE_CHOICES` (unused),
  `strings/action_snooze` (unused).
- **Dead-ish domain [verified]:** `OccurrenceStatus.SKIPPED` and `.IGNORED` are **never
  produced** anymore (no code path writes them); they survive only to render legacy
  records. `MISSED` is produced by reconcile but only visible in Details history.
  `OccurrenceStatusUi` still maps all four. Candidate for narrowing once legacy data
  concerns are weighed.
- **Unused compat table [verified]:** `PendingConfirmationEntity` remains in the schema
  and `@Database` but is never read/written — kept only so the v3/v4 DB validates. This is
  intentional but should be documented as "retained for schema parity, unused."
- **Partially-dead calendar surface [verified]:** `CalendarSystem` +
  `Schedule.OnceHijri/HijriMonthly/HijriYearly` + their `RecurrenceCalculator`/`Mappers`/
  `BalFormats` branches exist solely for migrated data. Correct for compatibility, but it
  is a standing maintenance cost and a source of the §8 legacy day-number.
- **Single source of truth is mostly clean [verified]:** one `Reminder` model, one
  scheduler, one completion method. The exception is the home checklist deriving state
  **without** consulting `snoozedUntil`, creating a second, disagreeing "truth" about
  postponed reminders (§4/§5).
- **Coupling:** ViewModels depend directly on `ReminderScheduler` (fine); the scheduler is
  cleanly seam-tested via `AlarmGateway`/`ReminderNotifications` fakes. No problematic
  UI↔scheduling coupling.
- **Migrations [verified]:** v1→v4 are additive/table-rebuild and look safe; the recreate
  in `MIGRATION_3_4` copies every surviving column. No unsafe migration found. Adding
  auto-prune (§7) is a *runtime* behaviour, not a migration, so it carries no schema risk.

---

## 16. Contradictions & hidden edge cases

1. **Home vs. Details disagree on snoozed reminders** (pending vs. مؤجل). **[verified]**
2. **Completed one-time reminder = invisible + undeletable** after 24h. **[verified]**
3. **Done-row tap → series delete** for recurring reminders. **[verified]**
4. **Snoozed completion uses a different occurrence key** than home completion. **[verified]**
5. **Paused reminder is invisible** on the home (same as deleted). **[verified]**
6. **"يحتاج تأكيدك" is not durable** — it evaporates at the day boundary rather than being
   resolved or recorded. **[verified]**
7. **Post-process-death alarm** shows "ringing" with no sound. **[verified]**
8. **MISSED records** accumulate but are essentially invisible. **[verified]**
9. **Completing a future-today occurrence early** is allowed from the home (today section
   is completable) — intended, but worth a conscious confirm that "tick tonight's dose at
   noon" is desired behaviour. **[verified]**

---

## 17. Features / concepts that should be removed

- The **greeting** line («صباح النور»/«مساء النور»). Replace with a functional heading.
- **Dead code:** `refreshPermissions` (or wire it), `isRingingFor`, `SNOOZE_CHOICES`
  duplication, unused `action_snooze` string, the em dash.
- The **permanent-completed-one-time** rows (auto-prune, §7).
- The **done-row → Details-delete** navigation for recurring reminders (make it undo-only).
- **Consider** narrowing `OccurrenceStatus` to what is actually produced (COMPLETED +
  MISSED), keeping SKIPPED/IGNORED only if legacy DB rendering must be preserved.
- **Do not** add: history screen, archive, categories, priorities, alarm-tone pickers,
  multi-snooze menus, or a second greeting variant.

## 18. Features that should be preserved

- The full-screen alarm + `SlideToConfirm` two-step completion (safety is correct).
- One `ReminderScheduler` lifecycle, idempotent records, reboot/reconcile resilience.
- Gregorian-only scheduling with light Hijri context; `RecurrenceCalculator`.
- DB v4 + migrations + legacy Hijri firing (data compatibility).
- The About and Permissions/Readiness screens (both strong).
- Home swipe-to-complete **as an affordance** (but de-emphasize the done section).
- The bell identity and teal/brass system.

---

## 19. Recommended final product structure

- **Home = a calm reminder list, not a checklist.** Header hero = the date (weekday +
  Gregorian), light Hijri month/year beneath; no greeting. Sections: **«يحتاج تأكيدك»**
  (rang, unresolved) → **«اليوم»** → **«قادم»**. Completion stays available (swipe/ring),
  but **«أنجزته اليوم» collapses to a one-line count** that expands on demand. Snoozed
  reminders appear under «قادم» at their snoozed time (not «يحتاج تأكيدك»).
- **Alarm = تأجيل / تم→slide.** Unchanged.
- **Editor = بماذا أذكّرك؟ → متى؟ → الوقت → (ملاحظة).** Unchanged.
- **Details = manage one reminder:** edit, pause/resume, end-series, delete, small history.
  Done-row from home no longer routes destructive delete here by accident.
- **Settings = المظهر / مدة التأجيل / الأذونات / عن رَنّة**, with live permission status.
- **Background:** auto-prune finished one-time reminders; keep reconcile.

## 20. Prioritized implementation plan

**P0 — critical (correctness / data integrity):**
1. Make completed one-time reminders reachable-then-pruned (§7); guarantee they are never
   permanent/undeletable.
2. Fix the snooze state truth: home must read `snoozedUntil` so postponed reminders show
   as «قادم» (or a «مؤجل» marker) at the snooze time, never «يحتاج تأكيدك / قبل X».
3. Remove the recurring done-row → series-delete footgun (undo-only done rows).
4. Surface **paused** reminders on the home (a muted row/marker) instead of hiding them.

**P1 — important (coherence / polish / hygiene):**
5. Replace the greeting with a date/task-summary heading; invert header hierarchy.
6. Collapse/cap «أنجزته اليوم».
7. Wire `refreshPermissions` to `ON_RESUME` in Settings (or remove + inline).
8. Remove the em dash; trim About name repetition.
9. Delete dead code (`isRingingFor`, `SNOOZE_CHOICES` dup, `action_snooze`).
10. Reconcile the snooze vs. home completion occurrence-key.

**P2 — optional (identity / long-term):**
11. Normalize the legacy `OnceHijri` schedule summary to drop the day-precise Hijri.
12. Narrow `OccurrenceStatus` to produced values (weigh legacy-record rendering).
13. Reconsider the typeface for a more distinctly Saudi identity (OFL-licensed).
14. Fix the post-process-death "ringing with no sound" cosmetic.

## 21. Decisions requiring no further discussion

- Completed one-time reminders **must not** be permanent/undeletable — auto-prune after
  their visible day; the `COMPLETED` record is the durable trace.
- The home **must not** label a snoozed reminder «يحتاج تأكيدك».
- The recurring done-row **must not** be a one-tap path to series deletion.
- The greeting «صباح النور» is removed; the header leads with the date.
- Hijri stays **month + year only, «تقريبًا»**, hidden on cards. (Already true except the
  legacy summary.)
- No history/archive/log screen is reintroduced.
- Developer name stays exactly **محمد المطيري**.
- Delete the confirmed dead code and the em dash.

## 22. Risks that cannot be confirmed without runtime testing

- **Contrast ratios** of done/muted text and the Hijri secondary line (WCAG AA) —
  needs rendered measurement.
- **Full-screen-intent + lock-screen** behaviour on real OEMs (Samsung/Xiaomi battery
  managers) and the fallback path when the ringer service is blocked.
- **DND / alarm-stream** interaction (silent mode vs. alarm channel) on real devices.
- **Large-font (200%+) layout** of the alarm hero row and the editor steppers.
- **Actual auto-prune timing** and undo-window interplay once implemented.
- **Migration on a real pre-v4 database** with legacy Hijri rows firing correctly.
- Whether the two-step alarm completion (tap تم → slide) is learnable for the target
  older audience — a usability question, not a code question.

---

## Concise summary

**Five most serious problems**
1. Completed one-time reminders become invisible **and** permanent/undeletable (DB junk).
2. Snoozed reminders are mislabelled «يحتاج تأكيدك / قبل X» on the home (home vs. Details disagree).
3. Tapping a recurring reminder's «أنجزته اليوم» row leads to a delete that nukes the whole series.
4. Paused reminders vanish from the home with no marker (indistinguishable from deleted).
5. Identity drift: the home reads as a to-do checklist rather than a reminder list that rings.

**Five highest-value improvements**
1. Auto-prune finished one-time reminders (fixes #1 with zero new UI).
2. Make the home honour `snoozedUntil` (one correct source of truth).
3. Make done-rows undo-only (kills the delete footgun).
4. Replace the greeting with a date/summary heading; collapse «أنجزته اليوم».
5. Fix the stale Settings permission status + delete the confirmed dead code / em dash.

**What should be deleted**
The greeting; permanent completed-one-time rows; the done-row→series-delete path; dead
code (`isRingingFor`, `refreshPermissions` or its non-wiring, `SNOOZE_CHOICES` dup,
`action_snooze`); the em dash; (optionally) the never-produced SKIPPED/IGNORED statuses.

**What should remain**
The alarm + slide-to-confirm; the single scheduler + idempotent records + reconcile;
Gregorian scheduling with light Hijri context; DB v4 + legacy Hijri firing; the About and
Permissions screens; the bell/teal/brass identity; swipe-to-complete as an affordance.

**Recommended final behaviour for completed reminders**
One-time: visible in «أنجزته اليوم» today with undo, then auto-pruned (record kept as
history trace). Recurring: completion never touches the series; done-rows offer undo only;
series deletion stays an explicit Details action.

**Recommended final Hijri display**
Month + year only — «يوافق شهر صفر ١٤٤٨ هـ تقريبًا» — on the header (and optionally the
editor date), hidden on compact cards; remove the day number from the one remaining legacy
schedule-summary path.

**Recommended greeting / heading strategy**
Remove «صباح النور»/«مساء النور». Lead the header with the **date** (weekday + Gregorian)
as the hero, with an optional single functional line («لديك ٣ تذكيرات اليوم» / «لا تذكيرات
اليوم»). No time-of-day greeting.

**Recommended next implementation phase**
A tight **P0 stabilization pass**: (1) completed-item lifecycle + prune, (2) snooze/paused
state truth on the home, (3) remove the done-row delete footgun, (4) header/greeting + dead
code cleanup. No new features. After P0, the app is coherent and stabilizable.
