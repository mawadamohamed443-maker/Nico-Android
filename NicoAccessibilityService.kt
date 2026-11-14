package com.nico.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NicoAccessibilityService : AccessibilityService() {
    companion object { var inst: NicoAccessibilityService? = null }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun global(action: Int) = performGlobalAction(action)

    private fun find(n: AccessibilityNodeInfo?, ok: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (ok(n)) return n
        for (i in 0 until n.childCount) find(n.getChild(i), ok)?.let { return it }
        return null
    }

    private fun clickNode(n: AccessibilityNodeInfo?): Boolean {
        var c = n
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun clickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(t)) if (clickNode(n)) return true
        return find(root) { (it.contentDescription?.toString() ?: "").contains(t, true) }?.let { clickNode(it) } ?: false
    }

    fun clickFirstSwitch(want: Boolean?): Boolean {
        val root = rootInActiveWindow ?: return false
        val sw = find(root) {
            val cn = it.className?.toString() ?: ""
            cn.contains("Switch") || cn.contains("ToggleButton")
        } ?: return false
        if (want != null && sw.isChecked == want) return true
        return clickNode(sw)
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = find(root) { it.isScrollable } ?: return false
        return n.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun typeText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: find(root) { it.isEditable } ?: return false
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }
}
