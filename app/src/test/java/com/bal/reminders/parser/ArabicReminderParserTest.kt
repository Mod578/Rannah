package com.bal.reminders.parser

import com.bal.reminders.domain.HijriDates
import com.bal.reminders.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicReminderParserTest {

    private val parser = ArabicReminderParser()
    private val zone = ZoneId.of("Asia/Riyadh")

    // Wednesday 2026-07-15, 10:00.
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, zone)

    private fun success(input: String, at: ZonedDateTime = now): ParseResult.Success {
        val result = parser.parse(input, at)
        assertTrue("expected Success for «$input» but was $result", result is ParseResult.Success)
        return result as ParseResult.Success
    }

    // ------------------------------------------------------- core examples

    @Test
    fun `daily clock-in - the flagship sentence`() {
        val r = success("ذكّرني كل يوم الساعة 9 ببصمة الدوام")
        assertEquals("بصمة الدوام", r.title)
        assertEquals(Schedule.Daily(LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `tomorrow at 8 meeting`() {
        val r = success("ذكّرني بكرة الساعة 8 بالاجتماع")
        assertEquals("الاجتماع", r.title)
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 16), LocalTime.of(8, 0)), r.schedule)
    }

    @Test
    fun `weekly on Sunday and Tuesday evening`() {
        val r = success("ذكّرني كل أحد وثلاثاء الساعة 6 مساءً بالنادي")
        assertEquals("النادي", r.title)
        assertEquals(
            Schedule.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY), LocalTime.of(18, 0)),
            r.schedule,
        )
    }

    @Test
    fun `relative half hour`() {
        val r = success("ذكّرني بعد نصف ساعة بالدواء")
        assertEquals("الدواء", r.title)
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(10, 30)), r.schedule)
    }

    @Test
    fun `monthly on the 25th with time`() {
        val r = success("ذكّرني يوم 25 من كل شهر الساعة 9 بدفع الفاتورة")
        assertEquals("دفع الفاتورة", r.title)
        assertEquals(Schedule.Monthly(25, LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `monthly without time is an incomplete draft with the day kept`() {
        val result = parser.parse("ذكّرني يوم 25 من كل شهر بدفع الفاتورة", now)
        assertTrue(result is ParseResult.Incomplete)
        val incomplete = result as ParseResult.Incomplete
        assertEquals(ParseResult.MissingPart.TIME, incomplete.missing)
        assertEquals("دفع الفاتورة", incomplete.draft.title)
        assertEquals(25, (incomplete.draft.schedule as Schedule.Monthly).dayOfMonth)
    }

    // -------------------------------------------------------------- numerals

    @Test
    fun `arabic-indic numerals`() {
        val r = success("ذكرني كل يوم الساعة ٩ ببصمة الدوام")
        assertEquals(Schedule.Daily(LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `mixed numerals in one sentence`() {
        val r = success("ذكرني يوم ٢٥ من كل شهر الساعة 7 مساء بالفاتورة")
        assertEquals("الفاتورة", r.title)
        assertEquals(Schedule.Monthly(25, LocalTime.of(19, 0)), r.schedule)
    }

    @Test
    fun `time with minutes`() {
        val r = success("ذكرني بكرة الساعة 8:30 بالموعد")
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 16), LocalTime.of(8, 30)), r.schedule)
    }

    // ------------------------------------------------------------- dayparts

    @Test
    fun `explicit morning marker`() {
        val r = success("ذكرني بكرة الساعة 5 صباحا بالسحور")
        assertEquals(LocalTime.of(5, 0), r.schedule.time)
    }

    @Test
    fun `single letter sad marker`() {
        val r = success("ذكرني بكرة 9 ص بالدوام")
        assertEquals(LocalTime.of(9, 0), r.schedule.time)
    }

    @Test
    fun `noon of 1 means 13`() {
        val r = success("ذكرني بكرة الساعة 1 الظهر بالغدا")
        assertEquals(LocalTime.of(13, 0), r.schedule.time)
    }

    @Test
    fun `night of 11 means 23`() {
        val r = success("ذكرني بكرة الساعة 11 بالليل بالحبوب")
        assertEquals(LocalTime.of(23, 0), r.schedule.time)
    }

    @Test
    fun `night of 2 means after midnight`() {
        val r = success("ذكرني بكرة الساعة 2 بالليل بالسحور")
        assertEquals(LocalTime.of(2, 0), r.schedule.time)
    }

    @Test
    fun `midnight expression`() {
        val r = success("ذكرني بكرة منتصف الليل بالتحديث")
        assertEquals(LocalTime.MIDNIGHT, r.schedule.time)
    }

    @Test
    fun `quarter-to expression`() {
        val r = success("ذكرني بكرة الساعة 8 الا ربع صباحا بالتجهيز")
        assertEquals(LocalTime.of(7, 45), r.schedule.time)
    }

    @Test
    fun `half-past expression`() {
        val r = success("ذكرني بكرة الساعة 8 ونص مساء بالمسلسل")
        assertEquals(LocalTime.of(20, 30), r.schedule.time)
    }

    // -------------------------------------------- no-marker AM-PM heuristic

    @Test
    fun `daily 9 with no marker means morning`() {
        val r = success("ذكرني كل يوم الساعة 9 بالفطور")
        assertEquals(LocalTime.of(9, 0), r.schedule.time)
    }

    @Test
    fun `daily 6 with no marker means evening`() {
        val r = success("ذكرني كل يوم الساعة 6 بالتمرين")
        assertEquals(LocalTime.of(18, 0), r.schedule.time)
    }

    @Test
    fun `today a passed morning hour flips to evening`() {
        // Now is 10:00; «الساعة 9» today already passed → 21:00 today.
        val r = success("ذكرني اليوم الساعة 9 بالعشا")
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 15), LocalTime.of(21, 0)), r.schedule)
    }

    @Test
    fun `time only rolls to tomorrow when both readings passed`() {
        val evening = ZonedDateTime.of(2026, 7, 15, 23, 0, 0, 0, zone)
        val r = success("ذكرني الساعة 9 بالاجتماع", evening)
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 16), LocalTime.of(9, 0)), r.schedule)
    }

    // ------------------------------------------------------------- weekdays

    @Test
    fun `different weekday spellings`() {
        val r = success("ذكرني كل الاحد والثلاثا الساعه 6 مساء بالنادي")
        assertEquals(
            setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
            (r.schedule as Schedule.Weekly).days,
        )
    }

    @Test
    fun `every friday`() {
        val r = success("ذكرني كل جمعه الساعه 11 صباحا بالتجهيز للصلاة")
        assertEquals(setOf(DayOfWeek.FRIDAY), (r.schedule as Schedule.Weekly).days)
    }

    @Test
    fun `single weekday without kul is one-time next such day`() {
        // Wednesday now → next Sunday is 2026-07-19.
        val r = success("ذكرني يوم الأحد الساعة 5 مساء بالعزيمة")
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 19), LocalTime.of(17, 0)), r.schedule)
    }

    // ------------------------------------------------------------- relative

    @Test
    fun `relative variants`() {
        assertEquals(
            LocalTime.of(10, 15),
            success("ذكرني بعد ربع ساعة اطفي الفرن").schedule.time,
        )
        assertEquals(
            LocalTime.of(12, 0),
            success("ذكرني بعد ساعتين بالمكالمة").schedule.time,
        )
        assertEquals(
            LocalTime.of(10, 45),
            success("ذكرني بعد 45 دقيقة بالغسيل").schedule.time,
        )
        val afterTwoDays = success("ذكرني بعد يومين بمراجعة العرض")
        assertEquals(LocalDate.of(2026, 7, 17), (afterTwoDays.schedule as Schedule.Once).date)
    }

    @Test
    fun `after tomorrow`() {
        val r = success("ذكرني بعد بكرة الساعة 3 العصر بالمشوار")
        assertEquals(Schedule.Once(LocalDate.of(2026, 7, 17), LocalTime.of(15, 0)), r.schedule)
    }

    // -------------------------------------------------------------- monthly

    @Test
    fun `monthly other phrasing`() {
        val r = success("ذكرني كل شهر يوم 1 الساعة 8 صباحا بالايجار")
        assertEquals(Schedule.Monthly(1, LocalTime.of(8, 0)), r.schedule)
    }

    @Test
    fun `monthly last day`() {
        val r = success("ذكرني اخر يوم من كل شهر الساعة 9 بالتقرير")
        assertEquals(Schedule.Monthly(31, LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `Hijri named date parses as a Hijri one time date, calendar preserved`() {
        val r = success("ذكرني ١٥ شعبان الساعة ٩ بالموعد")
        val schedule = r.schedule as Schedule.OnceHijri
        assertEquals(8, schedule.month)
        assertEquals(15, schedule.day)
        assertEquals(LocalTime.of(9, 0), schedule.time)
        // The chosen year is the next calculated occurrence.
        val civil = HijriDates.toGregorian(schedule.year, schedule.month, schedule.day)!!
        assertTrue(civil >= now.toLocalDate())
    }

    @Test
    fun `first Ramadan parses without silently inventing a time`() {
        val result = parser.parse("ذكرني أول رمضان بالزكاة", now)
        assertTrue(result is ParseResult.Incomplete)
        val schedule = (result as ParseResult.Incomplete).draft.schedule as Schedule.OnceHijri
        assertEquals(9, schedule.month)
        assertEquals(1, schedule.day)
    }

    @Test
    fun `explicit Hijri monthly recurrence remains Hijri`() {
        val r = success("ذكرني يوم ١٥ من كل شهر هجري الساعة ٩ بالدواء")
        assertEquals(Schedule.HijriMonthly(15, LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `Hijri yearly occasion stays Hijri yearly`() {
        val r = success("ذكرني ١٥ شعبان من كل سنة الساعة ٩ بصلة الرحم")
        assertEquals(Schedule.HijriYearly(8, 15, LocalTime.of(9, 0)), r.schedule)
    }

    @Test
    fun `Gregorian yearly by day slash month`() {
        val r = success("ذكرني 23/1 كل سنة الساعة 5 مساء بالتجديد")
        assertEquals(Schedule.Yearly(1, 23, LocalTime.of(17, 0)), r.schedule)
    }

    // ------------------------------------------------------------ incomplete

    @Test
    fun `date without time asks for the time`() {
        val result = parser.parse("ذكرني بكرة بالاجتماع", now)
        assertTrue(result is ParseResult.Incomplete)
        val incomplete = result as ParseResult.Incomplete
        assertEquals(ParseResult.MissingPart.TIME, incomplete.missing)
        assertEquals("الاجتماع", incomplete.draft.title)
        assertEquals(
            LocalDate.of(2026, 7, 16),
            (incomplete.draft.schedule as Schedule.Once).date,
        )
    }

    @Test
    fun `schedule without title asks for the title`() {
        val result = parser.parse("ذكرني كل يوم الساعة 9", now)
        assertTrue(result is ParseResult.Incomplete)
        val incomplete = result as ParseResult.Incomplete
        assertEquals(ParseResult.MissingPart.TITLE, incomplete.missing)
        assertEquals(Schedule.Daily(LocalTime.of(9, 0)), incomplete.draft.schedule)
    }

    @Test
    fun `plain text becomes a title draft`() {
        val result = parser.parse("موعد المستشفى", now)
        assertTrue(result is ParseResult.Incomplete)
        val incomplete = result as ParseResult.Incomplete
        assertEquals(ParseResult.MissingPart.TIME, incomplete.missing)
        assertEquals("موعد المستشفى", incomplete.draft.title)
    }

    @Test
    fun `blank input is NoMatch`() {
        assertEquals(ParseResult.NoMatch, parser.parse("   ", now))
    }

    // ---------------------------------------------------------------- titles

    @Test
    fun `title keeps its original spelling`() {
        val r = success("ذكرني كل يوم الساعة 9 ببصمة الدوام")
        // ة must survive normalization (matching uses ه internally).
        assertTrue(r.title.contains("بصمة"))
    }

    @Test
    fun `an connector is stripped`() {
        val r = success("ذكرني بكرة الساعة 7 مساء أن أتصل بأمي")
        assertEquals("أتصل بأمي", r.title)
    }

    @Test
    fun `bare title without preposition survives`() {
        val r = success("بصمة الدوام كل يوم الساعة 9")
        assertEquals("بصمة الدوام", r.title)
    }

    @Test
    fun `diacritics and tatweel are ignored in matching`() {
        val r = success("ذكّـرني كُل يوم السـاعة ٩ ببصمة الدوام")
        assertEquals(Schedule.Daily(LocalTime.of(9, 0)), r.schedule)
    }
}
