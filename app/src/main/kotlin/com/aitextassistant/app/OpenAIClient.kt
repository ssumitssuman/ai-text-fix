package com.aitextassistant

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

        val requestBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "Follow instructions strictly. Do not add new ideas unless asked. " +
                                    "Preserve meaning unless rewriting is requested. " +
                                    "Respond in the same language as input."
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    )
                }
            )
            put("temperature", 0.7)
            put("max_tokens", 1000)
        }.toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        callback(Result.failure(Exception("API error: ${response.code}")))
                        return
                    }

                    val json = JSONObject(responseBody)
                    val content = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    callback(Result.success(content))
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

        // Main instruction
        if (action == AIAction.CUSTOM && !customInstruction.isNullOrBlank()) {
            parts.add(customInstruction)
        } else {
            parts.add(action.prompt)
        }

        // Tone modifier
        if (tone != ToneModifier.NONE) {
            parts.add(tone.modifier)
        }

        // Text payload
        parts.add("\n\nText to transform:\n$text")

        return parts.joinToString(" ")
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        return prefs.getString("openai_api_key", "") ?: ""
    }
}
