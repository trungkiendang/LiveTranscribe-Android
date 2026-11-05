package com.kun.livetranslation.translation

import com.kun.livetranslation.ai.GeminiApi

/**
 * Translation through Google AI Studio (Gemini). Without an API key it uses [fallback]
 * (ML Kit). A failed request returns "" so the service keeps the last good caption.
 */
class GeminiTranslationRepository(
    private val api: GeminiApi,
    private val fallback: TranslationRepository
) : TranslationRepository {

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty() || targetCode.isBlank() || (!autoDetect && sourceCode.equals(targetCode, ignoreCase = true))) return clean
        if (!api.hasKey()) return fallback.translate(text, sourceCode, targetCode, autoDetect)
        val source = if (autoDetect || sourceCode.isBlank()) null else sourceCode
        return api.translate(clean, source, targetCode).orEmpty()
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) = fallback.prewarm(sourceCode, targetCode)

    override fun close() = fallback.close()
}
