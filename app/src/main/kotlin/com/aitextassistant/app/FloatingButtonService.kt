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

    private var isKeyboardVisible = false

    override fun onCreate() {
        super.onCreate()

        geminiClient = GeminiClient(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createFloatingButton()
        listenKeyboard()
    }

    private fun createFloatingButton() {
        floatingButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            alpha = 0.85f
            visibility = View.GONE

            setOnClickListener {
                handleFixGrammar()
            }

            setOnLongClickListener {
                Toast.makeText(context, "More options coming soon", Toast.LENGTH_SHORT).show()
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        params.x = 30
        params.y = 0

        // Enable dragging
        floatingButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (touchX - event.rawX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(floatingButton, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingButton, params)
    }

    private fun listenKeyboard() {
        val rootView = View(this)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val visible = keypadHeight > screenHeight * 0.15

            if (visible != isKeyboardVisible) {
                isKeyboardVisible = visible
                floatingButton.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
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
                result.onSuccess {
                    service.replaceSelectedText(it)
                }.onFailure { err ->
                    Toast.makeText(this, err.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        windowManager.removeView(floatingButton)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
