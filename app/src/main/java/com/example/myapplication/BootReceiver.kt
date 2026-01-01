package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val prefs = context.getSharedPreferences("AutoSilentPrefs", Context.MODE_PRIVATE)
            val sh = prefs.getInt("start_h", -1)
            val sm = prefs.getInt("start_m", -1)
            val eh = prefs.getInt("end_h", -1)
            val em = prefs.getInt("end_m", -1)

            if (sh != -1 && eh != -1) {
                scheduleDaily(context, sh, sm, "ACTION_START_SILENT", 101)
                scheduleDaily(context, eh, em, "ACTION_STOP_SILENT", 102)
            }
        }
    }

    private fun scheduleDaily(
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

        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }
}
