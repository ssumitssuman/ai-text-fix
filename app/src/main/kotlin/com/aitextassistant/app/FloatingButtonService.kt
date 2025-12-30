package com.aitextassistant.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.Toast

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: ImageView
    private lateinit var geminiClient: GeminiClient
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        geminiClient = GeminiClient(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createFloatingButton()
    }

    private fun createFloatingButton() {
        floatingButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            alpha = 0.85f
            setOnClickListener {
                handleFixGrammar()
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        layoutParams.x = 30
        layoutParams.y = 0

        windowManager.addView(floatingButton, layoutParams)
    }

    private fun handleFixGrammar() {
        val service = TextAssistantAccessibilityService.getInstance()
        val selectedText = service?.getSelectedText()

        if (selectedText.isNullOrBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            return
        }

        geminiClient.processText(
            text = selectedText,
            action = AIAction.FIX_GRAMMAR,
            tone = ToneModifier.NONE,
            customInstruction = null
        ) { result ->
            handler.post {
                result.onSuccess { fixedText ->
                    service.replaceSelectedText(fixedText)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: "AI failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingButton)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
