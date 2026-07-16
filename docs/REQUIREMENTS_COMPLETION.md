# رنّه: Core completion requirements (2026-07-16 expansion)

These requirements were added to the completion scope on 2026-07-16 and are **core product
requirements, not optional enhancements**. The final completion report must account for each
section. Labels below are canonical: screens, notifications and docs must all use the same words.

## R1. Completion, dismissal and snooze experience

Canonical action vocabulary (never mix, never use ambiguous «انتهى»):

| Action | Label | Meaning |
|---|---|---|
| Complete | **تم** | The task/obligation of the current occurrence is done |
| Stop | **إيقاف** | Stop an actively ringing alarm sound. Not completion |
| Snooze | **تأجيل** | Postpone the current occurrence by N minutes |
| Skip once | **تخطي هذه المرة** | Skip only the current occurrence of a recurring reminder |
| End series | **إنهاء التكرار** | Stop all future occurrences of a recurring reminder |

Rules:

- **تم** is the primary action wherever a reminder represents a task or obligation
  (cards, details, notifications). Snooze stays easy to reach but never dominant.
- Completing/skipping one occurrence must never delete or disable the series:
  occurrence-level state is persisted per (reminder, occurrence) with a status of
  completed / skipped / missed.
- **إنهاء التكرار** and deletion are series-wide: they require explicit confirmation.
  Reversible actions (تم، تأجيل، تخطي) never ask for confirmation; accidental completion is
  undoable through a clear Arabic undo affordance (تراجع) for a short period.
- Stopping an alarm sound (إيقاف) is not completion. After stopping, offer completion as a
  follow-up when the distinction is meaningful; a per-reminder option can declare
  "stopping counts as completing" explicitly.
- All action handling is idempotent: duplicate notification taps or duplicate Android intents
  must not double-log, double-advance or corrupt state.
- Large touch targets, clear Arabic labels, accessible semantics everywhere.

Notification action order: standard reminder = [تم] [تأجيل] (+ [تخطي هذه المرة] when recurring),
tap opens details. Active alarm = [إيقاف] [تأجيل].

## R2. Real Alarm Mode

Two clearly differentiated alert modes per reminder:

1. **تنبيه عادي** (standard): high-importance notification, configurable sound/vibration,
   complete + snooze actions, opens details.
2. **منبّه مهم** (alarm): a real clock-alarm experience:
   - exact scheduling via `AlarmManager.setAlarmClock` (surfaces as the system's next alarm);
   - dedicated alarm notification channel (channel silent; sound owned by the service);
   - foreground service (`systemExempted` type, held via `SCHEDULE_EXACT_ALARM`) plays the
     user-chosen system alarm ringtone on the alarm stream, looping, with vibration pattern,
     optional gradual volume ramp, and a configurable timeout;
   - full-screen `AlarmActivity` over lock screen / screen-off with big **إيقاف** and **تأجيل**;
   - configurable snooze duration; optional single re-alert if ignored;
   - on timeout: stop ringing, log the occurrence as missed, post a fallback high-priority
     missed notification;
   - reboot/process-death recovery through DB-derived rescheduling; a missed alarm within the
     grace window rings on boot, otherwise falls back to the missed notification;
   - when full-screen intents are unavailable (API 34+ permission revoked), fall back to a
     heads-up notification while the service still plays sound, and tell the user;
   - ringtone picker limited to local system alarm sounds with a preview before saving.

Platform compliance (verified against official docs, July 2026):

- Exact alarms: `SCHEDULE_EXACT_ALARM` (narrow, user-revocable; requested via
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). `USE_EXACT_ALARM` is deliberately not used.
  Denial degrades to windowed inexact alarms and the UI says so.
- FGS: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, type `systemExempted`,
  started from the exact-alarm broadcast (an allowed background-start exemption).
- Full-screen intent: `USE_FULL_SCREEN_INTENT`; on API 34+ check
  `NotificationManager.canUseFullScreenIntent()` and deep-link to
  `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` from the permissions screen.
- No DND-access request, no notification-policy bypass: alarm audio uses the alarm stream
  (audible in silent mode and under default DND alarm exceptions). The permissions screen
  explains that muted alarm volume, DND alarm settings and OEM battery restrictions can
  affect behavior.

Faking alarm mode with an ordinary notification is prohibited.

## R3. Alert mode selection

In the editor, the user picks the alert style in plain Arabic (no Android jargon):

- **تنبيه عادي:** يظهر كإشعار ويمكن إكماله أو تأجيله.
- **منبّه مهم:** يصدر صوتًا مستمرًا حتى توقفه أو تؤجله.

Category-based suggested defaults (medication, waking up, work clock-in, critical
appointments suggest alarm mode; bills and low-priority tasks default to standard),
always shown transparently as a suggestion the user can reverse, never silently applied.

## R4. Hijri and Gregorian date selection

The calendar system (**التقويم الهجري** / **التقويم الميلادي**) is part of the reminder's
scheduling semantics, not a display preference:

- Calendar selector inside creation/editing for date-bearing schedules (once, monthly, yearly).
- The chosen calendar is stored and preserved; recurrence is computed in that calendar.
  A Hijri reminder is never silently converted to Gregorian recurrence, nor vice versa.
- Primary date shown in the chosen calendar with the equivalent in the other calendar as
  secondary information, labeled «تقريبًا» for computed Hijri conversions (Umm al-Qura tables,
  user adjustment ±2 days applies to both display and scheduling).
- Arabic month names, Arabic-Indic numerals, RTL-safe, large-text-safe pickers;
  one picker at a time (no dual full pickers).
- Supported recurrences: one-time Hijri/Gregorian, monthly Hijri/Gregorian,
  yearly Hijri/Gregorian, with month-end clamping (29/30 Hijri, 28..31 Gregorian),
  leap years, nonexistent-day handling, calendar-system editing after creation,
  timezone/device-date changes, reboot rescheduling.
- When a recurrence cannot be represented reliably the UI explains the limitation and offers
  the closest explicit alternative rather than silently changing meaning.

## R5. Reminder creation redesign

Progressive disclosure. Primary flow: (1) what, (2) when, (3) which calendar,
(4) repeated?, (5) عادي أم منبّه مهم. Secondary customization lives in an expandable
section: sound/ringtone, vibration, snooze duration, re-alert if ignored,
stop-counts-as-completed, category, priority, notes.

Before saving, a concise Arabic summary states exactly what will happen (schedule, calendar
basis, alarm behavior). Saving is blocked until the schedule is explicit and valid.

## R6. Personalization (local, private)

Locally learned, user-controllable shortcuts: preferred alert mode per category, preferred
snooze duration, recent ringtone, preferred calendar per type. Learned values appear as
transparent optional suggestions in the editor; critical behavior never changes without
explicit user confirmation. Nothing leaves the device.

## R7. UX audit items

Fix while implementing: icon-only complete buttons on cards; unclear occurrence status
(completed/skipped/snoozed/missed must be visible in details history and the log); weak
post-save feedback; label consistency between screens and notifications; visibility of the
next alarm and of exact-alarm permission; duplicate alarms after editing; alarm sound
continuing after dismissal; multiple alarm screens for one occurrence; repeated notification
actions processed more than once (idempotent handling).

## R8. Verification

Automated tests (JVM): completion vs dismissal, occurrence vs series, skip, end-series,
undo, snooze + repeated snoozes, duplicate intents, alarm timeout policy, standard vs alarm
mode fire paths, Hijri once/monthly/yearly, conversion boundaries (29/30, month-end, leap),
calendar-system editing, reboot rescheduling, exact-permission denial fallback.

Real-device/emulator verification checklist (blocked inside the CI sandbox; run externally):
alarm sound, lock-screen and screen-off behavior, notification actions, FSI fallback,
snooze/completion flows, recurring occurrence handling, Hijri and Gregorian creation, large
Arabic text, light/dark themes. Compilation alone never marks these requirements complete.

Documentation must record: the final interaction model, stop-vs-complete distinction,
implemented alarm behavior, per-Android-version limitations and fallbacks, Hijri/Gregorian
semantics, tested configurations, known OEM limitations, remaining policy/platform constraints.
