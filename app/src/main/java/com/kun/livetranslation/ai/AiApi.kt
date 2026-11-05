package com.kun.livetranslation.ai

import android.util.Base64
import android.util.Log
import com.kun.livetranslation.data.SettingsRepository
import com.kun.livetranslation.speech.WavEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Outcome of "Test connection": each part is null when it was not run. */
data class AiTestReport(val translation: Result<String>?, val speech: Result<String>?, val millis: Long) {
    val ok: Boolean get() = translation?.isSuccess == true && (speech == null || speech.isSuccess)
}

/**
 * One client for every [AiProvider]. Functions that take an [AiConfig] work on a draft
 * (used by "Test connection" before anything is saved); the others use the saved settings.
 * The API key only ever goes into request headers and is never logged.
 */
class AiApi(private val settingsRepository: SettingsRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Saved configuration ──

    suspend fun savedConfig(): AiConfig = settingsRepository.settingsFlow.first().aiConfig

    suspend fun hasKey(): Boolean = savedConfig().apiKey.isNotBlank()

    suspend fun translate(text: String, sourceCode: String?, targetCode: String): String? =
        complete(savedConfig(), translationPrompt(text, sourceCode, targetCode)).getOrNull()

    suspend fun transcribe(wav: ByteArray, languageHint: String?): String? =
        transcribe(savedConfig(), wav, languageHint).getOrNull()

    // ── Any configuration ──

    suspend fun complete(cfg: AiConfig, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (cfg.apiKey.isBlank()) return@withContext Result.failure(IOException("API key is empty"))
        when (cfg.provider) {
            AiProvider.GEMINI -> send(
                Request.Builder().url("${cfg.endpoint}/models/${cfg.model.trim()}:generateContent")
                    .header("x-goog-api-key", cfg.apiKey.trim())
                    .post(json(geminiBody(JSONArray().put(JSONObject().put("text", prompt)))))
                    .build()
            ).mapCatching { parseGeminiText(it) }
            AiProvider.OPENAI_COMPAT -> send(
                Request.Builder().url("${cfg.endpoint}/chat/completions")
                    .header("Authorization", "Bearer ${cfg.apiKey.trim()}")
                    .post(
                        json(
                            JSONObject().put("model", cfg.model.trim()).put("temperature", 0)
                                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                                .toString()
                        )
                    ).build()
            ).mapCatching { parseOpenAiText(it) }
            AiProvider.ANTHROPIC -> send(
                Request.Builder().url("${cfg.endpoint}/messages")
                    .header("x-api-key", cfg.apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .post(
                        json(
                            JSONObject().put("model", cfg.model.trim()).put("max_tokens", 1024).put("temperature", 0)
                                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                                .toString()
                        )
                    ).build()
            ).mapCatching { parseAnthropicText(it) }
        }
    }

    suspend fun transcribe(cfg: AiConfig, wav: ByteArray, languageHint: String?): Result<String> = withContext(Dispatchers.IO) {
        if (cfg.apiKey.isBlank()) return@withContext Result.failure(IOException("API key is empty"))
        when (cfg.provider) {
            AiProvider.GEMINI -> {
                val parts = JSONArray()
                    .put(JSONObject().put("text", transcriptionPrompt(languageHint)))
                    .put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", "audio/wav").put("data", Base64.encodeToString(wav, Base64.NO_WRAP))
                        )
                    )
                send(
                    Request.Builder().url("${cfg.endpoint}/models/${cfg.model.trim()}:generateContent")
                        .header("x-goog-api-key", cfg.apiKey.trim())
                        .post(json(geminiBody(parts))).build()
                ).mapCatching { parseGeminiText(it) }
            }
            AiProvider.OPENAI_COMPAT -> {
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                    .addFormDataPart("model", cfg.sttModel.trim())
                languageHint?.substringBefore('-')?.takeIf { it.length in 2..3 }?.let { form.addFormDataPart("language", it) }
                send(
                    Request.Builder().url("${cfg.endpoint}/audio/transcriptions")
                        .header("Authorization", "Bearer ${cfg.apiKey.trim()}")
                        .post(form.build()).build()
                ).mapCatching { JSONObject(it).optString("text").trim() }
            }
            AiProvider.ANTHROPIC -> Result.failure(IOException("Claude cannot transcribe audio"))
        }
    }

    /** Models the provider says it offers, with whatever details it gives (needs a valid key). */
    suspend fun listModels(cfg: AiConfig): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        if (cfg.apiKey.isBlank()) return@withContext Result.failure(IOException("API key is empty"))
        val req = when (cfg.provider) {
            AiProvider.GEMINI -> Request.Builder().url("${cfg.endpoint}/models?pageSize=200").header("x-goog-api-key", cfg.apiKey.trim())
            AiProvider.OPENAI_COMPAT -> Request.Builder().url("${cfg.endpoint}/models").header("Authorization", "Bearer ${cfg.apiKey.trim()}")
            AiProvider.ANTHROPIC -> Request.Builder().url("${cfg.endpoint}/models?limit=100")
                .header("x-api-key", cfg.apiKey.trim()).header("anthropic-version", "2023-06-01")
        }.get().build()
        send(req).mapCatching { parseModels(cfg.provider, it) }
    }

    /** Runs a real translation (and a speech request if supported) with [cfg], without saving it. */
    suspend fun test(cfg: AiConfig): AiTestReport {
        val start = System.currentTimeMillis()
        val translation = complete(cfg, translationPrompt("Hello, how are you?", "en", "fr")).mapCatching {
            it.ifBlank { throw IOException("The model returned an empty answer") }
        }
        val speech = if (cfg.canTranscribe && translation.isSuccess) {
            // Half a second of silence: checks the endpoint, key and speech model without needing a microphone.
            transcribe(cfg, WavEncoder.encode(ByteArray(16_000)), null)
        } else null
        return AiTestReport(translation, speech, System.currentTimeMillis() - start)
    }

    // ── internals ──

    private fun send(request: Request): Result<String> = try {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.isSuccessful) Result.success(text)
            else {
                Log.w(TAG, "HTTP ${resp.code} from ${request.url.host}")
                Result.failure(IOException("HTTP ${resp.code}: ${errorMessage(text)}"))
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Request to ${request.url.host} failed: ${t.javaClass.simpleName}")
        Result.failure(IOException(t.message ?: t.javaClass.simpleName))
    }

    private fun json(body: String) = body.toRequestBody("application/json".toMediaType())

    private fun geminiBody(parts: JSONArray): String = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        .put("generationConfig", JSONObject().put("temperature", 0))
        .toString()

    internal companion object {
        private const val TAG = "AiApi"

        fun translationPrompt(text: String, sourceCode: String?, targetCode: String): String {
            val from = sourceCode?.let { "from ${languageName(it)} " } ?: ""
            return "Translate the following text ${from}to ${languageName(targetCode)}. " +
                "Reply with the translation only: no quotes, notes or explanations.\n\n$text"
        }

        fun transcriptionPrompt(languageHint: String?): String {
            val hint = languageHint?.let { " The spoken language is ${languageName(it)}." } ?: ""
            return "Transcribe the speech in this audio verbatim.$hint " +
                "Reply with the transcript only. If there is no intelligible speech, reply with nothing."
        }

        private fun languageName(code: String): String =
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

        /** Short, key-free explanation from an API error body. */
        fun errorMessage(body: String): String = try {
            val err = JSONObject(body).opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> ""
            }.ifBlank { body }
        } catch (_: Throwable) { body }.replace(Regex("\\s+"), " ").take(160)

        fun parseGeminiText(body: String): String =
            JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()

        fun parseOpenAiText(body: String): String =
            JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.trim().orEmpty()

        fun parseAnthropicText(body: String): String =
            JSONObject(body).optJSONArray("content")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()

        fun parseModels(provider: AiProvider, body: String): List<AiModelInfo> {
            val root = JSONObject(body)
            val out = mutableListOf<AiModelInfo>()
            if (provider == AiProvider.GEMINI) {
                val arr = root.optJSONArray("models") ?: return out
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    if (!methods.contains("generateContent")) continue
                    out += AiModelInfo(
                        id = m.optString("name").removePrefix("models/"),
                        displayName = m.optString("displayName"),
                        description = m.optString("description"),
                        inputTokens = m.optLong("inputTokenLimit", 0).takeIf { it > 0 },
                        outputTokens = m.optLong("outputTokenLimit", 0).takeIf { it > 0 }
                    )
                }
            } else {
                val arr = root.optJSONArray("data") ?: return out
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id").takeIf { it.isNotBlank() } ?: continue
                    out += AiModelInfo(
                        id = id,
                        displayName = m.optString("display_name"),
                        extra = m.optString("owned_by").takeIf { it.isNotBlank() }?.let { "by $it" } ?: ""
                    )
                }
            }
            return out
        }
    }
}
