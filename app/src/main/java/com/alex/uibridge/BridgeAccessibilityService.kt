package com.alex.uibridge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AlexBridge"
        var instance: BridgeAccessibilityService? = null
            private set
    }

    // 缓存最后已知的根节点（解决后台获取问题）
    private var lastKnownRoot: AccessibilityNodeInfo? = null
    private var lastRootTimestamp: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "onServiceConnected - instance set")
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lastKnownRoot = null
        Log.d(TAG, "onDestroy - instance cleared")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        event?.let {
            // 缓存从事件中获取的根节点
            val source = it.source
            if (source != null) {
                // 向上遍历找到真正的根节点
                var root = source
                var parent = root.parent
                while (parent != null) {
                    root = parent
                    parent = root.parent
                }
                lastKnownRoot = root
                lastRootTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Cached root from event, windowId: ${it.windowId}")
            }
        }
    }

    fun getUiTree(): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        
        // 获取根节点（使用多种策略）
        val root = getRootNodeWithFallback()
        
        Log.d(TAG, "getUiTree called, root=${root != null}")
        
        root?.let { 
            traverseNode(it, elements, 0)
            Log.d(TAG, "Traversed ${elements.size} elements")
        } ?: run {
            Log.w(TAG, "No root window available")
        }
        
        return elements
    }

    /**
     * 获取根节点，使用多种 fallback 策略
     */
    private fun getRootNodeWithFallback(): AccessibilityNodeInfo? {
        // 策略1: 直接获取当前窗口
        var root = rootInActiveWindow
        if (root != null) {
            Log.d(TAG, "Got root from rootInActiveWindow")
            return root
        }

        // 策略2: 使用缓存的根节点（如果5秒内）
        val cacheAge = System.currentTimeMillis() - lastRootTimestamp
        if (lastKnownRoot != null && cacheAge < 5000) {
            Log.d(TAG, "Using cached root (age: ${cacheAge}ms)")
            return lastKnownRoot
        }

        // 策略3: 从 windows 列表中获取
        try {
            val windows = windows
            if (windows != null && windows.isNotEmpty()) {
                Log.d(TAG, "Found ${windows.size} windows")
                
                // 优先找 TYPE_APPLICATION 窗口
                for (window in windows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val windowRoot = window.root
                        if (windowRoot != null) {
                            Log.d(TAG, "Got root from TYPE_APPLICATION window: ${window.windowId}")
                            return windowRoot
                        }
                    }
                }
                
                // 如果没找到，取第一个有 root 的窗口
                for (window in windows) {
                    val windowRoot = window.root
                    if (windowRoot != null) {
                        Log.d(TAG, "Got root from window: ${window.windowId}, type: ${window.type}")
                        return windowRoot
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting windows: ${e.message}")
        }

        // 策略4: 使用过期的缓存（总比没有好）
        if (lastKnownRoot != null) {
            Log.w(TAG, "Using expired cached root (age: ${cacheAge}ms)")
            return lastKnownRoot
        }

        Log.e(TAG, "All strategies failed to get root node")
        return null
    }

    fun getUiTreeAsJson(): String {
        val elements = getUiTree()
        return elementsToJson(elements)
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<Map<String, Any?>>,
        depth: Int
    ) {
        if (depth > 50) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val element = mutableMapOf<String, Any?>(
            "text" to (node.text?.toString() ?: ""),
            "desc" to (node.contentDescription?.toString() ?: ""),
            "id" to (node.viewIdResourceName ?: ""),
            "class" to (node.className?.toString() ?: ""),
            "package" to (node.packageName?.toString() ?: ""),
            "clickable" to node.isClickable,
            "focusable" to node.isFocusable,
            "editable" to node.isEditable,
            "scrollable" to node.isScrollable,
            "enabled" to node.isEnabled,
            "x1" to bounds.left,
            "y1" to bounds.top,
            "x2" to bounds.right,
            "y2" to bounds.bottom,
            "cx" to (bounds.left + bounds.right) / 2,
            "cy" to (bounds.top + bounds.bottom) / 2
        )

        elements.add(element)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverseNode(it, elements, depth + 1) }
        }
    }

    private fun elementsToJson(elements: List<Map<String, Any?>>): String {
        val jsonArray = JSONArray()
        elements.forEach { element ->
            jsonArray.put(mapToJson(element))
        }
        return jsonArray.toString()
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val jsonObject = JSONObject()
        map.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> jsonObject.put(key, mapToJson(value as Map<String, Any?>))
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject
    }
}
