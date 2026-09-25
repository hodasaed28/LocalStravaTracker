package com.example.localstravatracker.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.localstravatracker.MainActivity
import com.example.localstravatracker.TrackerApplication
import com.example.localstravatracker.data.Run
import com.example.localstravatracker.data.TrackPoint
import com.example.localstravatracker.utils.GpsFilter
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.timerTask

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var timer: Timer? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1001

        private val _isTracking = MutableStateFlow(false)
        val isTracking = _isTracking.asStateFlow()

        private val _durationSeconds = MutableStateFlow(0L)
        val durationSeconds = _durationSeconds.asStateFlow()

        private val _distanceMeters = MutableStateFlow(0f)
        val distanceMeters = _distanceMeters.asStateFlow()

        private val _currentSpeedKmh = MutableStateFlow(0f)
        val currentSpeedKmh = _currentSpeedKmh.asStateFlow()

        private val _pathPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
        val pathPoints = _pathPoints.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!_isTracking.value) return
                for (location in result.locations) {
                    processLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        _isTracking.value = true
        _durationSeconds.value = 0L
        _distanceMeters.value = 0f
        _currentSpeedKmh.value = 0f
        _pathPoints.value = emptyList()

        startForeground(NOTIFICATION_ID, buildNotification("بدأ الجري! جاري التتبع..."))
        startTimer()
        requestLocationUpdates()
    }

    private fun stopTracking() {
        _isTracking.value = false
        timer?.cancel()
        timer = null
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val recordedPoints = _pathPoints.value
        val totalDistance = _distanceMeters.value
        val duration = _durationSeconds.value

        if (recordedPoints.isNotEmpty() && duration > 0) {
            serviceScope.launch {
                val app = application as TrackerApplication
                val avgSpeed = (totalDistance / 1000f) / (duration / 3600f)
                val maxSpeed = recordedPoints.maxOfOrNull { it.speedKmh } ?: 0f
                val calories = (totalDistance * 0.06f).toInt()

                val run = Run(
                    durationMillis = duration * 1000L,
                    distanceMeters = totalDistance,
                    avgSpeedKmh = if (avgSpeed.isNaN()) 0f else avgSpeed,
                    maxSpeedKmh = maxSpeed,
                    caloriesBurned = calories
                )
                val runId = app.database.runDao().insertRun(run)
                val pointsToSave = recordedPoints.map { it.copy(runId = runId) }
                app.database.runDao().insertPoints(pointsToSave)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimer() {
        timer = Timer()
        timer?.scheduleAtFixedRate(timerTask {
            _durationSeconds.value += 1
            updateNotification()
        }, 1000L, 1000L)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private var lastLocation: Location? = null

    private fun processLocation(location: Location) {
        if (!GpsFilter.isValid(location)) return

        val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
        _currentSpeedKmh.value = speed

        val point = TrackPoint(
            runId = 0,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speedKmh = speed,
            timestamp = location.time
        )

        lastLocation?.let { prev ->
            val dist = prev.distanceTo(location)
            _distanceMeters.value += dist
        }
        lastLocation = location

        val updated = _pathPoints.value.toMutableList()
        updated.add(point)
        _pathPoints.value = updated
    }

    private fun buildNotification(content: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Local Strava Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()

    private fun updateNotification() {
        val distKm = String.format("%.2f", _distanceMeters.value / 1000f)
        val mins = _durationSeconds.value / 60
        val secs = _durationSeconds.value % 60
        val text = "المسافة: $distKm كم | الوقت: %02d:%02d".format(mins, secs)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "تتبع الجري في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}