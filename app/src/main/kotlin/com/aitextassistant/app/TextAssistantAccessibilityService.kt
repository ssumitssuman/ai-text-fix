package com.aitextassistant.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class TextAssistantAccessibilityService : AccessibilityService() {

    private var isKeyboardVisible = false
    private var isFocusedOnEditText = false
    private var currentEditNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        startFloatingButtonService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocused(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkKeyboardVisibility()
                checkEditTextFocus()
            }
        }

        updateButtonVisibility()
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        currentEditNode?.recycle()
        currentEditNode = null

        val node = event.source
        if (node != null && node.isEditable && !node.isPassword) {
            currentEditNode = node
            isFocusedOnEditText = true
        } else {
            node?.recycle()
            isFocusedOnEditText = false
        }
    }

    private fun checkKeyboardVisibility() {
        val winList = windows ?: return
        isKeyboardVisible = winList.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    }

    private fun checkEditTextFocus() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focused != null && focused.isEditable && !focused.isPassword) {
            if (currentEditNode != focused) {
                currentEditNode?.recycle()
                currentEditNode = focused
            }
            isFocusedOnEditText = true
        } else {
            focused?.recycle()
            isFocusedOnEditText = false
        }

        root.recycle()
    }

    private fun updateButtonVisibility() {
        val shouldShow = isKeyboardVisible && isFocusedOnEditText
        val intent = Intent(ACTION_UPDATE_VISIBILITY)
        intent.putExtra(EXTRA_SHOULD_SHOW, shouldShow)
        sendBroadcast(intent)
    }

    private fun startFloatingButtonService() {
        startService(Intent(this, FloatingButtonService::class.java))
    }

    /**
     * If text is selected → return selection
     * If nothing is selected → return full text
     */
    fun getSelectedText(): String? {
        val node = currentEditNode ?: return null
        val fullText = node.text?.toString() ?: return null

        val start = node.textSelectionStart
        val end = node.textSelectionEnd

        return if (start >= 0 && end > start && end <= fullText.length) {
            fullText.substring(start, end)
        } else {
            fullText
        }
    }

    fun replaceSelectedText(newText: String): Boolean {
        val node = currentEditNode ?: return false
        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            newText
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        currentEditNode?.recycle()
        currentEditNode = null
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_UPDATE_VISIBILITY =
            "com.aitextassistant.app.UPDATE_VISIBILITY"
        const val EXTRA_SHOULD_SHOW = "should_show"

        private var instance: TextAssistantAccessibilityService? = null

        fun getInstance(): TextAssistantAccessibilityService? = instance
    }
}
