package com.aitextassistant.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent   // ← THIS WAS MISSING
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TextAssistantAccessibilityService : AccessibilityService() {

    private var currentNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Start floating button service
        val intent = Intent(this, FloatingButtonService::class.java)
        startService(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val node = event.source
            if (node != null && node.isEditable && !node.isPassword) {
                currentNode = node
            }
        }
    }

    override fun onInterrupt() {}

    fun getSelectedText(): String? {
        val node = currentNode ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val text = node.text?.toString() ?: return null

        return if (start >= 0 && end > start && end <= text.length) {
            text.substring(start, end)
        } else {
            null
        }
    }

    fun replaceSelectedText(newText: String): Boolean {
        val node = currentNode ?: return false

        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val original = node.text?.toString() ?: return false

        if (start < 0 || end < start) return false

        val updated = original.substring(0, start) +
                newText +
                original.substring(end)

        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            updated
        )

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        private var instance: TextAssistantAccessibilityService? = null

        fun getInstance(): TextAssistantAccessibilityService? = instance
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
