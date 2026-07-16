package com.bal.reminders.format

import com.bal.reminders.data.DateDisplay
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hijri conversion must match the official Umm al-Qura tables, which is
 * exactly what java.time's HijrahChronology ships ("Hijrah-umalqura").
 * Anchors below are dates fixed by the published Umm al-Qura calendar.
 */
class HijriDatesTest {

    @Test
    fun `chronology is the official Umm al-Qura variant`() {
        assertEquals("islamic-umalqura", HijrahChronology.INSTANCE.calendarType)
    }

    @Test
    fun `Eid al-Adha 1445 fell on June 16 2024`() {
        assertEquals(
            LocalDate.of(2024, 6, 16),
            LocalDate.from(HijrahDate.of(1445, 12, 10)),
        )
    }

    @Test
    fun `Ramadan 1446 began on March 1 2025`() {
        assertEquals(
            LocalDate.of(2025, 3, 1),
            LocalDate.from(HijrahDate.of(1446, 9, 1)),
        )
    }

    @Test
    fun `conversion round-trips across a full year`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(365) {
            assertEquals(date, LocalDate.from(HijrahDate.from(date)))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `formatted hijri date uses arabic digits and the hijri suffix`() {
        val text = BalFormats.hijriDate(LocalDate.of(2024, 6, 16))!!
        assertTrue("expected هـ suffix in: $text", text.endsWith("هـ"))
        assertTrue("expected day ١٠ in: $text", text.startsWith("١٠ "))
        assertTrue("expected year ١٤٤٥ in: $text", text.contains("١٤٤٥"))
    }

    @Test
    fun `adjustment shifts the hijri day, not the civil date`() {
        val date = LocalDate.of(2024, 6, 16)
        assertEquals(BalFormats.hijriDate(date.plusDays(1)), BalFormats.hijriDate(date, 1))
        assertEquals(BalFormats.hijriDate(date.minusDays(2)), BalFormats.hijriDate(date, -2))
    }

    @Test
    fun `gregorian format uses arabic digits and the gregorian suffix`() {
        val text = BalFormats.gregorianDate(LocalDate.of(2026, 7, 15))
        assertTrue("expected م suffix in: $text", text.endsWith("م"))
        assertTrue("expected ١٥ in: $text", text.startsWith("١٥ "))
        assertTrue("expected ٢٠٢٦ in: $text", text.contains("٢٠٢٦"))
    }

    @Test
    fun `date lines follow the display preference`() {
        val date = LocalDate.of(2026, 7, 15)

        val both = BalFormats.dateLines(date, DateDisplay.BOTH)
        assertTrue(both.first.contains("هـ"))
        assertEquals(BalFormats.gregorianDate(date), both.second)

        val hijriOnly = BalFormats.dateLines(date, DateDisplay.HIJRI)
        assertTrue(hijriOnly.first.contains("هـ"))
        assertNull(hijriOnly.second)

        val gregorianOnly = BalFormats.dateLines(date, DateDisplay.GREGORIAN)
        assertTrue(gregorianOnly.first.contains("م"))
        assertTrue(!gregorianOnly.first.contains("هـ"))
        assertNull(gregorianOnly.second)
    }

    @Test
    fun `weekday comes from the civil date and is unaffected by adjustment`() {
        // 2026-07-15 is a Wednesday.
        assertEquals("الأربعاء", BalFormats.weekdayName(LocalDate.of(2026, 7, 15)))
        val lines = BalFormats.dateLines(LocalDate.of(2026, 7, 15), DateDisplay.BOTH, 2)
        assertTrue(lines.first.startsWith("الأربعاء"))
    }
}
