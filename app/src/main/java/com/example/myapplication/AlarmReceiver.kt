package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)

        // Check DND permission
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e("AutoSilent", "DND / Notification policy access not granted")
            return
        }

        when (intent.action) {
            "ACTION_START_SILENT" -> {
                SoundController.setSilent(context)
                Log.d("AutoSilent", "College timetable START: Phone set to 0 volume (SILENT)")
                // Reschedule for next day
                val sh = prefs.getInt("start_h", 9)
                val sm = prefs.getInt("start_m", 0)
                rescheduleNextDay(context, sh, sm, "ACTION_START_SILENT", 101)
            }

            "ACTION_STOP_SILENT" -> {
                SoundController.setNormal(context)
                Log.d("AutoSilent", "College timetable END: Phone restored to NORMAL sound")
                // Reschedule for next day
                val eh = prefs.getInt("end_h", 16)
                val em = prefs.getInt("end_m", 30)
                rescheduleNextDay(context, eh, em, "ACTION_STOP_SILENT", 102)
            }
        }
    }

    private fun rescheduleNextDay(context: Context, h: Int, m: Int, action: String, code: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }

        val pi = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            add(Calendar.DATE, 1) // Schedule for tomorrow
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }

        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pi
        )
    }
}
