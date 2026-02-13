package com.alex.uibridge

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.util.Log

/**
 * 透明保活 Activity
 * 1x1像素、透明、不可见，但让系统认为APP在前台
 * 这样无障碍服务可以读取其他应用的UI
 */
class TransparentKeepAliveActivity : Activity() {
    
    companion object {
        private const val TAG = "AlexBridge"
        var isRunning = false
            private set
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TransparentKeepAliveActivity onCreate")
        
        // 设置窗口属性
        setupWindow()
        
        // 设置透明内容
        val view = View(this)
        view.setBackgroundColor(Color.TRANSPARENT)
        setContentView(view)
        
        // 窗口透明
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // 1x1像素大小
        window.setLayout(1, 1)
        
        isRunning = true
    }
    
    private fun setupWindow() {
        val params = window.attributes
        
        // 不显示在最近任务
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        
        // 设置类型
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        
        window.attributes = params
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "TransparentKeepAliveActivity onResume - 现在APP被视为前台")
        isRunning = true
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "TransparentKeepAliveActivity onPause")
        // 不要设置 isRunning = false，因为 Activity 可能只是被部分遮挡
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TransparentKeepAliveActivity onDestroy")
        isRunning = false
    }
    
    override fun onBackPressed() {
        // 禁用返回键，防止被意外关闭
        // 只能通过代码 finish()
        Log.d(TAG, "TransparentKeepAliveActivity onBackPressed - 忽略")
    }
}
