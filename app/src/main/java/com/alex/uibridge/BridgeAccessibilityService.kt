package com.alex.uibridge

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BridgeAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {
        // 无障碍服务中断时的处理
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 监听无障碍事件（可选）
    }

    fun getUiTree(): List<Map<String, Any?>> {
        val elements = mutableListOf<Map<String, Any?>>()
        val root = rootInActiveWindow
        root?.let { traverseNode(it, elements, 0) }
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
            "text" to node.text?.toString(),
            "desc" to node.contentDescription?.toString(),
            "id" to node.viewIdResourceName,
            "class" to node.className?.toString(),
            "package" to node.packageName?.toString(),
            "clickable" to node.isClickable,
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            )
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
