package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log
import java.util.*

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastCallNumber: String? = null
        private var lastCallTime: Long = 0
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incoming = intent.extras?.getString(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (incoming.isNotEmpty()) {
                    val whitelist = prefs.getString("whitelist", "") ?: ""
                    val cleanIncoming = incoming.takeLast(10)
                    val cleanWhitelist = whitelist.takeLast(10)
                    val currentTime = System.currentTimeMillis()
                    val isWhitelisted =
                        cleanWhitelist.isNotEmpty() && cleanIncoming == cleanWhitelist
                    val isRepeated =
                        (cleanIncoming == lastCallNumber?.takeLast(10) && (currentTime - lastCallTime) < 300000)

                    Log.d("AutoSilent", "Call: $incoming | W:$isWhitelisted | R:$isRepeated")

                    if (isWhitelisted && isRepeated) {
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d("AutoSilent", "Emergency Ring ON")
                        lastCallNumber = null
                        lastCallTime = 0
                    } else {
                        lastCallNumber = incoming
                        lastCallTime = currentTime
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isInsideSilentWindow(prefs)) {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    Log.d("AutoSilent", "Return to SILENT")
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                lastCallNumber = null
                lastCallTime = 0
            }
        }
    }

    private fun isInsideSilentWindow(prefs: android.content.SharedPreferences): Boolean {
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val start = prefs.getInt("start_h", 9) * 60 + prefs.getInt("start_m", 15)
        val end = prefs.getInt("end_h", 16) * 60 + prefs.getInt("end_m", 30)

        return current in start..end
    }
}
