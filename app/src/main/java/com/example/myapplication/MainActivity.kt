package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.*

class MainActivity : AppCompatActivity() {

    // Live Ringer Views
    private lateinit var tvCurrentRingerState: TextView
    private lateinit var btnManualSilent: Button
    private lateinit var btnManualNormal: Button

    // Timetable Views
    private lateinit var tvStatus: TextView
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnSaveSchedule: Button

    // Geofence Views
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etRadius: EditText
    private lateinit var btnGetCurrentLocation: Button
    private lateinit var tvGeofenceStatus: TextView
    private lateinit var btnSaveGeofence: Button

    // Contacts Views
    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var btnAddContact: Button
    private lateinit var layoutContactsContainer: LinearLayout
    private lateinit var tvNoContacts: TextView

    // Simulator Views
    private lateinit var btnSimulateFamilyEmergency: Button
    private lateinit var btnSimulateUnknownEmergency: Button
    private lateinit var btnStopEmergencyRing: Button
    private var activeTestRingtone: android.media.Ringtone? = null

    private var startH = 9
    private var startM = 0
    private var endH = 16
    private var endM = 30

    private val emergencyContacts = mutableListOf<EmergencyContact>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        initViews()

        val prefs = getSharedPreferences("AutoSilentPrefs", MODE_PRIVATE)
        loadSavedData(prefs)

        // Timetable TimePickers
        btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startH = h
                startM = m
                btnStartTime.text = "Start: %02d:%02d".format(startH, startM)
                updateScheduleText()
            }, startH, startM, false).show()
        }

        btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endH = h
                endM = m
                btnEndTime.text = "End: %02d:%02d".format(endH, endM)
                updateScheduleText()
            }, endH, endM, false).show()
        }

        btnSaveSchedule.setOnClickListener {
            if (checkAndRequestPermissions()) {
                saveAndSchedule(prefs)
            }
        }

        // GPS Geofence Actions
        btnGetCurrentLocation.setOnClickListener {
            fetchCurrentGpsLocation()
        }

        btnSaveGeofence.setOnClickListener {
            setupGeofence(prefs)
        }

        // Emergency Contacts Actions
        btnAddContact.setOnClickListener {
            val name = etContactName.text.toString().trim()
            val phone = etContactPhone.text.toString().trim()

            if (name.isBlank()) {
                etContactName.error = "Enter a name/relationship (e.g., Father)"
                return@setOnClickListener
            }
            if (phone.length < 5) {
                etContactPhone.error = "Enter a valid phone number"
                return@setOnClickListener
            }

            val newContact = EmergencyContact(name, phone)
            emergencyContacts.add(newContact)
            saveContacts(prefs)
            renderContacts(prefs)

            etContactName.text.clear()
            etContactPhone.text.clear()
            Toast.makeText(this, "Added $name to Emergency list", Toast.LENGTH_SHORT).show()
        }

        // Live Ringer Manual Controls
        btnManualSilent.setOnClickListener {
            setSilent(true)
        }

        btnManualNormal.setOnClickListener {
            setSilent(false)
        }

        // Live Emergency Simulation Actions
        btnSimulateFamilyEmergency.setOnClickListener {
            simulateEmergency(
                title = "🚨 3rd Family Call Emergency Triggered!",
                message = "Emergency contact (e.g. Father/Mother) reached 3 calls within 5 minutes!\n\nPhone has been automatically UNSILENCED and ring volume set to maximum!"
            )
        }

        btnSimulateUnknownEmergency.setOnClickListener {
            simulateEmergency(
                title = "🚨 5th Unknown Caller Emergency Triggered!",
                message = "Unknown caller reached 5 calls within 5 minutes!\n\nDetected as crisis. Phone has been automatically UNSILENCED and ring volume set to maximum!"
            )
        }

        btnStopEmergencyRing.setOnClickListener {
            stopSimulation()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentRingerStatus()
    }

    private fun initViews() {
        tvCurrentRingerState = findViewById(R.id.tvCurrentRingerState)
        btnManualSilent = findViewById(R.id.btnManualSilent)
        btnManualNormal = findViewById(R.id.btnManualNormal)

        tvStatus = findViewById(R.id.tvStatus)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule)

        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        etRadius = findViewById(R.id.etRadius)
        btnGetCurrentLocation = findViewById(R.id.btnGetCurrentLocation)
        tvGeofenceStatus = findViewById(R.id.tvGeofenceStatus)
        btnSaveGeofence = findViewById(R.id.btnSaveGeofence)

        etContactName = findViewById(R.id.etContactName)
        etContactPhone = findViewById(R.id.etContactPhone)
        btnAddContact = findViewById(R.id.btnAddContact)
        layoutContactsContainer = findViewById(R.id.layoutContactsContainer)
        tvNoContacts = findViewById(R.id.tvNoContacts)

        btnSimulateFamilyEmergency = findViewById(R.id.btnSimulateFamilyEmergency)
        btnSimulateUnknownEmergency = findViewById(R.id.btnSimulateUnknownEmergency)
        btnStopEmergencyRing = findViewById(R.id.btnStopEmergencyRing)
    }

    private fun loadSavedData(prefs: SharedPreferences) {
        startH = prefs.getInt("start_h", 9)
        startM = prefs.getInt("start_m", 0)
        endH = prefs.getInt("end_h", 16)
        endM = prefs.getInt("end_m", 30)

        btnStartTime.text = "Start: %02d:%02d".format(startH, startM)
        btnEndTime.text = "End: %02d:%02d".format(endH, endM)
        updateScheduleText()

        // Load Geofence Data
        val lat = prefs.getFloat("geofence_lat", 0f)
        val lng = prefs.getFloat("geofence_lng", 0f)
        val radius = prefs.getFloat("geofence_radius", 200f)

        if (lat != 0f && lng != 0f) {
            etLatitude.setText(lat.toString())
            etLongitude.setText(lng.toString())
            etRadius.setText(radius.toInt().toString())
            tvGeofenceStatus.text = "Geofence Active: %.4f, %.4f (Radius: %.0fm)".format(lat, lng, radius)
        } else {
            etRadius.setText("200")
            tvGeofenceStatus.text = "Geofence: Not configured"
        }

        // Load Emergency Contacts
        val savedContactsJson = prefs.getString("emergency_contacts", "[]") ?: "[]"
        emergencyContacts.clear()
        emergencyContacts.addAll(EmergencyContact.listFromJson(savedContactsJson))
        renderContacts(prefs)
    }

    private fun updateScheduleText() {
        tvStatus.text = "Schedule: %02d:%02d → %02d:%02d (Configured)"
            .format(startH, startM, endH, endM)
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentGpsLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 201)
            return
        }

        Toast.makeText(this, "Acquiring GPS location...", Toast.LENGTH_SHORT).show()
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                etLatitude.setText(location.latitude.toString())
                etLongitude.setText(location.longitude.toString())
                Toast.makeText(this, "Location acquired!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to get GPS fix. Ensure GPS is turned on.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGeofence(prefs: SharedPreferences) {
        val latStr = etLatitude.text.toString().trim()
        val lngStr = etLongitude.text.toString().trim()
        val radiusStr = etRadius.text.toString().trim()

        if (latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(this, "Please enter or acquire GPS coordinates first", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latStr.toDoubleOrNull()
        val lng = lngStr.toDoubleOrNull()
        val radius = radiusStr.toFloatOrNull() ?: 200f

        if (lat == null || lng == null) {
            Toast.makeText(this, "Invalid GPS coordinates", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 201)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 202)
                Toast.makeText(this, "Please select 'Allow all the time' for background geofence detection", Toast.LENGTH_LONG).show()
                return
            }
        }

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

        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 203, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                prefs.edit().apply {
                    putFloat("geofence_lat", lat.toFloat())
                    putFloat("geofence_lng", lng.toFloat())
                    putFloat("geofence_radius", radius)
                    apply()
                }
                tvGeofenceStatus.text = "Geofence Active: %.4f, %.4f (Radius: %.0fm)".format(lat, lng, radius)
                Toast.makeText(this, "Campus Geofence Activated! Phone will auto-silent inside.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to register geofence: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun renderContacts(prefs: SharedPreferences) {
        layoutContactsContainer.removeAllViews()

        if (emergencyContacts.isEmpty()) {
            tvNoContacts.visibility = View.VISIBLE
        } else {
            tvNoContacts.visibility = View.GONE
            val inflater = LayoutInflater.from(this)

            for (contact in emergencyContacts) {
                val itemView = inflater.inflate(R.layout.item_contact, layoutContactsContainer, false)
                val tvName = itemView.findViewById<TextView>(R.id.tvContactName)
                val tvPhone = itemView.findViewById<TextView>(R.id.tvContactPhone)
                val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteContact)

                tvName.text = contact.name
                tvPhone.text = contact.phone

                btnDelete.setOnClickListener {
                    emergencyContacts.remove(contact)
                    saveContacts(prefs)
                    renderContacts(prefs)
                    Toast.makeText(this, "Removed ${contact.name}", Toast.LENGTH_SHORT).show()
                }

                layoutContactsContainer.addView(itemView)
            }
        }
    }

    private fun saveContacts(prefs: SharedPreferences) {
        val jsonStr = EmergencyContact.listToJson(emergencyContacts)
        prefs.edit().putString("emergency_contacts", jsonStr).apply()
    }

    private fun saveAndSchedule(prefs: SharedPreferences) {
        prefs.edit().apply {
            putInt("start_h", startH)
            putInt("start_m", startM)
            putInt("end_h", endH)
            putInt("end_m", endM)
            apply()
        }

        scheduleAlarm(startH, startM, "ACTION_START_SILENT", 101)
        scheduleAlarm(endH, endM, "ACTION_STOP_SILENT", 102)

        val isNowInClass = isCurrentTimeInSchedule(startH, startM, endH, endM)
        if (isNowInClass) {
            setSilent(true)
            Toast.makeText(this, "Timetable Saved! Class time active NOW ➔ Phone SILENCED!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Timetable Saved! Scheduled to silence at %02d:%02d".format(startH, startM), Toast.LENGTH_LONG).show()
            updateCurrentRingerStatus()
        }
    }

    private fun isCurrentTimeInSchedule(startH: Int, startM: Int, endH: Int, endM: Int): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    private fun setSilent(isSilent: Boolean) {
        if (isSilent) {
            SoundController.setSilent(this)
            Toast.makeText(this, "🔕 Phone set to 0 volume (SILENT)", Toast.LENGTH_SHORT).show()
        } else {
            SoundController.setNormal(this)
            Toast.makeText(this, "🔔 Phone restored to NORMAL sound", Toast.LENGTH_SHORT).show()
        }
        updateCurrentRingerStatus()
    }

    private fun updateCurrentRingerStatus() {
        val isMuted = SoundController.isSilent(this)
        if (isMuted) {
            tvCurrentRingerState.text = "Current Mode: 🔕 SILENT (Muted)"
            tvCurrentRingerState.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        } else {
            tvCurrentRingerState.text = "Current Mode: 🔔 NORMAL (Ringing)"
            tvCurrentRingerState.setTextColor(android.graphics.Color.parseColor("#059669"))
        }
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

    private fun checkAndRequestPermissions(): Boolean {
        // 1. Notification Policy (DND)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Please allow Do Not Disturb (DND) access", Toast.LENGTH_LONG).show()
            return false
        }

        // 2. Exact Alarm (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                Toast.makeText(this, "Please allow exact alarm permission", Toast.LENGTH_LONG).show()
                return false
            }
        }

        // 3. Phone state & Call Log permissions (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsNeeded = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_PHONE_STATE)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_CALL_LOG)
            }

            if (permissionsNeeded.isNotEmpty()) {
                requestPermissions(permissionsNeeded.toTypedArray(), 100)
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
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            saveAndSchedule(getSharedPreferences("AutoSilentPrefs", MODE_PRIVATE))
        } else if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentGpsLocation()
        }
    }

    private fun simulateEmergency(title: String, message: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Please grant DND permission first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }

        SoundController.triggerEmergencyLoud(this)
        updateCurrentRingerStatus()

        try {
            activeTestRingtone?.stop()
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            activeTestRingtone = android.media.RingtoneManager.getRingtone(this, uri)
            activeTestRingtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK / Stop Ring") { _, _ ->
                stopSimulation()
            }
            .setCancelable(false)
            .show()
    }

    private fun stopSimulation() {
        try {
            activeTestRingtone?.stop()
            activeTestRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SoundController.setSilent(this)
        updateCurrentRingerStatus()
        Toast.makeText(this, "Phone returned to Silent Mode", Toast.LENGTH_SHORT).show()
    }
}
