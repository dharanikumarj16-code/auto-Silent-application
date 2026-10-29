package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.*

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)

            // 1. Restore Daily Timetable Alarms
            val sh = prefs.getInt("start_h", -1)
            val sm = prefs.getInt("start_m", -1)
            val eh = prefs.getInt("end_h", -1)
            val em = prefs.getInt("end_m", -1)

            if (sh != -1 && eh != -1) {
                scheduleExact(context, sh, sm, "ACTION_START_SILENT", 101)
                scheduleExact(context, eh, em, "ACTION_STOP_SILENT", 102)
                Log.d("AutoSilent", "BootReceiver: Restored timetable alarms")
            }

            // 2. Restore Campus Geofence
            val lat = prefs.getFloat("geofence_lat", 0f)
            val lng = prefs.getFloat("geofence_lng", 0f)
            val radius = prefs.getFloat("geofence_radius", 200f)

            if (lat != 0f && lng != 0f) {
                restoreGeofence(context, lat.toDouble(), lng.toDouble(), radius)
            }
        }
    }

    private fun scheduleExact(
        context: Context, h: Int, m: Int, action: String, code: Int
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply { this.action = action }

        val pi = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pi
        )
    }

    @SuppressLint("MissingPermission")
    private fun restoreGeofence(context: Context, lat: Double, lng: Double, radius: Float) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            val geofencingClient = LocationServices.getGeofencingClient(context)
            val geofence = Geofence.Builder()
                .setRequestId("CAMPUS_GEOFENCE")
                .setCircularRegion(lat, lng, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()

            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            val geofenceIntent = Intent(context, GeofenceBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 203, geofenceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.d("AutoSilent", "BootReceiver: Campus Geofence restored successfully")
                }
        } catch (e: Exception) {
            Log.e("AutoSilent", "BootReceiver: Failed to restore geofence", e)
        }
    }
}
