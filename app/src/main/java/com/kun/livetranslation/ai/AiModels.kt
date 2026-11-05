package com.kun.livetranslation.ai

/** Cloud AI services LiveN can use for translation and speech recognition. */
enum class AiProvider(val label: String) {
    GEMINI("Gemini"),
    /** Any server speaking the OpenAI API: OpenAI, Groq, OpenRouter, Together, Ollama, LM Studio... */
    OPENAI_COMPAT("OpenAI API"),
    ANTHROPIC("Claude");

    companion object {
        fun fromName(name: String?): AiProvider = entries.firstOrNull { it.name == name } ?: GEMINI
    }
}

data class AiConfig(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    /** Empty means the provider's default endpoint. */
    val baseUrl: String = "",
    val model: String = "",
    /** Speech-to-text model; only used by OpenAI-compatible providers. */
    val sttModel: String = ""
) {
    val endpoint: String get() = baseUrl.trim().trimEnd('/').ifBlank { AiCatalog.defaultBase(provider) }
    val canTranscribe: Boolean get() = AiCatalog.supportsAudio(this)
}

data class ModelOption(val id: String, val recommended: Boolean, val note: String = "")

/** A ready-made OpenAI-compatible service. */
data class AiPreset(
    val name: String,
    val baseUrl: String,
    val models: List<ModelOption>,
    val sttModel: String,
    val audio: Boolean
)

/**
 * Static knowledge about providers. The recommended models are suggestions that were
 * sensible when written; "Fetch models" in the UI asks the provider for its live list.
 */
object AiCatalog {
    val presets: List<AiPreset> = listOf(
        AiPreset(
            "OpenAI", "https://api.openai.com/v1",
            listOf(ModelOption("gpt-4o-mini", true, "Fast and cheap"), ModelOption("gpt-4o", false, "Higher quality")),
            sttModel = "whisper-1", audio = true
        ),
        AiPreset(
            "Groq", "https://api.groq.com/openai/v1",
            listOf(ModelOption("llama-3.3-70b-versatile", true, "Good quality, very fast"), ModelOption("llama-3.1-8b-instant", false, "Fastest")),
            sttModel = "whisper-large-v3-turbo", audio = true
        ),
        AiPreset(
            "OpenRouter", "https://openrouter.ai/api/v1",
            listOf(ModelOption("google/gemini-2.5-flash", true, "Fast and cheap"), ModelOption("openai/gpt-4o-mini", false)),
            sttModel = "", audio = false
        ),
        AiPreset("Custom", "", emptyList(), sttModel = "", audio = true)
    )

    fun defaultBase(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1"
        AiProvider.OPENAI_COMPAT -> presets.first().baseUrl
    }

    fun presetFor(config: AiConfig): AiPreset? =
        presets.firstOrNull { it.baseUrl.isNotEmpty() && it.baseUrl == config.baseUrl.trim().trimEnd('/') }

    fun supportsAudio(config: AiConfig): Boolean = when (config.provider) {
        AiProvider.GEMINI -> true
        AiProvider.ANTHROPIC -> false
        AiProvider.OPENAI_COMPAT -> presetFor(config)?.audio ?: true
    }

    fun recommended(config: AiConfig): List<ModelOption> = when (config.provider) {
        AiProvider.GEMINI -> listOf(
            ModelOption("gemini-2.5-flash", true, "Fast, accurate, free tier"),
            ModelOption("gemini-2.5-flash-lite", false, "Cheapest"),
            ModelOption("gemini-2.5-pro", false, "Most accurate, slower")
        )
        AiProvider.ANTHROPIC -> listOf(
            ModelOption("claude-haiku-5-5", true, "Fastest"),
            ModelOption("claude-sonnet-5-5", false, "Higher quality")
        )
        AiProvider.OPENAI_COMPAT -> presetFor(config)?.models.orEmpty()
    }

    /** Live model ids with the recommended ones first (flagged), then the rest alphabetically. */
    fun merge(live: List<String>, recommended: List<ModelOption>): List<ModelOption> {
        val rec = recommended.filter { r -> live.isEmpty() || live.any { it.equals(r.id, true) } }
        val recIds = rec.map { it.id.lowercase() }.toSet()
        return rec + live.distinct().filter { it.lowercase() !in recIds }.sorted().map { ModelOption(it, false) }
    }
}
