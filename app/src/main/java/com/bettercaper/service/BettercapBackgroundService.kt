package com.bettercaper.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bettercaper.MainActivity
import com.bettercaper.R
import com.bettercaper.ssh.SshTunnelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BettercapBackgroundService : Service() {

    @Inject
    lateinit var tunnelManager: SshTunnelManager

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "bettercaper_channel")
            .setContentTitle("BetterCAPER Background Service")
            .setContentText("Running network commands")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        val action = intent?.action
        if (action != null) {
            serviceScope.launch {
                val cmd = when (action) {
                    "ACTION_WIFI_RECON" -> "wifi.recon on"
                    "ACTION_BLE_RECON" -> "ble.recon on"
                    else -> null
                }
                
                cmd?.let {
                    // TODO: Implement background execution using BettercapApiClient
                    // and ConnectionRepository to get the latest saved credentials.
                }
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "bettercaper_channel",
                "BetterCAPER Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
