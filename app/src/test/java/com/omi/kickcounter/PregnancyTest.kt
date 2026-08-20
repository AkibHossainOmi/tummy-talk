package com.omi.kickcounter

import com.omi.kickcounter.domain.Pregnancy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PregnancyTest {

    @Test
    fun `default LMP puts 2026-08-09 at exactly 24 weeks`() {
        val age = Pregnancy.ageAt(Pregnancy.DEFAULT_LMP, LocalDate.of(2026, 8, 9))
        assertEquals(24, age.weeks)
        assertEquals(0, age.days)
        assertEquals(168, age.totalDays)
    }

    @Test
    fun `due date is 280 days after LMP`() {
        assertEquals(LocalDate.of(2026, 11, 29), Pregnancy.dueDate(Pregnancy.DEFAULT_LMP))
    }

    @Test
    fun `days until due counts down`() {
        val days = Pregnancy.daysUntilDue(Pregnancy.DEFAULT_LMP, LocalDate.of(2026, 8, 9))
        assertEquals(112, days)
    }

    @Test
    fun `trimester boundaries follow the 13 and 28 week convention`() {
        val lmp = LocalDate.of(2026, 1, 1)
        assertEquals(1, Pregnancy.ageAt(lmp, lmp.plusWeeks(12)).trimester)
        assertEquals(2, Pregnancy.ageAt(lmp, lmp.plusWeeks(13)).trimester)
        assertEquals(2, Pregnancy.ageAt(lmp, lmp.plusWeeks(27)).trimester)
        assertEquals(3, Pregnancy.ageAt(lmp, lmp.plusWeeks(28)).trimester)
    }

    @Test
    fun `a date before the LMP clamps to zero rather than going negative`() {
        val age = Pregnancy.ageAt(LocalDate.of(2026, 2, 22), LocalDate.of(2026, 2, 1))
        assertEquals(0, age.totalDays)
    }

    @Test
    fun `partial weeks are reported as weeks and days`() {
        val age = Pregnancy.ageAt(Pregnancy.DEFAULT_LMP, LocalDate.of(2026, 8, 12))
        assertEquals("24w 3d", age.format())
    }
}
