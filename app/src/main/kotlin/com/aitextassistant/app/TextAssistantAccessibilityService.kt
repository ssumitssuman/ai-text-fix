package com.aitextassistant.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
        if (node != null && node.isEditable && !isPasswordField(node)) {
            currentEditNode = node
            isFocusedOnEditText = true
        } else {
            node?.recycle()
            isFocusedOnEditText = false
        }
    }

    private fun isPasswordField(node: AccessibilityNodeInfo): Boolean {
        return node.isPassword ||
            node.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
            node.inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0 ||
            node.inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD != 0
    }

    private fun checkKeyboardVisibility() {
        val windows = windows ?: return
        var keyboardFound = false

        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                keyboardFound = true
                break
            }
        }

        isKeyboardVisible = keyboardFound
    }

    private fun checkEditTextFocus() {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable && !isPasswordField(focusedNode)) {
            if (currentEditNode != focusedNode) {
                currentEditNode?.recycle()
                currentEditNode = focusedNode
            }
            isFocusedOnEditText = true
        } else {
            focusedNode?.recycle()
        }

        rootNode.recycle()
    }

    private fun updateButtonVisibility() {
        val shouldShow = isKeyboardVisible && isFocusedOnEditText
        val intent = Intent(ACTION_UPDATE_VISIBILITY)
        intent.putExtra(EXTRA_SHOULD_SHOW, shouldShow)
        sendBroadcast(intent)
    }

    private fun startFloatingButtonService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        startService(intent)
    }

    fun getSelectedText(): String? {
    val node = currentEditNode ?: return null
    val fullText = node.text?.toString() ?: return null

    val start = node.textSelectionStart
    val end = node.textSelectionEnd

    return if (start >= 0 && end > start && end <= fullText.length) {
        // User explicitly selected text
        fullText.substring(start, end)
    } else {
        // No selection → use entire text
        fullText
    }

    fun replaceSelectedText(newText: String): Boolean {
        val node = currentEditNode ?: return false
        val textStart = node.textSelectionStart
        val textEnd = node.textSelectionEnd

        if (textStart < 0 || textEnd < textStart) return false

        val currentText = node.text?.toString() ?: ""
        val updatedText =
            currentText.substring(0, textStart) +
            newText +
            currentText.substring(textEnd)

        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            updatedText
        )

        return node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        currentEditNode?.recycle()
        currentEditNode = null
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_UPDATE_VISIBILITY = "com.aitextassistant.UPDATE_VISIBILITY"
        const val EXTRA_SHOULD_SHOW = "should_show"

        private var instance: TextAssistantAccessibilityService? = null

        fun getInstance(): TextAssistantAccessibilityService? = instance
    }
}
