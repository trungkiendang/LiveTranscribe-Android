package com.kun.livetranslation

import com.kun.livetranslation.ai.AiApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCombinedCallTest {
    private fun geminiReply(text: String) =
        """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(text)}}]}}]}"""

    @Test
    fun parsesOriginalAndTranslation() {
        val (o, t) = AiApi.parseCombined(geminiReply("""{"original":"Hello there","translation":"Xin chào"}"""))
        assertEquals("Hello there", o)
        assertEquals("Xin chào", t)
    }

    @Test
    fun toleratesMarkdownFence() {
        val (o, t) = AiApi.parseCombined(geminiReply("```json\n{\"original\":\"你好\",\"translation\":\"Xin chào\"}\n```"))
        assertEquals("你好", o)
        assertEquals("Xin chào", t)
    }

    @Test
    fun promptNamesTheTargetLanguage() {
        assertTrue(AiApi.combinedPrompt("zh", "vi").contains("Vietnamese"))
    }
}
