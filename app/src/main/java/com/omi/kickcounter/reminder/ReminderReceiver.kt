package com.omi.kickcounter.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omi.kickcounter.KickCounterApp
import com.omi.kickcounter.R
import com.omi.kickcounter.appContainer
import com.omi.kickcounter.domain.KickStats
import com.omi.kickcounter.service.KickActionReceiver
import com.omi.kickcounter.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Fires on the hour inside the waking window. It only makes a sound when nothing
 * at all was logged in the hour that just ended — if she has been counting, it
 * stays quiet and simply schedules the next check.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()

        container.applicationScope.launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(context).cancel(REMINDER_ID)
                        ReminderScheduler.snooze(context, TimeUnit.MINUTES.toMillis(30))
                        return@launch
                    }
                    ACTION_DISMISS -> {
                        NotificationManagerCompat.from(context).cancel(REMINDER_ID)
                    }
                    else -> check(context)
                }

                val settings = container.settingsStore.current()
                ReminderScheduler.reschedule(context, settings)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun check(context: Context) {
        val container = context.appContainer
        val settings = container.settingsStore.current()
        if (!settings.remindersEnabled) return

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        // The schedule normally guarantees this, but a snooze, a clock change or a
        // degenerate window can land the alarm outside waking hours. Never make a
        // sound she did not ask for.
        val hourNow = java.time.Instant.ofEpochMilli(now).atZone(zone).hour
        if (!ReminderScheduler.isInsideWindow(
                hourNow,
                settings.reminderStartHour,
                settings.reminderEndHour,
            )
        ) {
            return
        }

        val thisHourStart = KickStats.startOfHour(now, zone)
        val previousHourStart = thisHourStart - TimeUnit.HOURS.toMillis(1)

        // Judge the hour that just finished, not the one a second old.
        val logged = container.repository.countBetween(previousHourStart, thisHourStart - 1)
        if (logged > 0) return

        val hourLabel = formatHour(
            java.time.Instant.ofEpochMilli(previousHourStart).atZone(zone).hour,
        )
        notify(context, hourLabel)
    }

    private fun notify(context: Context, hourLabel: String) {
        val notification = NotificationCompat.Builder(context, KickCounterApp.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footprint)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text, hourLabel))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.reminder_big_text, hourLabel)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setDeleteIntent(broadcast(context, ACTION_DISMISS, REQ_DISMISS))
            .addAction(
                R.drawable.ic_footprint,
                context.getString(R.string.action_kick),
                logMovementIntent(context),
            )
            .addAction(
                R.drawable.ic_undo,
                context.getString(R.string.action_snooze),
                broadcast(context, ACTION_SNOOZE, REQ_SNOOZE),
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
        }
    }

    private fun logMovementIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_LOG,
        Intent(context, KickActionReceiver::class.java).setAction(KickActionReceiver.ACTION_KICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQ_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    companion object {
        const val ACTION_CHECK = "com.omi.kickcounter.ACTION_REMINDER_CHECK"
        const val ACTION_SNOOZE = "com.omi.kickcounter.ACTION_REMINDER_SNOOZE"
        const val ACTION_DISMISS = "com.omi.kickcounter.ACTION_REMINDER_DISMISS"

        const val REMINDER_ID = 1002

        private const val REQ_LOG = 101
        private const val REQ_SNOOZE = 102
        private const val REQ_OPEN = 103
        private const val REQ_DISMISS = 104
    }
}
