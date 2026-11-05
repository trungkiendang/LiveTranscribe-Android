package com.kun.livetranslation.ai

import android.util.Base64
import android.util.Log
import com.kun.livetranslation.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the Google AI Studio Gemini REST API (generateContent).
 * The API key and model come from user settings; the key is sent only in the
 * `x-goog-api-key` header and never logged. Every call returns null on any failure.
 */
class GeminiApi(private val settingsRepository: SettingsRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun hasKey(): Boolean = settingsRepository.settingsFlow.first().geminiApiKey.isNotBlank()

    /** Translates [text]; [sourceCode] null means "detect the language". */
    suspend fun translate(text: String, sourceCode: String?, targetCode: String): String? {
        val from = sourceCode?.let { "from ${languageName(it)} " } ?: ""
        val prompt = "Translate the following text ${from}to ${languageName(targetCode)}. " +
            "Reply with the translation only: no quotes, notes or explanations.\n\n$text"
        return generate(JSONArray().put(JSONObject().put("text", prompt)))
    }

    /** Transcribes a WAV clip; [languageHint] null means "detect the language". */
    suspend fun transcribe(wav: ByteArray, languageHint: String?): String? {
        val hint = languageHint?.let { " The spoken language is ${languageName(it)}." } ?: ""
        val prompt = "Transcribe the speech in this audio verbatim.$hint " +
            "Reply with the transcript only. If there is no intelligible speech, reply with nothing."
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", Base64.encodeToString(wav, Base64.NO_WRAP))
                )
            )
        return generate(parts)
    }

    private suspend fun generate(parts: JSONArray): String? = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.geminiApiKey.isBlank()) return@withContext null
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${settings.geminiModel.trim()}:generateContent")
            .header("x-goog-api-key", settings.geminiApiKey.trim())
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Gemini HTTP ${resp.code}: ${text.take(200)}")
                    return@withContext null
                }
                JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text")?.trim()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gemini request failed: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun languageName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

    private companion object {
        const val TAG = "GeminiApi"
    }
}
