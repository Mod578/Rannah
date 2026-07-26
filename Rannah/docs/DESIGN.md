# رَنّة — Design & Architecture

رَنّة (*rannah*, "a ring/chime") is an offline Arabic reminder app: write what you
need, choose when, save, and confirm it when it's done. One reliable, calm, RTL
Arabic experience — no accounts, no network, no clutter. Every reminder **rings**;
the name is the promise.

## Product model (deliberately small)

A reminder has a **title**, a **schedule**, and an optional **note**. That is all a
user sets — no categories, priorities, templates, or tags. Scheduling is Gregorian;
the Hijri calendar appears as a full companion date («١١ صفر ١٤٤٨ هـ») beside the
Gregorian one, and still drives legacy migrated data.

Reminders surface as a calm **checklist of occurrences**. For each reminder, رَنّة
derives the current occurrence from the schedule via the single
`OccurrenceStateResolver`, pairs it with the completion log, and files it into one
plain section:

- **يحتاج تأكيدك** — rang and still unconfirmed. Needs you now.
- **مؤجل** — snoozed; shows the snooze time.
- **اليوم** — due later today.
- **قادم** — the next occurrence of everything else, nearest first.
- **أنجزته اليوم** — confirmed today (collapsed to a count that expands on demand).
- **متوقفة** — paused reminders, still visible so they can be resumed.

Every reminder appears once. Confirming one occurrence of a recurring reminder never
ends the series. Finished one-time reminders show for the rest of the day, then
auto-prune (the `COMPLETED` record stays as the durable trace).

### Completion

On the home a completion is a **deliberate swipe** on the row (RTL-aware, latched
against double-fire, restrained haptic), with an accessible alternative: tapping the
leading ring (a real `Role.Button`). Both run the same idempotent
`ReminderScheduler.complete`, and both are **undoable** via a snackbar.

## Full-screen alarm

Every reminder rings a real looping alarm over the lock screen (`AlarmActivity`,
`AlarmRingerService`). The screen offers exactly two answers, in the order a sleepy
person meets them: **تأجيل** (large, reversible) and **تم**. تم does not complete —
it hushes the ring and reveals a **slide-to-confirm** phrased as the real claim
(«اسحب للتأكيد»). Only finishing that slide records the occurrence. A background tap,
the back gesture, leaving the screen, or a ring-out never count as completion. While
it rings, the bell mark swings from its loop — the one place motion says "now".

## Scheduling (unchanged, reliable core)

`ReminderScheduler` is the single owner of the lifecycle (fire, snooze, complete,
reschedule, prune). The Room database (v4) is the source of truth; alarms are always
rederivable from it, which is what makes reboot, process death, and time changes
safe. Occurrence records are unique per `(reminder, occurrence, status)`, so replayed
intents and double taps are no-ops. Legacy Hijri schedules still recur via the Umm
al-Qura tables; the app shows their equivalent civil date.

## Visual identity — *a brass bell on stone*

- **Bell mark.** One confident bell: a fine suspension loop on a narrow crown, a body
  that flares to a broad rim, an upward-arching mouth, and a single warm brass clapper
  nested inside that mouth — the ring itself. Drawn from one source (`AppMark`),
  mirrored by hand into the launcher / splash / notification vectors so every surface
  wears the exact same bell. No stock bell, clock, flag, or palm. Cream bell on a
  deep-teal launcher.
- **Colour.** Deep sea-teal primary (`#0B6B5F` / dark `#5FCFBE`) for action and
  confidence; warm brass (`#9A6B1E` / `#E1B667`) for the ring and accents; a warm
  stone canvas by day (`#F4F1EA`), warm charcoal by night (`#1A1915`). Grounded in
  warm neutrals, distinct from generic Material, without flags, swords, palms, or
  pasted heritage patterns.
- **Type.** Tajawal (SIL OFL 1.1), one family, a restrained four-weight hierarchy with
  ExtraBold reserved for the wordmark and the date. Line-heights and body sizes are
  tuned a step up for older eyes and the taller Arabic script; Arabic-Indic numerals
  are clear at large sizes. Latin runs (the version, credits) are bidi-isolated so they
  never reorder inside the RTL page.
- **Shape & motion.** Calm rounded corners on cards and rows (16 dp, tightened from a
  bubblier earlier scale), one selected-state chip everywhere. Motion is feedback only
  — the confirming swipe/slide, the ringing bell's swing, calm push transitions —
  never decoration.

## Structure

`ui/home` checklist · `ui/editor` one short create/edit form · `ui/details` ·
`ui/settings` (+ permissions, about, privacy) · `alarm/` full-screen alarm ·
`scheduling/` scheduler + notifications + alarm gateway · `domain/` model + resolver +
recurrence + Hijri · `data/` Room + DataStore. Layout direction is forced RTL and the
locale is Arabic regardless of the system language.
