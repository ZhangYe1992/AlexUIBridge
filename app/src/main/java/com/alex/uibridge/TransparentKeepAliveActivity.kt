package com.alex.uibridge

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.os.Build

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

    private val handler = Handler(Looper.getMainLooper())
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            // 定期检查，保持Activity活跃
            if (isRunning) {
                Log.d(TAG, "KeepAlive tick")
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TransparentKeepAliveActivity onCreate")

        // 在最近任务中隐藏 - 必须在 setContentView 之前
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setExcludeFromRecents(true)
        }

        // 设置窗口属性 - 关键！
        setupWindow()

        // 设置透明内容
        val view = View(this)
        view.setBackgroundColor(Color.TRANSPARENT)
        setContentView(view)

        // 窗口透明
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 1x1像素大小
        window.setLayout(1, 1)

        // 保持屏幕常亮（防止被系统休眠）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isRunning = true

        // 启动保活循环
        handler.post(keepAliveRunnable)

        Log.d(TAG, "TransparentKeepAliveActivity 初始化完成")
    }

    private fun setupWindow() {
        val params = window.attributes

        // 设置窗口类型 - 使用SYSTEM_ALERT让系统认为我们在前台
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION

        // 不聚焦，但保持显示
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        window.attributes = params
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "TransparentKeepAliveActivity onResume - APP现在被视为前台！")
        isRunning = true
    }

    override fun onPause() {
        super.onPause()
        Log.w(TAG, "TransparentKeepAliveActivity onPause - 可能被系统切换")
        // 不要设置 isRunning = false，尽快恢复
        handler.postDelayed({
            if (!isRunning) {
                Log.d(TAG, "尝试恢复Activity...")
                // 重新 bringToFront
                bringToFront()
            }
        }, 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "TransparentKeepAliveActivity onDestroy - 保活失败！")
        isRunning = false
        handler.removeCallbacks(keepAliveRunnable)
    }

    override fun onBackPressed() {
        // 禁用返回键，防止被意外关闭
        Log.d(TAG, "TransparentKeepAliveActivity onBackPressed - 忽略")
        // 不调用 super，不做任何事
    }

    override fun onStop() {
        super.onStop()
        Log.w(TAG, "TransparentKeepAliveActivity onStop")
        // 尝试保持运行
        if (isRunning) {
            handler.postDelayed({
                if (!isFinishing) {
                    bringToFront()
                }
            }, 100)
        }
    }

    private fun bringToFront() {
        try {
            val intent = Intent(this, TransparentKeepAliveActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            Log.d(TAG, "尝试 bringToFront")
        } catch (e: Exception) {
            Log.e(TAG, "bringToFront 失败: ${e.message}")
        }
    }
}
