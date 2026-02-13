package com.alex.uibridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.util.Log

/**
 * 悬浮窗服务 - 带可拖拽按钮
 * 即使Activity被划掉，悬浮窗仍然保留
 */
class FloatingButtonService : Service() {
    
    private val TAG = "AlexBridge"
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    companion object {
        const val CHANNEL_ID = "floating_button"
        const val NOTIFICATION_ID = 2
        var isRunning = false
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingButtonService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingButton()
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FloatingButtonService started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        removeFloatingButton()
        isRunning = false
        Log.d(TAG, "FloatingButtonService destroyed")
    }
    
    private fun createFloatingButton() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 加载悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
            
            // 窗口参数
            params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                
                // 放在屏幕左侧中间
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = 0
                y = 0
                
                // 不聚焦，但接受触摸
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                
                format = PixelFormat.TRANSLUCENT
            }
            
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮按钮已创建")
            
            // 设置按钮点击和拖拽
            setupFloatingButton()
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮按钮失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun setupFloatingButton() {
        val buttonView = floatingView?.findViewById<View>(R.id.floating_button)
        
        buttonView?.setOnClickListener {
            // 点击打开主界面
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "点击悬浮按钮，打开主界面")
        }
        
        // 拖拽功能
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        
        buttonView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    touchX = event.rawX
                    touchY = event.rawY
                    false // 返回false让点击事件也能触发
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - touchX).toInt()
                    params?.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun removeFloatingButton() {
        try {
            floatingView?.let {
                windowManager?.removeView(it)
                floatingView = null
                Log.d(TAG, "悬浮按钮已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮按钮失败: ${e.message}")
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "悬浮按钮服务"
            val descriptionText = "保持无障碍服务运行"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
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
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Alex UI Bridge")
                .setContentText("悬浮按钮运行中 - 点击管理")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Alex UI Bridge")
                .setContentText("悬浮按钮运行中")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}
