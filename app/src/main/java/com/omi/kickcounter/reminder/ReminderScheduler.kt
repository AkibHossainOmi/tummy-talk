package com.omi.kickcounter.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.omi.kickcounter.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules the hourly check on the hour, but only inside the waking window.
 *
 * Reminders are deliberately never scheduled overnight: a sleeping baby is normal
 * for 20-40 minutes at a time, so an alert outside waking hours would be a false
 * alarm that costs her sleep.
 */
object ReminderScheduler {

    fun reschedule(context: Context, settings: Settings, zone: ZoneId = ZoneId.systemDefault()) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)

        manager.cancel(pending)
        if (!settings.remindersEnabled) return

        val triggerAt = nextTriggerMillis(
            now = System.currentTimeMillis(),
            startHour = settings.reminderStartHour,
            endHour = settings.reminderEndHour,
            zone = zone,
        )

        // Exact delivery is nice-to-have, not load-bearing: an hourly nudge that
        // slips a few minutes is still useful, so fall back rather than nag for
        // the permission.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        if (canBeExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun snooze(context: Context, delayMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + delayMillis
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (canBeExact) {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context),
            )
        } else {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context),
            )
        }
    }

    /**
     * The next top of the hour that falls inside the waking window. If the window
     * has closed for today, this is the opening hour tomorrow.
     */
    fun nextTriggerMillis(now: Long, startHour: Int, endHour: Int, zone: ZoneId): Long {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var candidate = current
            .withMinute(0).withSecond(0).withNano(0)
            .plusHours(1)

        repeat(48) {
            if (isInsideWindow(candidate.hour, startHour, endHour)) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusHours(1)
        }
        // Degenerate configuration; fall back to the opening hour tomorrow.
        return openingTomorrow(current.toLocalDate(), startHour, zone)
    }

    /**
     * Windows are inclusive of [startHour] and exclusive of [endHour], and a window
     * that wraps past midnight is supported.
     */
    fun isInsideWindow(hour: Int, startHour: Int, endHour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    private fun openingTomorrow(today: LocalDate, startHour: Int, zone: ZoneId): Long =
        ZonedDateTime.of(today.plusDays(1), LocalTime.of(startHour, 0), zone)
            .toInstant().toEpochMilli()

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_CODE = 100
}
