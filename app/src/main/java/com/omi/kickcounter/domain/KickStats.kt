package com.omi.kickcounter.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Pure functions over a sorted-ascending list of tap timestamps (epoch millis).
 * Everything here is deliberately side-effect free so the awkward parts —
 * midnight boundaries, DST, movement grouping — are unit testable.
 */
object KickStats {

    /**
     * Collapses taps that land within [groupingMinutes] of the previous *counted*
     * movement. A window of 0 means every tap counts, which is the default and
     * matches how guidance describes counting: each kick, roll, jab or swish is one.
     */
    fun distinctMovements(timestamps: List<Long>, groupingMinutes: Int): List<Long> {
        if (groupingMinutes <= 0) return timestamps
        val window = TimeUnit.MINUTES.toMillis(groupingMinutes.toLong())
        val out = ArrayList<Long>(timestamps.size)
        var last = Long.MIN_VALUE
        for (t in timestamps) {
            if (last == Long.MIN_VALUE || t - last >= window) {
                out.add(t)
                last = t
            }
        }
        return out
    }

    fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(zone: ZoneId, now: Long): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun localDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Counts per local hour 0..23 for the given day. */
    fun hourlyCounts(timestamps: List<Long>, date: LocalDate, zone: ZoneId): IntArray {
        val counts = IntArray(24)
        for (t in timestamps) {
            val zoned = Instant.ofEpochMilli(t).atZone(zone)
            if (zoned.toLocalDate() == date) counts[zoned.hour]++
        }
        return counts
    }

    /** Start of the local hour containing [millis]. */
    fun startOfHour(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone)
            .withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

    /** Movements inside a half-open session window. */
    fun movementsIn(timestamps: List<Long>, from: Long, to: Long): List<Long> =
        timestamps.filter { it >= from && it <= to }

    /** The busiest stretch of a given length, found by sliding a window over the day. */
    data class BestWindow(val count: Int, val from: Long, val to: Long)

    /**
     * Answers "was there ever a two-hour stretch with ten movements in it", which a
     * session anchored at the first tap cannot answer. A session that starts while
     * the baby happens to be quiet can expire below the goal even though a later
     * two hours was busy; this is the figure that keeps that from looking alarming.
     *
     * [timestamps] must be sorted ascending.
     */
    fun bestWindow(timestamps: List<Long>, windowMillis: Long): BestWindow? {
        if (timestamps.isEmpty()) return null
        var start = 0
        var best = BestWindow(0, timestamps.first(), timestamps.first())
        for (end in timestamps.indices) {
            while (timestamps[end] - timestamps[start] > windowMillis) start++
            val count = end - start + 1
            if (count > best.count) {
                best = BestWindow(count, timestamps[start], timestamps[end])
            }
        }
        return best
    }

    /** How long the first [goal] movements of a session took, or null if it never got there. */
    fun timeToGoalMillis(sessionMovements: List<Long>, goal: Int): Long? {
        if (sessionMovements.size < goal) return null
        return sessionMovements[goal - 1] - sessionMovements[0]
    }
}
