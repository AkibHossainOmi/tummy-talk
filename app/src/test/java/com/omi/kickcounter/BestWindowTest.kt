package com.omi.kickcounter

import com.omi.kickcounter.domain.KickStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class BestWindowTest {

    private val twoHours = TimeUnit.HOURS.toMillis(2)

    private fun minute(m: Long) = TimeUnit.MINUTES.toMillis(m)

    /** Repeats [times] taps at the given minute, as a burst logged one second apart. */
    private fun burst(atMinute: Long, times: Int): List<Long> =
        (0 until times).map { minute(atMinute) + it * 1000L }

    @Test
    fun `a quiet start does not hide a busy two hours`() {
        // The reported case: a session anchored at minute 1 expires at 7 of 10,
        // but a later two-hour stretch clearly contains more than ten.
        val taps = (
            burst(1, 1) + burst(60, 2) + burst(100, 1) +
                burst(120, 3) + burst(125, 5) + burst(130, 2)
            ).sorted()

        assertEquals(14, taps.size)

        // Anchored at the first movement, only 7 fall inside the two-hour window.
        val anchored = KickStats.movementsIn(taps, taps.first(), taps.first() + twoHours)
        assertEquals(7, anchored.size)

        // The sliding window finds the busy stretch the anchor missed.
        val best = KickStats.bestWindow(taps, twoHours)!!
        assertEquals(13, best.count)
    }

    @Test
    fun `an empty day has no window`() {
        assertNull(KickStats.bestWindow(emptyList(), twoHours))
    }

    @Test
    fun `a single movement is a window of one`() {
        val best = KickStats.bestWindow(listOf(minute(5)), twoHours)!!
        assertEquals(1, best.count)
        assertEquals(minute(5), best.from)
        assertEquals(minute(5), best.to)
    }

    @Test
    fun `movements exactly two hours apart both count`() {
        val taps = listOf(0L, twoHours)
        assertEquals(2, KickStats.bestWindow(taps, twoHours)!!.count)
    }

    @Test
    fun `movements more than two hours apart do not share a window`() {
        val taps = listOf(0L, twoHours + 1)
        assertEquals(1, KickStats.bestWindow(taps, twoHours)!!.count)
    }

    @Test
    fun `the reported window covers the busiest stretch`() {
        val taps = (burst(0, 2) + burst(200, 6) + burst(210, 4)).sorted()
        val best = KickStats.bestWindow(taps, twoHours)!!
        assertEquals(10, best.count)
        assertEquals(minute(200), best.from)
    }

    @Test
    fun `all movements inside one window are counted together`() {
        val taps = (1..12).map { minute(it * 5L) }
        assertEquals(12, KickStats.bestWindow(taps, twoHours)!!.count)
    }
}
