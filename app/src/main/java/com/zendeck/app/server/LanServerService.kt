package com.zendeck.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zendeck.app.MainActivity
import com.zendeck.app.data.repository.LinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class LanServerService : Service() {

    private var server: ZenDeckNanoServer? = null

    override fun onCreate() {
        super.onCreate()
        val ip = getLanIpAddress()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ip))
        server = ZenDeckNanoServer(ZenDeckNanoServer.PORT, LinkRepository.getInstance(this)).also {
            it.start()
            Log.i(TAG, "LAN server started on port ${ZenDeckNanoServer.PORT} · http://$ip:${ZenDeckNanoServer.PORT}")
        }
        _isRunning.value = true
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        _isRunning.value = false
        Log.i(TAG, "LAN server stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LAN Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "ZenDeck local network access" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String?): Notification {
        val address = if (ip != null) "http://$ip:${ZenDeckNanoServer.PORT}" else "Connect to WiFi"
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZenDeck · LAN server active")
            .setContentText("Open $address on your laptop")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "LanServerService"
        const val CHANNEL_ID = "zendeck_lan_server"
        const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun getLanIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.flatMap { iface -> iface.inetAddresses.toList() }
                ?.firstOrNull { addr -> !addr.isLoopbackAddress && addr is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) { null }
    }
}
