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
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class BridgeHttpService : Service() {

    private var server: HttpServer? = null
    private val PORT = 8080
    private val TAG = "AlexBridge"

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
                Log.d(TAG, "HTTP server started on port $PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server: ${e.message}")
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

            Log.d(TAG, "Request: ${session.uri}")

            val response = when (session.uri) {
                "/ping" -> handlePing()
                "/dump" -> handleDump()
                "/debug" -> handleDebug()
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
                """{"status":"ok","service":"Alex UI Bridge"}""")
        }

        private fun handleDump(): Response {
            val service = BridgeAccessibilityService.instance
            
            if (service == null) {
                Log.w(TAG, "Accessibility service instance is null")
                return newFixedLengthResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "application/json",
                    """{"error":"Accessibility service not connected","solution":"请在系统设置中开启无障碍权限"}""")
            }
            
            val root = service.rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "rootInActiveWindow is null")
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                    """{"error":"Cannot access window content","debug":"无障碍服务已连接，但无法获取窗口内容。请检查：1.应用是否在前台 2.是否有悬浮窗权限"}""")
            }
            
            val json = service.getUiTreeAsJson()
            Log.d(TAG, "Returning UI tree with ${service.getUiTree().size} elements")
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
        }

        private fun handleDebug(): Response {
            val service = BridgeAccessibilityService.instance
            val debugInfo = JSONObject().apply {
                put("accessibility_service", if (service != null) "connected" else "null")
                put("root_window", if (service?.rootInActiveWindow != null) "available" else "null")
                put("http_service", if (instance != null) "running" else "null")
            }
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                debugInfo.toString())
        }
    }
}
