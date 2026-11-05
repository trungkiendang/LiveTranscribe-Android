package com.kun.livetranslation

import com.kun.livetranslation.ai.AiApi
import com.kun.livetranslation.ai.AiCatalog
import com.kun.livetranslation.ai.AiConfig
import com.kun.livetranslation.ai.AiProvider
import com.kun.livetranslation.ai.ModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCatalogTest {

    @Test
    fun merge_putsRecommendedFirst_thenRestAlphabetical() {
        val rec = listOf(ModelOption("m-fast", true), ModelOption("m-big", false))
        val merged = AiCatalog.merge(listOf("zeta", "m-big", "alpha", "m-fast"), rec)
        assertEquals(listOf("m-fast", "m-big", "alpha", "zeta"), merged.map { it.id })
        assertTrue(merged.first().recommended)
    }

    @Test
    fun merge_dropsRecommendedModelsTheProviderDoesNotOffer() {
        val merged = AiCatalog.merge(listOf("only-this"), listOf(ModelOption("gone", true)))
        assertEquals(listOf("only-this"), merged.map { it.id })
    }

    @Test
    fun merge_withoutLiveList_showsRecommendedOnly() {
        val rec = listOf(ModelOption("a", true), ModelOption("b", false))
        assertEquals(listOf("a", "b"), AiCatalog.merge(emptyList(), rec).map { it.id })
    }

    @Test
    fun audioSupport_perProvider() {
        assertTrue(AiConfig(AiProvider.GEMINI).canTranscribe)
        assertFalse(AiConfig(AiProvider.ANTHROPIC).canTranscribe)
        assertTrue(AiConfig(AiProvider.OPENAI_COMPAT, baseUrl = "https://api.groq.com/openai/v1").canTranscribe)
        assertFalse(AiConfig(AiProvider.OPENAI_COMPAT, baseUrl = "https://openrouter.ai/api/v1/").canTranscribe)
    }

    @Test
    fun endpoint_usesProviderDefaultWhenBlank_andTrimsTrailingSlash() {
        assertEquals("https://api.anthropic.com/v1", AiConfig(AiProvider.ANTHROPIC).endpoint)
        assertEquals("http://localhost:11434/v1", AiConfig(AiProvider.OPENAI_COMPAT, baseUrl = " http://localhost:11434/v1/ ").endpoint)
    }

    @Test
    fun parsers_readEachProvidersResponseShape() {
        assertEquals("Bonjour", AiApi.parseGeminiText("""{"candidates":[{"content":{"parts":[{"text":" Bonjour "}]}}]}"""))
        assertEquals("Salut", AiApi.parseOpenAiText("""{"choices":[{"message":{"content":"Salut"}}]}"""))
        assertEquals("Coucou", AiApi.parseAnthropicText("""{"content":[{"type":"text","text":"Coucou"}]}"""))
        assertEquals("", AiApi.parseGeminiText("{}"))
    }

    @Test
    fun parseModelIds_filtersGeminiToGenerateContentModels() {
        val gemini = """{"models":[
            {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]}]}"""
        assertEquals(listOf("gemini-2.5-flash"), AiApi.parseModelIds(AiProvider.GEMINI, gemini))
        assertEquals(listOf("gpt-4o", "whisper-1"), AiApi.parseModelIds(AiProvider.OPENAI_COMPAT, """{"data":[{"id":"gpt-4o"},{"id":"whisper-1"}]}"""))
    }

    @Test
    fun errorMessage_prefersApiMessage_andNeverExceedsLimit() {
        assertEquals("API key not valid", AiApi.errorMessage("""{"error":{"message":"API key not valid"}}"""))
        assertTrue(AiApi.errorMessage("x".repeat(500)).length <= 160)
    }
}
