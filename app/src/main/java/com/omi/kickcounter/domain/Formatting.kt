package com.omi.kickcounter.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object Formatting {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val mediumDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val longDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

    fun clock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)

    fun mediumDate(date: java.time.LocalDate): String = date.format(mediumDateFormatter)

    fun dayAndDate(date: java.time.LocalDate): String = date.format(longDateFormatter)

    /** "9 AM", "12 PM", "11 PM" */
    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    /** "just now", "4m ago", "2h 10m ago", "3d ago" */
    fun relative(from: Long?, now: Long): String {
        // Kept short: this lands in a narrow tile that clipped the longer wording.
        if (from == null) return "none yet"
        val delta = (now - from).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "${h}h ago" else "${h}h ${m}m ago"
            }
            else -> "${minutes / (60 * 24)}d ago"
        }
    }

    /** "18 min" / "1h 12m" */
    fun duration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
    }
}
