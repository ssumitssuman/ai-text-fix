package com.aitextassistant.app

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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
            put("model", "llama3-8b-8192")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        "You are a helpful writing assistant. Follow instructions strictly. " +
                        "Preserve meaning unless rewriting is requested. " +
                        "Respond in the same language as the input text."
                    )
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.6)
            put("max_tokens", 800)
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
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
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        callback(Result.failure(Exception("API error: ${response.code}")))
                        return
                    }

                    val json = JSONObject(body)
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

        if (action == AIAction.CUSTOM && !customInstruction.isNullOrBlank()) {
            parts.add(customInstruction)
        } else {
            parts.add(action.prompt)
        }

        if (tone != ToneModifier.NONE) {
            parts.add(tone.modifier)
        }

        parts.add("\n\nText:\n$text")
        return parts.joinToString(" ")
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        return prefs.getString("openai_api_key", "") ?: ""
    }
}
