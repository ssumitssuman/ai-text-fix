package com.aitextassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the correct UI
        setContentView(R.layout.activity_main)

        // Bind views
        statusText = findViewById(R.id.statusText)
        apiKeyInput = findViewById(R.id.apiKeyInput)

        val saveApiKeyBtn = findViewById<Button>(R.id.saveApiKeyBtn)
        val enableAccessibilityBtn = findViewById<Button>(R.id.enableAccessibilityBtn)
        val enableOverlayBtn = findViewById<Button>(R.id.enableOverlayBtn)

        // Load existing API key if saved
        loadApiKey()
        updateStatus()

        // Save API key button
        saveApiKeyBtn.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                saveApiKey(apiKey)
                Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
                updateStatus()
            } else {
                Toast.makeText(this, "Please enter a valid API key", Toast.LENGTH_SHORT).show()
            }
        }

        // Open Accessibility settings
        enableAccessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Open Draw Over Apps permission
        enableOverlayBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ============================
    // Helpers
    // ============================

    private fun loadApiKey() {
        val prefs = getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("openai_api_key", "")
        if (!apiKey.isNullOrEmpty()) {
            apiKeyInput.setText(apiKey)
        }
    }

    private fun saveApiKey(apiKey: String) {
        val prefs = getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("openai_api_key", apiKey)
            .apply()
    }

    private fun updateStatus() {
        val prefs = getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        val hasApiKey = !prefs.getString("openai_api_key", "").isNullOrEmpty()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        val status = buildString {
            append("Status\n\n")
            append("API Key: ${if (hasApiKey) "Saved" else "Not set"}\n")
            append("Accessibility: ${if (accessibilityEnabled) "Enabled" else "Disabled"}\n")
            append("Overlay: ${if (overlayEnabled) "Enabled" else "Disabled"}\n")
        }

        statusText.text = status
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
    return try {
        val enabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        enabled == 1
    } catch (e: Exception) {
        false
    }
    }
