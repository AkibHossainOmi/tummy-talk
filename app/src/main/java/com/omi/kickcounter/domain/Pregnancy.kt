package com.omi.kickcounter.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Gestational age, measured the way clinicians do it: from the first day of the
 * last menstrual period (LMP), not from conception.
 */
data class GestationalAge(
    val weeks: Int,
    val days: Int,
    val totalDays: Int,
) {
    val trimester: Int
        get() = when {
            weeks < 13 -> 1
            weeks < 28 -> 2
            else -> 3
        }

    /** e.g. "24w 3d" */
    fun format(): String = "${weeks}w ${days}d"
}

object Pregnancy {

    /** A full-term pregnancy is 40 weeks from LMP. */
    const val TERM_DAYS = 280

    /**
     * Seeded from the known reference point: 2026-08-09 was exactly 24w 0d,
     * so the LMP was 168 days earlier. Editable in Settings.
     */
    val DEFAULT_LMP: LocalDate = LocalDate.of(2026, 2, 22)

    fun ageAt(lmp: LocalDate, date: LocalDate): GestationalAge {
        val total = ChronoUnit.DAYS.between(lmp, date).toInt().coerceAtLeast(0)
        return GestationalAge(weeks = total / 7, days = total % 7, totalDays = total)
    }

    fun dueDate(lmp: LocalDate): LocalDate = lmp.plusDays(TERM_DAYS.toLong())

    fun daysUntilDue(lmp: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, dueDate(lmp)).toInt()
}
