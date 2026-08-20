package com.omi.kickcounter.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.omi.kickcounter.KickCounterApp
import com.omi.kickcounter.R
import com.omi.kickcounter.appContainer
import com.omi.kickcounter.domain.Formatting
import com.omi.kickcounter.domain.KickSnapshot
import com.omi.kickcounter.domain.SnapshotProvider
import com.omi.kickcounter.ui.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the always-on notification alive. The notification is the app's primary
 * input surface — she should never have to open the app to log a movement.
 */
class KickService : LifecycleService() {

    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    /** The most recent notification, kept so a dismissal can be undone instantly. */
    @Volatile
    private var lastNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()

        // Must happen within a few seconds of start, before any data is loaded.
        startInForeground(placeholderNotification())

        val container = appContainer
        val provider = SnapshotProvider(container.repository, container.settingsStore)
        lifecycleScope.launch {
            provider.snapshots().collectLatest { snapshot ->
                notify(buildNotification(snapshot))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_RESTORE) {
            // Android 13+ lets the user swipe a foreground-service notification away
            // while the service keeps running. Since the notification *is* the way she
            // logs a movement, a dismissal is always accidental — put it straight back.
            startInForeground(lastNotification ?: placeholderNotification())
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                KickCounterApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(KickCounterApp.NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        lastNotification = notification
        // POST_NOTIFICATIONS is requested at launch; if it was revoked the service
        // simply keeps running with whatever the system last showed.
        runCatching { notificationManager.notify(KickCounterApp.NOTIFICATION_ID, notification) }
    }

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, KickCounterApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footprint)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            // setOngoing blocks the swipe on older releases; the delete intent covers
            // Android 13+, where the swipe and "Clear all" are allowed through.
            .setDeleteIntent(restoreIntent())

    private fun placeholderNotification(): Notification =
        baseNotification()
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_loading))
            .build()

    private fun buildNotification(s: KickSnapshot): Notification {
        val title = "${s.age.format()}  ·  ${getString(R.string.today)} ${s.todayCount}"
        val text = buildString {
            append(getString(R.string.this_hour)).append(' ').append(s.thisHourCount)
            append("  ·  ").append(Formatting.relative(s.lastKick, s.now))
        }
        val session = s.activeSession
        val sub = when {
            session == null -> getString(R.string.session_idle)
            session.count >= session.goal -> getString(R.string.goal_reached, session.goal)
            else -> getString(R.string.session_progress, session.count, session.goal)
        }

        return baseNotification()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(text))
            .addAction(
                R.drawable.ic_footprint,
                movementActionLabel(s.settings.babyName),
                actionIntent(KickActionReceiver.ACTION_KICK, REQ_KICK),
            )
            .addAction(
                R.drawable.ic_undo,
                getString(R.string.action_undo),
                actionIntent(KickActionReceiver.ACTION_UNDO, REQ_UNDO),
            )
            .apply {
                // A third button is only worth the clutter while an undo is fresh.
                if (s.redoIsFresh) {
                    addAction(
                        R.drawable.ic_redo,
                        getString(R.string.action_redo),
                        actionIntent(KickActionReceiver.ACTION_REDO, REQ_REDO),
                    )
                }
            }
            .build()
    }

    private fun movementActionLabel(babyName: String): String =
        if (babyName.isBlank()) {
            getString(R.string.action_kick)
        } else {
            getString(R.string.action_kick_named, babyName)
        }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, KickActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun restoreIntent(): PendingIntent {
        val intent = Intent(this, KickService::class.java).setAction(ACTION_RESTORE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, REQ_RESTORE, intent, flags)
        } else {
            PendingIntent.getService(this, REQ_RESTORE, intent, flags)
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQ_OPEN,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val ACTION_RESTORE = "com.omi.kickcounter.ACTION_RESTORE"

        private const val REQ_KICK = 1
        private const val REQ_UNDO = 2
        private const val REQ_OPEN = 3
        private const val REQ_RESTORE = 4
        private const val REQ_REDO = 5

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KickService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KickService::class.java))
        }
    }
}
