package com.omi.kickcounter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.omi.kickcounter.data.KickDatabase
import com.omi.kickcounter.data.KickRepository
import com.omi.kickcounter.data.SettingsStore
import com.omi.kickcounter.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KickCounterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()

        container.applicationScope.launch {
            ReminderScheduler.reschedule(this@KickCounterApp, container.settingsStore.current())
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Deliberately IMPORTANCE_LOW: the persistent counter must be permanently
        // visible but must never make a sound — she has it up overnight.
        val counter = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // The reminder channel is the only one allowed to make a noise.
        val reminders = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.reminder_channel_description)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build(),
            )
        }

        manager.createNotificationChannel(counter)
        manager.createNotificationChannel(reminders)
    }

    companion object {
        const val CHANNEL_ID = "kick_counter_persistent"
        const val REMINDER_CHANNEL_ID = "kick_counter_reminders"
        const val NOTIFICATION_ID = 1001
    }
}

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = KickRepository(KickDatabase.get(context))
    val settingsStore = SettingsStore(context)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KickCounterApp).container
