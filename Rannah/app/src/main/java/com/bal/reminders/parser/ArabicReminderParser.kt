package com.bal.reminders.parser

import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based Arabic parser. Matching runs on normalized text (see
 * [ConsumableText]); every recognized date/time expression is consumed and the
 * remaining original words become the title. Schedules are Gregorian.
 *
 * Supported grammar (examples):
 * - «ذكرني بكرة الساعة 8 بالاجتماع»            one-time, tomorrow
 * - «ذكرني كل يوم الساعة 9 بالدواء»            daily
 * - «كل أحد وثلاثاء الساعة 6 مساءً النادي»      weekly on days
 * - «بعد نص ساعة الدواء»                        relative
 * - «يوم 25 من كل شهر دفع الفاتورة»             monthly
 * - «يوم الجمعة الساعة 1 الظهر صلاة»            next weekday, one-time
 *
 * AM/PM without a marker uses a daytime heuristic: 7..12 → morning/noon,
 * 1..6 → afternoon/evening; a one-time reminder for today flips to the other
 * half of the day when the first reading has already passed.
 */
@Singleton
class ArabicReminderParser @Inject constructor() : ReminderParser {

    override fun parse(input: String, now: ZonedDateTime): ParseResult {
        val text = ConsumableText(input)
        if (text.text.isBlank()) return ParseResult.NoMatch

        val hadVerb = text.consumeFirst(VERB) != null

        // Order matters: specific/recurring patterns first so their numbers and
        // day names are consumed before generic date/time matching runs.
        val monthlyDay = extractMonthly(text, now)
        val yearly = text.consumeFirst(YEARLY) != null
        val weeklyDays = extractWeekly(text)
        val daily = weeklyDays == null && !yearly && text.consumeFirst(DAILY) != null
        val recurring = monthlyDay != null || weeklyDays != null || daily || yearly

        val relativeAt = if (!recurring) extractRelative(text, now) else null
        val dateRef = if (relativeAt == null) extractDateRef(text) else null
        val onceWeekday = if (relativeAt == null && !recurring && dateRef == null) {
            extractOnceWeekday(text)
        } else {
            null
        }
        val rawTime = extractTime(text)

        val title = cleanTitle(text.remainder(), hadVerb)

        val schedule: Schedule? = when {
            relativeAt != null -> Schedule.Once(relativeAt.toLocalDate(), relativeAt.toLocalTime())
            yearly && dateRef != null -> rawTime?.let { yearlySchedule(dateRef, resolveTime(it), now) }
            monthlyDay != null -> rawTime?.let { Schedule.Monthly(monthlyDay, resolveTime(it)) }
            weeklyDays != null -> rawTime?.let { Schedule.Weekly(weeklyDays, resolveTime(it)) }
            daily -> rawTime?.let { Schedule.Daily(resolveTime(it)) }
            dateRef != null -> rawTime?.let { onceForDate(dateRef, it, now) }
            onceWeekday != null -> rawTime?.let { onceForWeekday(onceWeekday, it, now) }
            rawTime != null -> onceForToday(rawTime, now)
            else -> null
        }

        return when {
            schedule != null && title.isNotBlank() ->
                ParseResult.Success(title, schedule)

            schedule != null ->
                ParseResult.Incomplete(
                    ParseResult.Draft(title = null, schedule = schedule),
                    ParseResult.MissingPart.TITLE,
                )

            else -> {
                // A date or recurrence without a time (or nothing at all):
                // build the best partial schedule with a placeholder time so
                // the editor opens pre-filled.
                val placeholder = LocalTime.of(8, 0)
                val partial: Schedule? = when {
                    yearly && dateRef != null -> yearlySchedule(dateRef, placeholder, now)
                    monthlyDay != null -> Schedule.Monthly(monthlyDay, placeholder)
                    weeklyDays != null -> Schedule.Weekly(weeklyDays, placeholder)
                    daily -> Schedule.Daily(placeholder)
                    dateRef != null -> Schedule.Once(resolveDateOnly(dateRef, now), placeholder)
                    onceWeekday != null ->
                        Schedule.Once(nextDateFor(onceWeekday, now.toLocalDate(), inclusiveToday = false), placeholder)
                    else -> null
                }
                ParseResult.Incomplete(
                    ParseResult.Draft(title = title.ifBlank { null }, schedule = partial),
                    ParseResult.MissingPart.TIME,
                )
            }
        }
    }

    // ---------------------------------------------------------------- pieces

    private fun extractMonthly(text: ConsumableText, now: ZonedDateTime): Int? {
        text.consumeFirstIf(MONTHLY_DAY_OF) { it.groupValues[1].toInt() in 1..31 }
            ?.let { return it.groupValues[1].toInt() }
        text.consumeFirstIf(MONTHLY_EVERY) { it.groupValues[1].toInt() in 1..31 }
            ?.let { return it.groupValues[1].toInt() }
        text.consumeFirst(MONTHLY_LAST_DAY)?.let { return 31 }
        text.consumeFirst(MONTHLY_WORD)?.let { return now.dayOfMonth }
        return null
    }

    private fun extractWeekly(text: ConsumableText): Set<DayOfWeek>? {
        val match = text.consumeFirst(WEEKLY) ?: return null
        val days = DAY_TOKEN.findAll(match.value)
            .mapNotNull { DAY_NAMES[it.groupValues[1]] }
            .toSet()
        return days.ifEmpty { null }
    }

    private fun extractRelative(text: ConsumableText, now: ZonedDateTime): ZonedDateTime? {
        for ((regex, apply) in relativeRules) {
            val match = text.consumeFirst(regex) ?: continue
            return apply(match, now).withSecond(0).withNano(0)
        }
        return null
    }

    private fun extractDateRef(text: ConsumableText): DateRef? {
        text.consumeFirst(AFTER_TOMORROW)?.let { return DateRef.Offset(2) }
        text.consumeFirst(TOMORROW)?.let { return DateRef.Offset(1) }
        text.consumeFirst(TODAY)?.let { return DateRef.Offset(0) }
        text.consumeFirstIf(DAY_SLASH_MONTH) {
            it.groupValues[1].toInt() in 1..31 && it.groupValues[2].toInt() in 1..12
        }?.let { return DateRef.DayMonth(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        text.consumeFirstIf(DAY_NUMBER) { it.groupValues[1].toInt() in 1..31 }
            ?.let { return DateRef.DayNum(it.groupValues[1].toInt()) }
        return null
    }

    private fun extractOnceWeekday(text: ConsumableText): DayOfWeek? {
        val match = text.consumeFirst(ONCE_WEEKDAY) ?: return null
        val token = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        return DAY_NAMES[token]
    }

    private fun extractTime(text: ConsumableText): RawTime? {
        text.consumeFirst(MIDNIGHT)?.let { return RawTime(0, 0, Marker.EXPLICIT_24) }

        val match = text.consumeFirstIf(TIME) { m ->
            val hasClockWord = m.groupValues[1].isNotEmpty()
            val hasMinutes = m.groupValues[3].isNotEmpty()
            val hasFraction = m.groupValues[4].isNotEmpty()
            val hasMarker = m.groupValues[5].isNotEmpty()
            val hour = m.groupValues[2].toInt()
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            // A bare number is not a time: require some clock context.
            (hasClockWord || hasMinutes || hasFraction || hasMarker) &&
                hour in 0..23 && minute in 0..59 &&
                !(hour > 12 && hasMarker) // "15 مساء" is not a valid reading
        }
        if (match != null) {
            var hour = match.groupValues[2].toInt()
            var minute = match.groupValues[3].toIntOrNull() ?: 0
            val fraction = match.groupValues[4].trim()
            if (match.groupValues[3].isEmpty() && fraction.isNotEmpty()) {
                var total = hour * 60 + fractionMinutes(fraction)
                if (total < 0) total += 24 * 60
                hour = (total / 60) % 24
                minute = total % 60
            }
            val marker = markerOf(match.groupValues[5].trim())
            return RawTime(hour, minute, marker)
        }

        text.consumeFirst(NOON_ALONE)?.let { return RawTime(12, 0, Marker.EXPLICIT_24) }
        return null
    }

    private fun fractionMinutes(fraction: String): Int = when {
        fraction.startsWith("الا") -> if (fraction.endsWith("ثلث")) -20 else -15
        fraction.endsWith("نص") || fraction.endsWith("النص") || fraction.endsWith("نصف") -> 30
        fraction.endsWith("ثلث") || fraction.endsWith("الثلث") -> 20
        else -> 15 // ربع
    }

    private fun markerOf(token: String): Marker = when (token) {
        "" -> Marker.NONE
        "ص", "صباحا", "الصبح", "صباح", "فجرا", "الفجر", "بالفجر" -> Marker.AM
        "ظهرا", "الظهر", "بعد الظهر" -> Marker.NOON
        "عصرا", "العصر", "المغرب" -> Marker.AFTERNOON
        "م", "مساء", "مساءا", "المساء" -> Marker.EVENING
        "ليلا", "الليل", "بالليل", "في الليل" -> Marker.NIGHT
        else -> Marker.NONE
    }

    // ------------------------------------------------------------ resolution

    /** Applies the AM/PM marker (or the daytime heuristic) to a raw reading. */
    private fun resolveTime(raw: RawTime): LocalTime {
        val h = raw.hour
        if (raw.marker == Marker.EXPLICIT_24 || h == 0 || h >= 13) {
            return LocalTime.of(h, raw.minute)
        }
        val hour24 = when (raw.marker) {
            Marker.AM -> if (h == 12) 0 else h
            Marker.NOON -> if (h in 1..5) h + 12 else h
            Marker.AFTERNOON -> if (h in 1..11) h + 12 else 12
            Marker.EVENING -> if (h == 12) 0 else h + 12
            Marker.NIGHT -> when (h) {
                12 -> 0
                in 1..4 -> h // «2 بالليل» = after midnight
                else -> h + 12
            }
            Marker.NONE, Marker.EXPLICIT_24 ->
                if (h in 1..6) h + 12 else h // daytime heuristic
        }
        return LocalTime.of(hour24, raw.minute)
    }

    /** One-time with no date: today if still ahead, else flip AM/PM, else tomorrow. */
    private fun onceForToday(raw: RawTime, now: ZonedDateTime): Schedule.Once {
        val primary = resolveTime(raw)
        val today = now.toLocalDate()
        if (today.atTime(primary).atZone(now.zone).isAfter(now)) {
            return Schedule.Once(today, primary)
        }
        if (raw.marker == Marker.NONE && raw.hour in 1..12) {
            val flipped = LocalTime.of((primary.hour + 12) % 24, primary.minute)
            if (today.atTime(flipped).atZone(now.zone).isAfter(now)) {
                return Schedule.Once(today, flipped)
            }
        }
        return Schedule.Once(today.plusDays(1), primary)
    }

    private fun onceForDate(ref: DateRef, raw: RawTime, now: ZonedDateTime): Schedule {
        val time = resolveTime(raw)
        val today = now.toLocalDate()
        return when (ref) {
            is DateRef.Offset -> {
                if (ref.days == 0L) {
                    // «اليوم» with a time that already passed rolls to tomorrow.
                    onceForToday(raw, now)
                } else {
                    Schedule.Once(today.plusDays(ref.days), time)
                }
            }

            is DateRef.DayNum -> {
                var month = YearMonth.from(now)
                var date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                if (date.atTime(time).atZone(now.zone) <= now) {
                    month = month.plusMonths(1)
                    date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                }
                Schedule.Once(date, time)
            }

            is DateRef.DayMonth -> {
                var month = YearMonth.of(now.year, ref.month)
                var date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                if (date.atTime(time).atZone(now.zone) <= now) {
                    month = month.plusYears(1)
                    date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                }
                Schedule.Once(date, time)
            }
        }
    }

    /** «25/1 كل سنه» → Gregorian yearly; «يوم 25 كل سنه» assumes the current month. */
    private fun yearlySchedule(ref: DateRef, time: LocalTime, now: ZonedDateTime): Schedule? =
        when (ref) {
            is DateRef.DayMonth -> Schedule.Yearly(ref.month, ref.day, time)
            is DateRef.DayNum -> Schedule.Yearly(now.monthValue, ref.day, time)
            is DateRef.Offset -> null
        }

    private fun resolveDateOnly(ref: DateRef, now: ZonedDateTime): LocalDate {
        val today = now.toLocalDate()
        return when (ref) {
            is DateRef.Offset -> today.plusDays(ref.days)
            is DateRef.DayNum -> {
                var month = YearMonth.from(now)
                var date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                if (date < today) {
                    month = month.plusMonths(1)
                    date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                }
                date
            }
            is DateRef.DayMonth -> {
                var month = YearMonth.of(now.year, ref.month)
                var date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                if (date < today) {
                    month = month.plusYears(1)
                    date = month.atDay(minOf(ref.day, month.lengthOfMonth()))
                }
                date
            }
        }
    }

    private fun onceForWeekday(day: DayOfWeek, raw: RawTime, now: ZonedDateTime): Schedule.Once {
        val time = resolveTime(raw)
        val todayOk = now.toLocalDate().dayOfWeek == day &&
            now.toLocalDate().atTime(time).atZone(now.zone).isAfter(now)
        val date = nextDateFor(day, now.toLocalDate(), inclusiveToday = todayOk)
        return Schedule.Once(date, time)
    }

    private fun nextDateFor(day: DayOfWeek, from: LocalDate, inclusiveToday: Boolean): LocalDate {
        val start = if (inclusiveToday) 0L else 1L
        for (offset in start..7L) {
            val date = from.plusDays(offset)
            if (date.dayOfWeek == day) return date
        }
        return from.plusDays(7) // unreachable
    }

    // ----------------------------------------------------------------- title

    private fun cleanTitle(raw: String, hadVerb: Boolean): String {
        var t = raw.trim(' ', '.', '،', ',', '؛', '-', '!', '؟', '?', ':')
        // Leading connectors: «أن أتصل» → «أتصل», «على الاجتماع» → «الاجتماع».
        while (true) {
            val first = t.substringBefore(' ')
            val connector = first in CONNECTORS || (hadVerb && first in VERB_CONNECTORS)
            if (!connector || !t.contains(' ')) break
            t = t.substringAfter(' ').trim()
        }
        // Prepositional ب: «بالاجتماع» → «الاجتماع», and after a reminder verb
        // «بموعد الطبيب» → «موعد الطبيب».
        if (t.length > 3 && (t.startsWith("بال") || (hadVerb && t.startsWith("ب")))) {
            t = t.drop(1)
        }
        return t.trim()
    }

    // ------------------------------------------------------------- internals

    private data class RawTime(val hour: Int, val minute: Int, val marker: Marker)

    private enum class Marker { NONE, AM, NOON, AFTERNOON, EVENING, NIGHT, EXPLICIT_24 }

    private sealed interface DateRef {
        data class Offset(val days: Long) : DateRef
        data class DayNum(val day: Int) : DateRef
        data class DayMonth(val day: Int, val month: Int) : DateRef
    }

    private companion object {
        /** Word-bounded pattern; Java's \b is not reliable for Arabic. */
        fun w(pattern: String) = Regex("(?<![\\p{L}\\d])(?:$pattern)(?![\\p{L}\\d])")

        val VERB = w("ذكرني|ذكريني|ذكروني|فكرني|فكريني|نبهني|نبهيني|نبهوني")

        const val DAY_ALT = "سبت|احد|اثنين|اتنين|ثنين|ثلاثاء|ثلاثا|اربعاء|اربعا|خميس|جمعه"

        val DAY_NAMES: Map<String, DayOfWeek> = mapOf(
            "سبت" to DayOfWeek.SATURDAY,
            "احد" to DayOfWeek.SUNDAY,
            "اثنين" to DayOfWeek.MONDAY,
            "اتنين" to DayOfWeek.MONDAY,
            "ثنين" to DayOfWeek.MONDAY,
            "ثلاثاء" to DayOfWeek.TUESDAY,
            "ثلاثا" to DayOfWeek.TUESDAY,
            "اربعاء" to DayOfWeek.WEDNESDAY,
            "اربعا" to DayOfWeek.WEDNESDAY,
            "خميس" to DayOfWeek.THURSDAY,
            "جمعه" to DayOfWeek.FRIDAY,
        )

        val WEEKLY = w("كل (?:يوم |ايام )?(?:ال)?(?:$DAY_ALT)(?: ?و ?(?:ال)?(?:$DAY_ALT))*")
        // و? — a day may arrive glued to واو العطف («وثلاثاء», «والثلاثا»).
        val DAY_TOKEN = Regex("(?<![\\p{L}])و?(?:ال)?($DAY_ALT)(?![\\p{L}])")
        val ONCE_WEEKDAY = w("يوم (?:ال)?($DAY_ALT)(?: (?:الجايه|الجاي|القادمه|القادم|الجيه))?|ال($DAY_ALT)(?: (?:الجايه|الجاي|القادمه|القادم|الجيه))?")

        val DAILY = w("كل يوم|يوميا|كل الايام")
        val YEARLY = w("كل سنه|كل عام|سنويا|من كل سنه|من كل عام")

        val MONTHLY_DAY_OF = w("(?:يوم )?(\\d{1,2}) من كل شهر")
        val MONTHLY_EVERY = w("كل شهر (?:يوم )?(\\d{1,2})")
        val MONTHLY_LAST_DAY = w("اخر يوم (?:من |في )?(?:كل )?شهر|نهايه كل شهر")
        val MONTHLY_WORD = w("شهريا")

        val AFTER_TOMORROW = w("بعد بكره|بعد بكرا|بعد غد|بعد غدا")
        val TOMORROW = w("بكره|بكرا|غدا|غد|باكر|باجر")
        val TODAY = w("اليوم|النهارده")

        val DAY_SLASH_MONTH = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])")
        val DAY_NUMBER = w("يوم (\\d{1,2})")

        val MIDNIGHT = w("منتصف الليل|نص الليل|نصف الليل")
        val NOON_ALONE = w("الظهر|وقت الظهر")

        const val MARKER_ALT =
            " صباحا| الصبح| صباح| فجرا| الفجر| بالفجر| ظهرا| الظهر| بعد الظهر| عصرا| العصر| المغرب" +
                "| مساءا| مساء| المساء| ليلا| بالليل| في الليل| الليل| ص(?![\\p{L}])| م(?![\\p{L}])"

        val TIME = Regex(
            "(?:(?:على|عند) )?(الساعه ?)?(?<![\\d/:.])(\\d{1,2})(?:[:.](\\d{1,2}))?(?![\\d/])" +
                "( و ?(?:نصف|نص|النص|ربع|الربع|ثلث|الثلث)| الا (?:ربع|ثلث))?" +
                "($MARKER_ALT)?",
        )

        val relativeRules: List<Pair<Regex, (MatchResult, ZonedDateTime) -> ZonedDateTime>> = listOf(
            w("بعد ساعه و ?(?:نص|نصف)") to { _, now -> now.plusMinutes(90) },
            w("بعد ساعه و ?ربع") to { _, now -> now.plusMinutes(75) },
            w("بعد ساعتين و ?(?:نص|نصف)") to { _, now -> now.plusMinutes(150) },
            w("بعد (?:نص|نصف) ساعه") to { _, now -> now.plusMinutes(30) },
            w("بعد ربع ساعه") to { _, now -> now.plusMinutes(15) },
            w("بعد ثلث ساعه") to { _, now -> now.plusMinutes(20) },
            w("بعد (\\d+) (?:دقيقه|دقايق|دقائق)") to { m, now -> now.plusMinutes(m.groupValues[1].toLong()) },
            w("بعد (\\d+) (?:ساعه|ساعات)") to { m, now -> now.plusHours(m.groupValues[1].toLong()) },
            w("بعد (\\d+) (?:يوم|ايام)") to { m, now -> now.plusDays(m.groupValues[1].toLong()) },
            w("بعد (\\d+) (?:اسبوع|اسابيع)") to { m, now -> now.plusWeeks(m.groupValues[1].toLong()) },
            w("بعد (\\d+) (?:شهر|شهور|اشهر)") to { m, now -> now.plusMonths(m.groupValues[1].toLong()) },
            w("بعد دقيقتين") to { _, now -> now.plusMinutes(2) },
            w("بعد ساعتين") to { _, now -> now.plusHours(2) },
            w("بعد يومين") to { _, now -> now.plusDays(2) },
            w("بعد اسبوعين") to { _, now -> now.plusWeeks(2) },
            w("بعد شهرين") to { _, now -> now.plusMonths(2) },
            w("بعد دقيقه") to { _, now -> now.plusMinutes(1) },
            w("بعد ساعه") to { _, now -> now.plusHours(1) },
            w("بعد يوم") to { _, now -> now.plusDays(1) },
            w("بعد اسبوع") to { _, now -> now.plusWeeks(1) },
            w("بعد شهر") to { _, now -> now.plusMonths(1) },
            w("بعد شويه|بعد شوي") to { _, now -> now.plusMinutes(15) },
        )

        val CONNECTORS = setOf("ان", "اني", "أن", "أني", "إني", "إن")
        val VERB_CONNECTORS = setOf("على", "عن", "في")
    }
}
