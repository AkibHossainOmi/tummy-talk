package com.omi.kickcounter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.omi.kickcounter.appContainer
import kotlinx.coroutines.launch

/**
 * Handles the notification's action buttons. Kept as a receiver rather than a
 * service command so a tap is recorded even if the system is being stingy — the
 * insert is a few milliseconds of work and the service redraws the notification
 * reactively once the row lands.
 */
class KickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    ACTION_KICK -> {
                        val settings = container.settingsStore.current()
                        container.repository.record(settings.dailyGoal, settings.groupingMinutes)
                        vibrate(context, 30)
                    }
                    ACTION_UNDO -> {
                        if (container.repository.undoLast()) vibrate(context, 15)
                    }
                    ACTION_REDO -> {
                        if (container.repository.redoLast()) vibrate(context, 30)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Silent tactile confirmation, so she doesn't have to look at the screen. */
    private fun vibrate(context: Context, millis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        const val ACTION_KICK = "com.omi.kickcounter.ACTION_KICK"
        const val ACTION_UNDO = "com.omi.kickcounter.ACTION_UNDO"
        const val ACTION_REDO = "com.omi.kickcounter.ACTION_REDO"
    }
}
