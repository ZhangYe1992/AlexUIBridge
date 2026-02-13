package com.alex.uibridge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AlexBridge"
        var instance: BridgeAccessibilityService? = null
    }

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
        Log.d(TAG, "onDestroy - instance cleared")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 可以在这里记录事件
    }

    fun getUiTree(): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        
        // 尝试多种方式获取窗口
        var root = rootInActiveWindow
        
        // 如果rootInActiveWindow为空，尝试从windows列表获取
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow is null, trying windows list")
            val windows = windows
            if (windows != null && windows.isNotEmpty()) {
                // 找到最顶层的窗口
                for (window in windows) {
                    val windowRoot = window.root
                    if (windowRoot != null) {
                        root = windowRoot
                        Log.d(TAG, "Found window root: ${window.windowId}")
                        break
                    }
                }
            }
        }
        
        Log.d(TAG, "getUiTree called, root=${root != null}")
        
        root?.let { 
            traverseNode(it, elements, 0)
            Log.d(TAG, "Traversed ${elements.size} elements")
        } ?: run {
            Log.w(TAG, "No root window available")
        }
        
        return elements
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
