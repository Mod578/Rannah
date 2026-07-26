# رَنّة — Final Completion Report

**Phase: coherence, refinement and release hardening.**

The two-scope model established in the previous phase is unchanged: «اليوم» acts
on the current occurrence, «التذكير كله» acts on the reminder. This pass removed
the remaining ambiguity around it — crowded rows, a hardcoded "tomorrow", two
vocabularies for one state, and a report that miscounted its own screen.

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
