package com.example.myapplication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("AutoSilent", "Geofence error: $errorMessage")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)

        when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("AutoSilent", "📍 GEOFENCE ENTER: Entered College/School zone!")
                prefs.edit().putBoolean("is_inside_geofence", true).apply()
                SoundController.setSilent(context)
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("AutoSilent", "📍 GEOFENCE EXIT: Left College/School zone!")
                prefs.edit().putBoolean("is_inside_geofence", false).apply()
                SoundController.setNormal(context)
            }
        }
    }
}
