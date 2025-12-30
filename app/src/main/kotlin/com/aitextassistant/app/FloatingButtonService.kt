package com.aitextassistant

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat

class FloatingButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var buttonView: ImageView? = null
    private var undoLayout: View? = null
    private var menuLayout: View? = null
    private var loadingView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var undoRunnable: Runnable? = null
    private var originalText: String? = null

    private lateinit var openAIClient: OpenAIClient
    private var currentTone = ToneModifier.NONE
    private var customInstruction: String? = null

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TextAssistantAccessibilityService.ACTION_UPDATE_VISIBILITY) {
                val shouldShow = intent.getBooleanExtra(
                    TextAssistantAccessibilityService.EXTRA_SHOULD_SHOW,
                    false
                )
                if (shouldShow) showButton() else hideButton()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        openAIClient = OpenAIClient(this)

        val filter = IntentFilter(TextAssistantAccessibilityService.ACTION_UPDATE_VISIBILITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(visibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(visibilityReceiver, filter)
        }

        createFloatingButton()
    }

    private fun createFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (16 * resources.displayMetrics.density).toInt()
            y = (400 * resources.displayMetrics.density).toInt()
        }

        floatingView = FrameLayout(this)
        buttonView = ImageView(this).apply {
            val size = (48 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size)
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundResource(android.R.drawable.btn_default)
            alpha = 0.8f
            visibility = View.GONE
            setOnClickListener { handleButtonClick() }
            setOnLongClickListener {
                showActionMenu()
                true
            }
        }

        (floatingView as FrameLayout).addView(buttonView)
        windowManager?.addView(floatingView, params)
    }

    private fun showButton() {
        handler.post {
            buttonView?.visibility = View.VISIBLE
        }
    }

    private fun hideButton() {
        handler.post {
            buttonView?.visibility = View.GONE
            hideMenu()
            hideUndo()
            hideLoading()
        }
    }

    private fun handleButtonClick() {
        val service = TextAssistantAccessibilityService.getInstance()
        val selectedText = service?.getSelectedText()
        if (selectedText.isNullOrEmpty()) {
            Toast.makeText(this, R.string.select_text_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        processAI(selectedText, AIAction.FIX_GRAMMAR)
    }

    private fun showActionMenu() {
        hideMenu()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            elevation = 8f
        }

        AIAction.values().forEach { action ->
            val tv = TextView(this).apply {
                text = action.displayName
                setPadding(32, 24, 32, 24)
                setOnClickListener {
                    hideMenu()
                    processAI(
                        TextAssistantAccessibilityService.getInstance()?.getSelectedText() ?: "",
                        action
                    )
                }
            }
            layout.addView(tv)
        }

        menuLayout = layout

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(menuLayout, params)
    }

    private fun hideMenu() {
        menuLayout?.let {
            windowManager?.removeView(it)
            menuLayout = null
        }
    }

    private fun processAI(text: String, action: AIAction) {
        originalText = text
        showLoading()

        openAIClient.processText(text, action, currentTone, customInstruction) { result ->
            handler.post {
                hideLoading()
                result.onSuccess { showResult(it) }
                    .onFailure {
                        Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun showLoading() {
        hideLoading()
        loadingView = ProgressBar(this)
        windowManager?.addView(
            loadingView,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        )
    }

    private fun hideLoading() {
        loadingView?.let {
            windowManager?.removeView(it)
            loadingView = null
        }
    }

    private fun showResult(text: String) {
        val service = TextAssistantAccessibilityService.getInstance()
        service?.replaceSelectedText(text)
        showUndo()
    }

    private fun showUndo() {
        hideUndo()
        val tv = TextView(this).apply {
            text = getString(R.string.undo)
            setPadding(24, 16, 24, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setOnClickListener {
                originalText?.let {
                    TextAssistantAccessibilityService.getInstance()?.replaceSelectedText(it)
                }
                hideUndo()
            }
        }

        undoLayout = tv
        windowManager?.addView(
            undoLayout,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        )

        undoRunnable = Runnable { hideUndo() }
        handler.postDelayed(undoRunnable!!, 5000)
    }

    private fun hideUndo() {
        undoRunnable?.let { handler.removeCallbacks(it) }
        undoLayout?.let {
            windowManager?.removeView(it)
            undoLayout = null
        }
    }

    override fun onDestroy() {
        unregisterReceiver(visibilityReceiver)
        floatingView?.let { windowManager?.removeView(it) }
        hideMenu()
        hideUndo()
        hideLoading()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
