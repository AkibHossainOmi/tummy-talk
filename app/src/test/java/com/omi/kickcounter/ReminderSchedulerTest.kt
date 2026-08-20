package com.omi.kickcounter

import com.omi.kickcounter.reminder.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {

    private val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(dhaka).toInstant().toEpochMilli()

    @Test
    fun `window includes the start hour and excludes the end hour`() {
        assertTrue(ReminderScheduler.isInsideWindow(9, 9, 21))
        assertTrue(ReminderScheduler.isInsideWindow(20, 9, 21))
        assertFalse(ReminderScheduler.isInsideWindow(21, 9, 21))
        assertFalse(ReminderScheduler.isInsideWindow(8, 9, 21))
    }

    @Test
    fun `a window that wraps past midnight is supported`() {
        // Someone who sleeps during the day: 10 PM through 6 AM.
        assertTrue(ReminderScheduler.isInsideWindow(23, 22, 6))
        assertTrue(ReminderScheduler.isInsideWindow(0, 22, 6))
        assertTrue(ReminderScheduler.isInsideWindow(5, 22, 6))
        assertFalse(ReminderScheduler.isInsideWindow(6, 22, 6))
        assertFalse(ReminderScheduler.isInsideWindow(12, 22, 6))
    }

    @Test
    fun `an empty window never fires`() {
        assertFalse(ReminderScheduler.isInsideWindow(9, 9, 9))
    }

    @Test
    fun `mid-window the next trigger is the next top of the hour`() {
        val next = ReminderScheduler.nextTriggerMillis(
            now = at(2026, 8, 10, 14, 37),
            startHour = 9,
            endHour = 21,
            zone = dhaka,
        )
        assertEquals(at(2026, 8, 10, 15, 0), next)
    }

    @Test
    fun `at the last hour of the window the next trigger is tomorrow morning`() {
        val next = ReminderScheduler.nextTriggerMillis(
            now = at(2026, 8, 10, 20, 30),
            startHour = 9,
            endHour = 21,
            zone = dhaka,
        )
        assertEquals(at(2026, 8, 11, 9, 0), next)
    }

    @Test
    fun `overnight nothing is scheduled until the window opens`() {
        val next = ReminderScheduler.nextTriggerMillis(
            now = at(2026, 8, 10, 2, 15),
            startHour = 9,
            endHour = 21,
            zone = dhaka,
        )
        assertEquals(at(2026, 8, 10, 9, 0), next)
    }

    @Test
    fun `exactly on the hour the next trigger is an hour later`() {
        val next = ReminderScheduler.nextTriggerMillis(
            now = at(2026, 8, 10, 10, 0),
            startHour = 9,
            endHour = 21,
            zone = dhaka,
        )
        assertEquals(at(2026, 8, 10, 11, 0), next)
    }
}
