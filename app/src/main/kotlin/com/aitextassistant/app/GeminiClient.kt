package com.aitextassistant.app

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun processText(
        text: String,
        action: AIAction,
        tone: ToneModifier,
        customInstruction: String?,
        callback: (Result<String>) -> Unit
    ) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            callback(Result.failure(Exception("API key not found")))
            return
        }

        val prompt = buildPrompt(text, action, tone, customInstruction)

        // Gemini request body
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1000)
            })
        }.toString()

        // CORRECT FREE-TIER GEMINI ENDPOINT
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey) // API KEY IN HEADER
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()

                    if (!response.isSuccessful || body == null) {
                        callback(Result.failure(Exception("API error ${response.code}: $body")))
                        return
                    }

                    val json = JSONObject(body)
                    val outputText = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    callback(Result.success(outputText))

                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
    }

    private fun buildPrompt(
        text: String,
        action: AIAction,
        tone: ToneModifier,
        customInstruction: String?
    ): String {
        val parts = mutableListOf<String>()

        if (action == AIAction.CUSTOM && !customInstruction.isNullOrBlank()) {
            parts.add(customInstruction)
        } else {
            parts.add(action.prompt)
        }

        if (tone != ToneModifier.NONE) {
            parts.add(tone.modifier)
        }

        parts.add("\n\nText to transform:\n$text")

        return parts.joinToString(" ")
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        return prefs.getString("openai_api_key", "") ?: ""
    }
}
