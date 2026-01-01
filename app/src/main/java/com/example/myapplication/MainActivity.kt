package com.example.myapplication

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etWhitelist: EditText

    private var startH = 9
    private var startM = 0
    private var endH = 17
    private var endM = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etWhitelist = findViewById(R.id.etWhitelist)

        val prefs = getSharedPreferences("AutoSilentPrefs", MODE_PRIVATE)
        loadSavedData(prefs)

        findViewById<Button>(R.id.btnStartTime).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startH = h
                startM = m
                updateUI()
            }, startH, startM, false).show()
        }

        findViewById<Button>(R.id.btnEndTime).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endH = h
                endM = m
                updateUI()
            }, endH, endM, false).show()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (checkPermissions()) {
                saveAndSchedule(prefs)
            }
        }
    }

    private fun loadSavedData(prefs: SharedPreferences) {
        startH = prefs.getInt("start_h", 9)
        startM = prefs.getInt("start_m", 0)
        endH = prefs.getInt("end_h", 17)
        endM = prefs.getInt("end_m", 0)
        etWhitelist.setText(prefs.getString("whitelist", ""))
        updateUI()
    }

    private fun updateUI() {
        tvStatus.text = "Silent: %02d:%02d → %02d:%02d"
            .format(startH, startM, endH, endM)
    }

    private fun checkPermissions(): Boolean {

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Allow DND access", Toast.LENGTH_LONG).show()
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                return false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_PHONE_STATE,
                        android.Manifest.permission.READ_CALL_LOG
                    ), 100
                )
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            saveAndSchedule(getSharedPreferences("AutoSilentPrefs", MODE_PRIVATE))
        }
    }

    private fun saveAndSchedule(prefs: SharedPreferences) {
        prefs.edit().apply {
            putInt("start_h", startH)
            putInt("start_m", startM)
            putInt("end_h", endH)
            putInt("end_m", endM)
            putString("whitelist", etWhitelist.text.toString())
            apply()
        }

        scheduleAlarm(startH, startM, "ACTION_START_SILENT", 101)
        scheduleAlarm(endH, endM, "ACTION_STOP_SILENT", 102)

        Toast.makeText(this, "Auto Silent Activated", Toast.LENGTH_LONG).show()
    }

    private fun scheduleAlarm(h: Int, m: Int, action: String, code: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            this.action = action
        }

        val pi = PendingIntent.getBroadcast(
            this, code, intent,
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
}
