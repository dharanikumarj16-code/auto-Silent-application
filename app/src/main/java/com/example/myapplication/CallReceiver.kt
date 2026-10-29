package com.example.myapplication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONArray
import java.util.*

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incoming = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (incoming.isNotBlank()) {
                    val cleanIncoming = incoming.filter { it.isDigit() }.takeLast(10)
                    if (cleanIncoming.isBlank()) return

                    val now = System.currentTimeMillis()
                    val windowMillis = 5 * 60 * 1000L // 5 minutes window

                    // 1. Check if the caller is an Emergency Contact (Father, Mother, Sister, etc.)
                    val contactsJson = prefs.getString("emergency_contacts", "[]") ?: "[]"
                    val emergencyContacts = EmergencyContact.listFromJson(contactsJson)
                    val matchingContact = emergencyContacts.firstOrNull {
                        it.phone.filter { c -> c.isDigit() }.takeLast(10) == cleanIncoming
                    }
                    val isEmergencyContact = matchingContact != null

                    // 2. Sliding 5-minute call history per number
                    val historyKey = "calls_$cleanIncoming"
                    val savedHistory = prefs.getString(historyKey, "[]") ?: "[]"
                    val recentCalls = mutableListOf<Long>()

                    try {
                        val jsonArr = JSONArray(savedHistory)
                        for (i in 0 until jsonArr.length()) {
                            val timestamp = jsonArr.getLong(i)
                            if (now - timestamp <= windowMillis) {
                                recentCalls.add(timestamp)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Add current call timestamp
                    recentCalls.add(now)

                    // Persist updated call timestamps
                    val newJsonArr = JSONArray()
                    recentCalls.forEach { newJsonArr.put(it) }
                    prefs.edit().putString(historyKey, newJsonArr.toString()).apply()

                    val callCountIn5Min = recentCalls.size
                    val contactLabel = matchingContact?.name ?: "Unknown Caller"
                    Log.d("AutoSilent", "Call from: $cleanIncoming ($contactLabel) | Count in 5 min: $callCountIn5Min")

                    // 3. Determine if emergency threshold is met:
                    //    - Emergency Contacts: 3 calls within 5 mins
                    //    - Unknown / Other Numbers: 5 or more calls within 5 mins
                    val shouldTriggerEmergency = if (isEmergencyContact) {
                        callCountIn5Min >= 3
                    } else {
                        callCountIn5Min >= 5
                    }

                    if (shouldTriggerEmergency) {
                        Log.d("AutoSilent", "🚨 EMERGENCY TRIGGERED for $contactLabel ($callCountIn5Min calls in 5m)! Unsilencing phone...")
                        prefs.edit().putBoolean("is_emergency_active", true).apply()
                        SoundController.triggerEmergencyLoud(context)

                        try {
                            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val ringtone = RingtoneManager.getRingtone(context, alertUri)
                            ringtone?.play()
                        } catch (e: Exception) {
                            Log.e("AutoSilent", "Error playing ringtone", e)
                        }
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended or missed: If still inside college hours/geofence, revert back to silent
                if (isInsideSilentWindow(prefs)) {
                    SoundController.setSilent(context)
                    Log.d("AutoSilent", "Call ended. Phone reverted to 0 volume SILENT mode")
                }
                prefs.edit().putBoolean("is_emergency_active", false).apply()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                prefs.edit().putBoolean("is_emergency_active", false).apply()
            }
        }
    }

    private fun isInsideSilentWindow(prefs: android.content.SharedPreferences): Boolean {
        if (prefs.getBoolean("is_inside_geofence", false)) {
            return true
        }

        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val start = prefs.getInt("start_h", 9) * 60 + prefs.getInt("start_m", 0)
        val end = prefs.getInt("end_h", 16) * 60 + prefs.getInt("end_m", 30)

        return current in start..end
    }
}
