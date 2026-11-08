package com.kun.livetranslation.settings

import com.kun.livetranslation.BuildConfig

data class CaptionSettings(
    val sourceLanguageCode: String = "en",
    val targetLanguageCode: String = "vi",
    val autoDetectSource: Boolean = false,
    val textSizeSp: Float = 20f,
    val overlayOpacity: Float = 0.65f,
    val showOriginal: Boolean = true,
    /** Fade the caption box away after a few quiet seconds; it returns with the next speech. */
    val autoHideOverlay: Boolean = true,
    /** Gemini Live: audio streams over one WebSocket and captions arrive word by word. Experimental. */
    val aiLiveStreaming: Boolean = false,
    val sherpaOnDevice: Boolean = true,
    val serverBaseUrl: String = DEFAULT_BASE_URL,
    val overlayX: Int = 60,
    val overlayY: Int = 220,
    val overlayMinimized: Boolean = false,
    val audioSource: AudioSource = AudioSource.MIC,
    val sttBackend: SttBackend = SttBackend.LOCAL_VOSK,
    val translationBackend: TranslationBackend = TranslationBackend.ML_KIT,
    /** Cloud AI provider settings, used by the "Cloud AI" translation and speech engines. */
    val aiProvider: com.kun.livetranslation.ai.AiProvider = com.kun.livetranslation.ai.AiProvider.GEMINI,
    val aiApiKey: String = "",
    val aiBaseUrl: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    val aiSttModel: String = "",
    val sttBaseUrl: String = DEFAULT_STT_URL,
    val overlayWidthDp: Int = DEFAULT_OVERLAY_WIDTH_DP,
    val overlayHeightDp: Int = DEFAULT_OVERLAY_HEIGHT_DP,
    /** Overlay look. Defaults render identically to the app's original look. */
    val overlayThemeId: String = "default",
    val overlayFontId: String = "default",
    /** Interface (UI) language, defaulting to English until the user opts in
     *  to an on-device ML Kit translation. Not the caption source language. */
    val uiLanguageCode: String = "en",
    /** True once the first-launch onboarding has been completed. */
    val onboardingComplete: Boolean = false,
    /** Whether completed captions should be persisted to local history. */
    val saveHistory: Boolean = true,
    /** Whether the user acknowledged/dismissed the borderline hardware warning. */
    val borderlineWarningDismissed: Boolean = false,
    /** Optional simulated compatibility tier for manual testing (e.g. PASSED, BORDERLINE, UNSUPPORTED). */
    val simulatedCompatibilityTier: String? = null
) {
    val aiConfig: com.kun.livetranslation.ai.AiConfig
        get() = com.kun.livetranslation.ai.AiConfig(aiProvider, aiApiKey, aiBaseUrl, aiModel, aiSttModel)

    companion object {
        val DEFAULT_BASE_URL: String = BuildConfig.DEFAULT_TRANSLATE_URL
        val DEFAULT_STT_URL: String = BuildConfig.DEFAULT_STT_URL
        const val DEFAULT_AI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_OVERLAY_WIDTH_DP = 320
        const val DEFAULT_OVERLAY_HEIGHT_DP = 180
        const val MIN_OVERLAY_WIDTH_DP = 180
        const val MIN_OVERLAY_HEIGHT_DP = 80
    }
}
