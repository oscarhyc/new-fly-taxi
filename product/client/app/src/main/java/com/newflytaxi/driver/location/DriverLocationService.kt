package com.newflytaxi.driver.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.newflytaxi.driver.BuildConfig
import com.newflytaxi.driver.data.ApiFactory
import com.newflytaxi.driver.data.DriverApi
import com.newflytaxi.driver.data.SessionStore
import com.newflytaxi.driver.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Real-device location service (Plan B).
 *
 * Cadence mirrors the tunables recovered from the shipped config:
 *   idle          -> every 600 s   (gps_update_normal_sec)
 *   on an order   -> every  20 s   (gps_update_accepted_book_sec)
 *
 * A foreground service is required: the app must keep reporting while the driver
 * is on a trip, and Android will kill a backgrounded process otherwise.
 */
object LocationHub {
    private val _position = MutableStateFlow<Pair<Double, Double>?>(null)
    val position: StateFlow<Pair<Double, Double>?> = _position
    private val _speed = MutableStateFlow(0.0)
    val speed: StateFlow<Double> = _speed

    fun publish(lat: Double, lng: Double, spd: Double) {
        _position.value = lat to lng
        _speed.value = spd
    }
}

class DriverLocationService : android.app.Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient
    private var api: DriverApi? = null
    private var activeBid: String? = null
    private var onOrder = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            LocationHub.publish(loc.latitude, loc.longitude, loc.speed.toDouble())
            scope.launch {
                api?.gps(
                    com.newflytaxi.driver.data.GpsRequest(
                        lat = loc.latitude, lng = loc.longitude,
                        speed = loc.speed.toDouble(), bid = activeBid,
                    )
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        api = ApiFactory.create(SessionStore(this), BuildConfig.API_BASE_URL)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ON_ORDER -> { onOrder = true; activeBid = intent.getStringExtra(EXTRA_BID) }
            ACTION_IDLE -> { onOrder = false; activeBid = null }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        val interval = if (onOrder) ON_ORDER_INTERVAL_MS else IDLE_INTERVAL_MS
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMinUpdateDistanceMeters(if (onOrder) 20f else 100f)
            .build()
        fused.removeLocationUpdates(callback)
        fused.requestLocationUpdates(req, callback, mainLooper)
    }

    private fun startForegroundNotification() {
        val ch = NotificationChannel(CHANNEL, getString(R.string.channel_location_name), NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.location_running_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "driver_location"
        const val NOTIF_ID = 4711
        const val IDLE_INTERVAL_MS = 600_000L     // gps_update_normal_sec
        const val ON_ORDER_INTERVAL_MS = 20_000L // gps_update_accepted_book_sec
        const val ACTION_IDLE = "com.newflytaxi.driver.IDLE"
        const val ACTION_ON_ORDER = "com.newflytaxi.driver.ON_ORDER"
        const val ACTION_STOP = "com.newflytaxi.driver.STOP"
        const val EXTRA_BID = "bid"

        fun start(context: Context, bid: String? = null) {
            val svc = Intent(context, DriverLocationService::class.java).apply {
                action = if (bid != null) ACTION_ON_ORDER else ACTION_IDLE
                putExtra(EXTRA_BID, bid)
            }
            context.startForegroundService(svc)
        }

        fun setOrder(context: Context, bid: String?) {
            val svc = Intent(context, DriverLocationService::class.java).apply {
                action = if (bid != null) ACTION_ON_ORDER else ACTION_IDLE
                putExtra(EXTRA_BID, bid)
            }
            context.startService(svc)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DriverLocationService::class.java).apply { action = ACTION_STOP })
        }
    }
}
