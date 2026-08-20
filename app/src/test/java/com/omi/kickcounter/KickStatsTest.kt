package com.omi.kickcounter

import com.omi.kickcounter.domain.KickStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class KickStatsTest {

    private val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, zone: ZoneId = dhaka): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `grouping off keeps every tap`() {
        val taps = listOf(0L, 1_000L, 2_000L)
        assertEquals(taps, KickStats.distinctMovements(taps, groupingMinutes = 0))
    }

    @Test
    fun `grouping collapses taps inside the window and keeps the first of each burst`() {
        val minute = TimeUnit.MINUTES.toMillis(1)
        val taps = listOf(0L, minute, 2 * minute, 10 * minute, 11 * minute)
        assertEquals(listOf(0L, 10 * minute), KickStats.distinctMovements(taps, 5))
    }

    @Test
    fun `grouping measures from the last counted movement not the last raw tap`() {
        val minute = TimeUnit.MINUTES.toMillis(1)
        // Taps every 3 minutes with a 5 minute window: the chain must not slide forward
        // on skipped taps, so 0, 6 and 12 are counted.
        val taps = listOf(0L, 3 * minute, 6 * minute, 9 * minute, 12 * minute)
        assertEquals(listOf(0L, 6 * minute, 12 * minute), KickStats.distinctMovements(taps, 5))
    }

    @Test
    fun `hourly counts bucket by local hour and ignore other days`() {
        val day = LocalDate.of(2026, 8, 9)
        val taps = listOf(
            at(2026, 8, 9, 0, 5),
            at(2026, 8, 9, 9, 10),
            at(2026, 8, 9, 9, 55),
            at(2026, 8, 9, 23, 59),
            at(2026, 8, 10, 0, 1),
            at(2026, 8, 8, 23, 59),
        )
        val hourly = KickStats.hourlyCounts(taps, day, dhaka)
        assertEquals(1, hourly[0])
        assertEquals(2, hourly[9])
        assertEquals(1, hourly[23])
        assertEquals(4, hourly.sum())
    }

    @Test
    fun `start of hour truncates to the local hour`() {
        val start = KickStats.startOfHour(at(2026, 8, 9, 14, 37), dhaka)
        assertEquals(at(2026, 8, 9, 14, 0), start)
    }

    @Test
    fun `day bucketing survives a DST transition`() {
        // London springs forward on 2026-03-29; that local day is only 23 hours long.
        val london = ZoneId.of("Europe/London")
        val taps = listOf(
            at(2026, 3, 29, 1, 30, london),
            at(2026, 3, 29, 4, 30, london),
            at(2026, 3, 28, 23, 30, london),
        )
        val dates = taps.map { KickStats.localDate(it, london) }
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 28),
            ),
            dates,
        )
    }

    @Test
    fun `session movements are bounded by the session window`() {
        val taps = listOf(
            at(2026, 8, 9, 9, 30),
            at(2026, 8, 9, 10, 5),
            at(2026, 8, 9, 10, 40),
            at(2026, 8, 9, 12, 30),
        )
        val inSession = KickStats.movementsIn(
            taps,
            from = at(2026, 8, 9, 10, 0),
            to = at(2026, 8, 9, 11, 0),
        )
        assertEquals(2, inSession.size)
    }

    @Test
    fun `an open session must not be bounded by a stale clock reading`() {
        // Regression: the upper bound came from a 30-second heartbeat, so a tap
        // recorded a moment ago fell outside its own session and the dial sat on
        // zero while the daily total climbed.
        val sessionStart = at(2026, 8, 20, 3, 0)
        val justRecorded = sessionStart + 500
        val staleNow = sessionStart - 20_000

        assertEquals(
            0,
            KickStats.movementsIn(listOf(justRecorded), sessionStart, staleNow).size,
        )
        assertEquals(
            1,
            KickStats.movementsIn(listOf(justRecorded), sessionStart, Long.MAX_VALUE).size,
        )
    }

    @Test
    fun `time to goal spans the first movement to the goal-th movement`() {
        val session = listOf(
            at(2026, 8, 9, 10, 0),
            at(2026, 8, 9, 10, 10),
            at(2026, 8, 9, 10, 30),
        )
        assertEquals(null, KickStats.timeToGoalMillis(session, goal = 4))
        assertEquals(TimeUnit.MINUTES.toMillis(10), KickStats.timeToGoalMillis(session, goal = 2))
        assertEquals(TimeUnit.MINUTES.toMillis(30), KickStats.timeToGoalMillis(session, goal = 3))
    }

    @Test
    fun `extra movements after the goal do not change the time to goal`() {
        val session = listOf(
            at(2026, 8, 9, 10, 0),
            at(2026, 8, 9, 10, 5),
            at(2026, 8, 9, 11, 55),
        )
        assertEquals(TimeUnit.MINUTES.toMillis(5), KickStats.timeToGoalMillis(session, goal = 2))
    }
}
