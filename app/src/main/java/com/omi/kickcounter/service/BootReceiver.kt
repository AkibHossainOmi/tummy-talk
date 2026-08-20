package com.omi.kickcounter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omi.kickcounter.appContainer
import com.omi.kickcounter.reminder.ReminderScheduler
import kotlinx.coroutines.launch

/**
 * Brings the notification back after a reboot or an app update, and re-arms the
 * hourly reminder — alarms do not survive either event.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                KickService.start(context)
                val container = context.appContainer
                val pending = goAsync()
                container.applicationScope.launch {
                    try {
                        ReminderScheduler.reschedule(context, container.settingsStore.current())
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
