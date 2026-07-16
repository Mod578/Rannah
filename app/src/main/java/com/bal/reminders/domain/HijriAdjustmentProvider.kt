package com.bal.reminders.domain

/**
 * The user's ±2-day Hijri sighting adjustment, needed by the scheduler so
 * Hijri reminders fire on the same announced date the app displays.
 */
fun interface HijriAdjustmentProvider {
    suspend fun adjustmentDays(): Int
}
