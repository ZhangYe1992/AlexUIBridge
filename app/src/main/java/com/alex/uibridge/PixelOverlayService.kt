package com.alex.uibridge

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.util.Log

/**
 * 1像素悬浮窗服务
 * 让APK始终处于"前台"状态，无障碍服务可以读取其他应用
 */
class PixelOverlayService : Service() {
    
    private val TAG = "AlexBridge"
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PixelOverlayService created")
        createOverlay()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PixelOverlayService started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        Log.d(TAG, "PixelOverlayService destroyed")
    }
    
    private fun createOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 创建1x1像素的透明View
            overlayView = LinearLayout(this).apply {
                setBackgroundColor(0x00000000) // 完全透明
                layoutParams = LinearLayout.LayoutParams(1, 1)
            }
            
            // 窗口参数
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                
                width = 1
                height = 1
                
                // 放在屏幕角落，不影响触摸
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                
                // 透明、不聚焦、不接受触摸事件
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                
                format = PixelFormat.TRANSLUCENT
            }
            
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "1像素悬浮窗已创建")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun removeOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
                Log.d(TAG, "1像素悬浮窗已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败: ${e.message}")
        }
    }
}
