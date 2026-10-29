package com.example.myapplication

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log

object SoundController {

    fun setSilent(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            // Save current volume before muting
            val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_RING)
            if (currentVol > 0) {
                prefs.edit().putInt("saved_ring_volume", currentVol).apply()
            }

            // Set ring & notification volume directly to 0 (Silent)
            am.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_SHOW_UI)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)

            // Switch to Vibrate/Silent mode without triggering OS revert
            if (nm.isNotificationPolicyAccessGranted) {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }

            Log.d("AutoSilent", "SoundController: Volume set to 0 (SILENT)")
        } catch (e: Exception) {
            Log.e("AutoSilent", "SoundController: Error setting silent volume", e)
        }
    }

    fun setNormal(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            val savedVol = prefs.getInt("saved_ring_volume", (maxVol * 0.7).toInt())
            val targetVol = if (savedVol > 0) savedVol else (maxVol * 0.7).toInt()

            if (nm.isNotificationPolicyAccessGranted) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            am.setStreamVolume(AudioManager.STREAM_RING, targetVol, AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVol, 0)

            Log.d("AutoSilent", "SoundController: Volume restored to NORMAL ($targetVol)")
        } catch (e: Exception) {
            Log.e("AutoSilent", "SoundController: Error restoring volume", e)
        }
    }

    fun triggerEmergencyLoud(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            if (nm.isNotificationPolicyAccessGranted) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            am.setStreamVolume(AudioManager.STREAM_RING, maxVol, AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVol, 0)

            Log.d("AutoSilent", "SoundController: EMERGENCY LOUD TRIGGERED (Volume: $maxVol)")
        } catch (e: Exception) {
            Log.e("AutoSilent", "SoundController: Error triggering emergency volume", e)
        }
    }

    fun isSilent(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVol = am.getStreamVolume(AudioManager.STREAM_RING)
        return currentVol == 0 || am.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }
}
