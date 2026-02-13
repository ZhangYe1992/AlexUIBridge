package com.alex.uibridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD

class BridgeHttpService : Service() {

    private var server: HttpServer? = null
    private val PORT = 8080

    companion object {
        const val CHANNEL_ID = "uibridge_channel"
        const val NOTIFICATION_ID = 1
        var instance: BridgeHttpService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        server = HttpServer(PORT).apply {
            try {
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alex UI Bridge")
            .setContentText("HTTP服务运行中 - ${getDeviceIpAddress()}:$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }

    private inner class HttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            // 处理 CORS 预检请求
            if (session.method == NanoHTTPD.Method.OPTIONS) {
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "").apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    addHeader("Access-Control-Allow-Headers", "Content-Type")
                }
            }

            val response = when (session.uri) {
                "/ping" -> handlePing()
                "/dump" -> handleDump()
                else -> newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "application/json",
                    """{"error":"Not Found"}""")
            }

            // 添加 CORS 头
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Content-Type", "application/json")

            return response
        }

        private fun handlePing(): Response {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                """{"status":"ok"}""")
        }

        private fun handleDump(): Response {
            val service = BridgeAccessibilityService.instance
            return if (service != null) {
                val json = service.getUiTreeAsJson()
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
            } else {
                newFixedLengthResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "application/json",
                    """{"error":"Accessibility service not available"}""")
            }
        }
    }
}
