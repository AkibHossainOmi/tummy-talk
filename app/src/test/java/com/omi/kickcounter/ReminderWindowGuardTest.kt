package com.omi.kickcounter

import com.omi.kickcounter.reminder.ReminderScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The receiver re-checks the window before making any sound. These cover the paths
 * that can deliver an alarm outside the hours she chose.
 */
class ReminderWindowGuardTest {

    @Test
    fun `a snooze that crosses the end of the window must not sound`() {
        // Snoozed at 8:45 PM for 30 minutes lands at 9:15 PM, past a 9 PM cutoff.
        assertFalse(ReminderScheduler.isInsideWindow(21, 9, 21))
    }

    @Test
    fun `a snooze still inside the window may sound`() {
        // Snoozed at 7:45 PM for 30 minutes lands at 8:15 PM, comfortably inside.
        assertTrue(ReminderScheduler.isInsideWindow(20, 9, 21))
    }

    @Test
    fun `a degenerate window rejects every hour`() {
        (0..23).forEach { hour ->
            assertFalse("hour $hour", ReminderScheduler.isInsideWindow(hour, 9, 9))
        }
    }

    @Test
    fun `an overnight window rejects the middle of the day`() {
        assertFalse(ReminderScheduler.isInsideWindow(13, 22, 6))
        assertTrue(ReminderScheduler.isInsideWindow(1, 22, 6))
    }
}
