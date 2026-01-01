package com.example.myapplication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check DND permission (MANDATORY)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e("AutoSilent", "DND permission not granted")
            return
        }

        when (intent.action) {

            "ACTION_START_SILENT" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                Log.d("AutoSilent", "Silent mode activated")
            }

            "ACTION_STOP_SILENT" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Log.d("AutoSilent", "Silent mode deactivated")
            }
        }
    }
}
