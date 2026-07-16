# رنّه (Ranna): Product, Design & Architecture Decisions

> «كل موعد يهمّك… توصلك رنّته في وقتها»

## 1. Product analysis

**The problem.** Arabic speakers who want a reminder app today choose between English apps with
broken RTL and machine-translated strings, or heavyweight task managers built around projects and
checklists. Neither lets you type a sentence the way you'd say it — «ذكّرني كل يوم الساعة 9 ببصمة
الدوام» — and trust that a notification will actually fire at 9:00.

**Target users.** Employees (clock-in, meetings, bills), students (lectures, revision), and anyone
managing medication or family routines. Arabic-first, phone-first, no interest in "productivity
systems".

**Core scenarios (daily).**
1. Type one Arabic sentence → confirm interpretation → done.
2. Notification fires at the exact minute → complete or snooze from the notification itself.
3. Glance at today: what's next, what's left.
4. Edit or silence a recurring reminder without breaking its schedule.

**North-star principle.** One sentence in, one reliable notification out. Everything else serves that.

## 2. First-release scope

### In v1
| Feature | Why |
|---|---|
| Arabic NL input (rule-based, local) | The core differentiator; no network, no AI dependency |
| Interpretation preview before saving | Trust — the user must see what the app understood |
| Manual form (date, time, recurrence, weekdays, monthly, category, priority, snooze, sound/vibration) | Fallback and fine-tuning |
| Templates (بصمة الدوام، دواء، فاتورة، ماء، موعد، مذاكرة، اتصال) | Zero-typing creation for the most common reminders |
| One-time / daily / weekly-on-days / monthly / relative recurrence | Covers the real usage matrix |
| Complete & snooze from the notification | The notification *is* the primary UI |
| Exact alarms via AlarmManager + reboot/time-change rescheduling | Reliability is the product |
| Permission guidance screen (notifications, exact alarms, battery) | Modern Android makes or breaks reminder apps |
| Search + category filter, duplicate reminder | Cheap, high-value list management |
| Completed log (سجلّ الإنجاز) | Closure + archive; also the substrate for future stats |
| Light/dark themes, full RTL, Arabic plurals & date formatting | Table stakes for the identity |

### Postponed (and why)
- **Home-screen widget, Quick Settings tile, app shortcuts** — high value, but each is a separate
  surface to design well; v1.1 candidates. The architecture (repository + scheduler as singletons)
  already supports them.
- **Voice input** — Android's Arabic SpeechRecognizer feeds the same parser; deferred until the
  text parser has real-world mileage.
- **Location reminders** — different permission model (background location) and battery profile;
  a separate epic.
- **Stats & streaks** — the completions table already records everything needed; ship the UI later.
- **Hijri calendar** — genuinely valuable for this audience; needs careful dual-calendar UX, v1.2.
- **Backup/export, sync** — repository interface keeps the door open; no provider coupling today.
- **Nagging/repeat-until-confirmed notifications** — useful for medication; needs its own
  escalation design so it doesn't become an alarm-clock clone.

## 3. Technical architecture

Single Gradle module (`:app`), packages by layer — Clean Architecture boundaries without
multi-module ceremony:

```
com.bal.reminders
├── domain/        pure Kotlin: models, Recurrence, RecurrenceCalculator, repository interface
├── parser/        ArabicReminderParser (isolated; replaceable behind ReminderParser interface)
├── data/          Room (entities, DAO, converters), RepositoryImpl
├── scheduling/    ReminderScheduler (AlarmManager), receivers, NotificationPresenter, reconcile Worker
└── ui/            Compose: theme, navigation, screens, viewmodels
```

- **MVVM + unidirectional data flow.** ViewModels expose `StateFlow` of immutable UI state.
- **Hilt** for DI — receivers, workers and ViewModels all need the same singletons.
- **Room** stores reminders + a completions log. Recurrence persisted as discrete columns
  (type, time, days bitmask, dayOfMonth) — queryable, no JSON blobs.
- **`java.time` everywhere** (minSdk 26 → no desugaring). All scheduling math is in
  `RecurrenceCalculator`, a pure function `nextOccurrence(recurrence, after)` — heavily unit tested.
- **Parser isolation.** `parser/` depends only on `domain` types and exposes
  `parse(text, now): ParseResult`. Swappable for an on-device model later without touching domain.

### Scheduling model (the reliability core)
- **One alarm per reminder** — only the *next* occurrence is ever scheduled. On fire, the receiver
  shows the notification and schedules the following occurrence. No alarm fan-out, no duplicates.
- `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()`; otherwise a windowed alarm
  (±10 min) **and a visible status banner** telling the user exact alarms are off.
- PendingIntent identity = reminder id (`FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`) → editing a
  reminder atomically replaces its alarm; deleting cancels it.
- **Receivers:** `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` → full
  reschedule pass from the DB (source of truth is Room, never the alarm table).
- **WorkManager only for non-exact work:** a daily reconcile job that re-verifies every active
  reminder has its alarm registered (defence-in-depth against OEM battery killers) — never for
  user-facing trigger times.
- **Snooze** re-registers the same reminder id with `snoozedUntil`; recurring reminders keep their
  base schedule intact underneath.
- Missed occurrences (device off past trigger): fire once on reschedule if <
  a grace window, else skip forward — never a flood of stale notifications.

### Risk register
| Risk | Mitigation |
|---|---|
| POST_NOTIFICATIONS denied (13+) | Onboarding request + persistent, calm in-app status card; app remains usable as a list |
| SCHEDULE_EXACT_ALARM revoked (14+ default-off for new installs of non-clock apps) | `USE_EXACT_ALARM` is not appropriate; request `SCHEDULE_EXACT_ALARM` via settings intent, degrade to windowed alarms with clear status |
| Doze / OEM battery killers | `setExactAndAllowWhileIdle`, reconcile worker, optional battery-optimization exemption guidance (never nagging) |
| Reboot loses alarms | Boot receiver + `nextTriggerAt` persisted in DB |
| Time/timezone change | Receivers recompute from wall-clock local time (reminders are wall-clock semantics: "9 صباحًا" means 9 AM wherever you are) |
| Duplicate schedules | Single alarm per id; scheduling is idempotent (same request code) |

## 4. Visual identity: هوية «رنّه»

**Name.** «رنّه» (renamed from «بال» on 2026-07-15, product decision). From «رنّة» (a ring, a
chime): the sound a reminder makes when it arrives, written with the shadda. The app's voice
speaks Arabic natively: «وش أذكّرك فيه؟», not a translation of "What should I remind you of?".
House style: never write the name as «بال», and never use the em dash in user-facing copy.

**Palette — «ليل ونهار» (night & day).** Time is the product; the palette is the day cycle,
not a corporate blue:
- **نهار (light):** warm sand background `#FAF6EF`, ink-indigo text `#1F2430`, saffron accent
  `#C77E23`, deep teal secondary `#2A6B6B`.
- **ليل (dark):** deep night `#151A26`, warm off-white text `#EFE9DD`, amber accent `#E0A458`,
  soft teal secondary `#7FB5B5`.

**Typography.** IBM Plex Sans Arabic (SIL OFL) — designed for interfaces, excellent Arabic-Latin
harmony, true weights (Regular/Medium/SemiBold/Bold). Numerals: Arabic-Indic in flowing text is a
per-locale formatting decision; times shown with `NumberFormat` for `ar` locale.

**Motif.** The bell-clapper dot: a single amber circle "moment", used as the icon's clapper,
list bullet accents, and the progress dot in onboarding. No arabesque, no mashrabiya clichés.

**Icon/logo.** Adaptive vector icon: a calm stroked alarm bell with two sound-wave strokes (the
رنّة) and the amber clapper dot on the night-indigo field. Drawn as VectorDrawable; no licensed
assets anywhere, fonts are OFL.

**Motion.** Short, physical, restrained: crossfade between tabs, RTL-aware slide for stacked
screens, gentle spring on the check-complete action. Nothing decorative.

## 5. Quality bar
- Unit tests: `RecurrenceCalculator` (month-end, midnight, weekly sets, past times, TZ), the full
  Arabic parser matrix (dialect variants, ص/م, mixed numerals), scheduler idempotency via fakes.
- All UI strings in `res/values/strings.xml` (Arabic is the default locale), proper plurals.
- Compose previews for major screens; `lint` and `assembleRelease` must pass clean.
