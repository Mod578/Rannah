package com.bal.reminders.format

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import org.junit.Assert.assertEquals
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
    fun `hijri full renders a concise day month year date`() {
        // 2024-06-16 is 10 Dhu al-Hijjah 1445.
        val text = BalFormats.hijriFull(LocalDate.of(2024, 6, 16))!!
        assertEquals("١٠ ذو الحجة ١٤٤٥ هـ", text)
    }

    @Test
    fun `gregorian format uses arabic digits and the gregorian suffix`() {
        val text = BalFormats.gregorianDate(LocalDate.of(2026, 7, 15))
        assertTrue("expected م suffix in: $text", text.endsWith("م"))
        assertTrue("expected ١٥ in: $text", text.startsWith("١٥ "))
        assertTrue("expected ٢٠٢٦ in: $text", text.contains("٢٠٢٦"))
    }

    @Test
    fun `date lines lead with the gregorian date and add a light hijri context`() {
        // 2026-07-15 is a Wednesday.
        val (primary, secondary) = BalFormats.dateLines(LocalDate.of(2026, 7, 15))
        assertTrue("weekday first: $primary", primary.startsWith("الأربعاء"))
        assertTrue("gregorian in primary: $primary", primary.contains("م"))
        assertTrue("hijri context secondary: $secondary", secondary!!.contains("هـ"))
    }

    @Test
    fun `weekday comes from the civil date`() {
        assertEquals("الأربعاء", BalFormats.weekdayName(LocalDate.of(2026, 7, 15)))
    }
}
