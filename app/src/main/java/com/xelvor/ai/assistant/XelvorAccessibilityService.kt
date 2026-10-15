package com.xelvor.ai.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in screen assistance. Android requires the user to enable this service
 * manually. Actions are requested only after the user reviews and approves a task.
 */
class XelvorAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        val collected = mutableListOf<String>()
        try { collectText(root, collected); return collected.distinct().take(120).joinToString("\n").take(6000) } finally { root.recycle() }
    }

    private fun collectText(node: AccessibilityNodeInfo, output: MutableList<String>) {
        val value = node.text?.toString()?.trim().orEmpty()
        if (value.isNotBlank()) output.add(value)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, output)
            child.recycle()
        }
    }

    fun clickVisibleText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try { findAndClick(root, target.trim()) } finally { root.recycle() }
    }

    private fun findAndClick(node: AccessibilityNodeInfo, target: String): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.equals(target, ignoreCase = true) || description.equals(target, ignoreCase = true)) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val parent = node.parent
            if (parent != null) {
                val clicked = parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                if (clicked) return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = findAndClick(child, target)
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: XelvorAccessibilityService? = null
            private set
    }
}
