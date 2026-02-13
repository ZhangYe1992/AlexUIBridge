package com.alex.uibridge

import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Path
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

            Log.d(TAG, "Request: ${session.method} ${session.uri}")

            val response = when (session.uri) {
                "/ping" -> handlePing()
                "/dump" -> handleDump()
                "/debug" -> handleDebug()
                "/tap" -> handleTap(session)
                "/swipe" -> handleSwipe(session)
                "/back" -> handleBack()
                "/home" -> handleHome()
                "/power" -> handlePower()
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
            
            val json = service.getUiTreeAsJson()
            val elements = service.getUiTree()
            Log.d(TAG, "Returning UI tree with ${elements.size} elements")
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
        }

        private fun handleDebug(): Response {
            val service = BridgeAccessibilityService.instance
            val debugInfo = JSONObject().apply {
                put("accessibility_service", if (service != null) "connected" else "null")
                put("http_service", if (instance != null) "running" else "null")
            }
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                debugInfo.toString())
        }

        /**
         * POST /tap
         * Body: {"x": 500, "y": 1000}
         */
        private fun handleTap(session: IHTTPSession): Response {
            if (session.method != NanoHTTPD.Method.POST) {
                return jsonResponse(405, """{"error":"Method not allowed"}""")
            }

            val params = parseBody(session)
            val x = params.optInt("x", -1)
            val y = params.optInt("y", -1)

            if (x < 0 || y < 0) {
                return jsonResponse(400, """{"error":"Missing x or y parameter"}""")
            }

            val success = performTap(x.toFloat(), y.toFloat())
            return jsonResponse(if (success) 200 else 500, 
                """{"ok":$success,"action":"tap","x":$x,"y":$y}""")
        }

        /**
         * POST /swipe
         * Body: {"x1": 500, "y1": 1500, "x2": 500, "y2": 500, "duration": 300}
         */
        private fun handleSwipe(session: IHTTPSession): Response {
            if (session.method != NanoHTTPD.Method.POST) {
                return jsonResponse(405, """{"error":"Method not allowed"}""")
            }

            val params = parseBody(session)
            val x1 = params.optInt("x1", -1)
            val y1 = params.optInt("y1", -1)
            val x2 = params.optInt("x2", -1)
            val y2 = params.optInt("y2", -1)
            val duration = params.optInt("duration", 300)

            if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) {
                return jsonResponse(400, """{"error":"Missing coordinates"}""")
            }

            val success = performSwipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration)
            return jsonResponse(if (success) 200 else 500,
                """{"ok":$success,"action":"swipe","x1":$x1,"y1":$y1,"x2":$x2,"y2":$y2,"duration":$duration}""")
        }

        private fun handleBack(): Response {
            val service = BridgeAccessibilityService.instance
            val success = service?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
            return jsonResponse(if (success) 200 else 500, """{"ok":$success,"action":"back"}""")
        }

        private fun handleHome(): Response {
            val service = BridgeAccessibilityService.instance
            val success = service?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
            return jsonResponse(if (success) 200 else 500, """{"ok":$success,"action":"home"}""")
        }

        private fun handlePower(): Response {
            val service = BridgeAccessibilityService.instance
            val success = service?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
            return jsonResponse(if (success) 200 else 500, """{"ok":$success,"action":"power"}""")
        }

        private fun performTap(x: Float, y: Float): Boolean {
            val service = BridgeAccessibilityService.instance ?: return false
            
            val path = Path().apply {
                moveTo(x, y)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            return service.dispatchGesture(gesture, null, null)
        }

        private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Int): Boolean {
            val service = BridgeAccessibilityService.instance ?: return false
            
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
                .build()
            
            return service.dispatchGesture(gesture, null, null)
        }

        private fun parseBody(session: IHTTPSession): JSONObject {
            return try {
                val map = HashMap<String, String>()
                session.parseBody(map)
                val body = map["postData"] ?: "{}"
                JSONObject(body)
            } catch (e: Exception) {
                JSONObject()
            }
        }

        private fun jsonResponse(status: Int, body: String): Response {
            val statusCode = when (status) {
                200 -> NanoHTTPD.Response.Status.OK
                400 -> NanoHTTPD.Response.Status.BAD_REQUEST
                405 -> NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED
                else -> NanoHTTPD.Response.Status.INTERNAL_ERROR
            }
            return newFixedLengthResponse(statusCode, "application/json", body)
        }
    }
}
