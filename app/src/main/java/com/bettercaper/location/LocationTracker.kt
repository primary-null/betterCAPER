package com.bettercaper.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.bettercaper.ssh.SshTunnelManager
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Socket
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tunnelManager: SshTunnelManager
) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                sendLocationToKali(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) return
        isTracking = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun stopTracking() {
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun sendLocationToKali(location: Location) {
        val port = tunnelManager.localGpsPort
        if (port <= 0) return

        scope.launch {
            try {
                val socket = Socket("127.0.0.1", port)
                val out = socket.getOutputStream()
                val data = "${location.latitude},${location.longitude},${location.altitude}\n"
                out.write(data.toByteArray())
                out.flush()
                socket.close()
            } catch (e: Exception) {
                // Ignore errors if tunnel is down or python script isn't running
            }
        }
    }
}
