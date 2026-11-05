package com.kun.livetranslation.ui

import com.kun.livetranslation.service.CaptionRuntimeState
import com.kun.livetranslation.settings.CaptionSettings
import com.kun.livetranslation.settings.Language
import com.kun.livetranslation.settings.TranslationBackend
import com.kun.livetranslation.speech.VoskModelInfo
import com.kun.livetranslation.translation.MlKitLanguages
import com.kun.livetranslation.ui.l10n.UiLocalizationRepository.UiLocalizationStage
import com.kun.livetranslation.compatibility.DeviceSpecs
import com.kun.livetranslation.update.UpdateInfo

data class MainUiState(
    val settings: CaptionSettings = CaptionSettings(),
    val runtime: CaptionRuntimeState = CaptionRuntimeState(),
    val micPermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val libreLanguages: List<Language> = emptyList(),
    val libreLoading: Boolean = false,
    val libreError: String? = null,
    val voskModels: List<VoskModelInfo> = emptyList(),
    val voskDownloadProgress: Map<String, Float> = emptyMap(),
    /** Last failed download per model name, shown in the model sheet. */
    val voskDownloadErrors: Map<String, String> = emptyMap(),
    val availableUpdate: UpdateInfo? = null,
    /** True when the running APK was installed from the Google Play Store. Used
     *  to warn Play Store users before they grab a (potentially unstable)
     *  GitHub release that would break Play auto-update. */
    val installedFromPlayStore: Boolean = false,
    /** Interface language (e.g. "es", "zh") for the app's own menus/text. */
    val uiLanguageCode: String = "en",
    /** True while an ML Kit UI-translation model downloads / strings translate. */
    val uiLocalizationBusy: Boolean = false,
    val uiLocalizationError: String? = null,
    /** Which phase of UI localization is running (download vs. translate). */
    val uiLocalizationStage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
    /** Translation progress while the catalog is being localized. */
    val uiLocalizationTranslated: Int = 0,
    val uiLocalizationTotal: Int = 0,
    /** True once the first-launch onboarding has been completed. */
    val onboardingComplete: Boolean = false,
    /** Hardware inspection results for running on-device AI models. */
    val deviceSpecs: DeviceSpecs? = null,
    /** Dismissible alert when device is unsupported for on-device AI. */
    val showUnsupportedModal: Boolean = false,
    /** Whether the in-app tutorial screen is open. */
    val showTutorial: Boolean = false
) {
    /** Languages the user is allowed to pick as the speech source, given the
     *  current audio source + STT backend combination. */
    val availableSourceLanguages: List<Language>
        get() {
            val installedVosk = voskModels.filter { it.installed }
            return when (settings.sttBackend) {
                // Vosk does the recognition for BOTH mic and system audio, so
                // the picker has to limit to languages with an installed model
                // either way — otherwise the user picks something we can't
                // recognize and StreamingSttEngine errors with "No on-device
                // model installed".
                com.kun.livetranslation.settings.SttBackend.LOCAL_VOSK ->
                    installedVosk
                        .distinctBy { it.languageCode.lowercase() }
                        .map { Language(it.languageCode, it.languageName) }
                // Remote Whisper (system audio) and AndroidSpeechRecognizer
                // (mic) can in principle handle any locale the translation
                // engine supports.
                com.kun.livetranslation.settings.SttBackend.REMOTE_WHISPER,
                com.kun.livetranslation.settings.SttBackend.GEMINI ->
                    translationLanguages()
            }
        }

    /** Target language list is driven by whichever translation engine is active. */
    val availableTargetLanguages: List<Language>
        get() = translationLanguages()

    private fun translationLanguages(): List<Language> = when (settings.translationBackend) {
        TranslationBackend.ML_KIT, TranslationBackend.SYSTEM, TranslationBackend.GEMINI -> MlKitLanguages.LIST
        TranslationBackend.LIBRE_TRANSLATE ->
            if (libreLanguages.isNotEmpty()) libreLanguages else Language.FALLBACK
    }
}
