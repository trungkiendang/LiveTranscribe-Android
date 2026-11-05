package com.kun.livetranslation.di

import android.content.Context
import com.kun.livetranslation.data.SettingsDataStore
import com.kun.livetranslation.data.SettingsRepository
import com.kun.livetranslation.data.TranscriptHistoryStore
import com.kun.livetranslation.data.CaptionProfileRepository
import com.kun.livetranslation.data.FileCaptionProfileRepository
import com.kun.livetranslation.data.FileGlossaryRepository
import com.kun.livetranslation.data.GlossaryRepository
import com.kun.livetranslation.service.CaptionRuntimeStore
import com.kun.livetranslation.speech.LocalVoskSttClient
import com.kun.livetranslation.speech.VoskModelRegistry
import com.kun.livetranslation.translation.LanguageCatalogStore
import com.kun.livetranslation.translation.LibreTranslateRepository
import com.kun.livetranslation.ai.GeminiApi
import com.kun.livetranslation.translation.GeminiTranslationRepository
import com.kun.livetranslation.translation.MlKitTranslationRepository
import com.kun.livetranslation.translation.SystemTranslationRepository
import com.kun.livetranslation.translation.MockTranslationRepository
import com.kun.livetranslation.translation.RoutingTranslationRepository
import com.kun.livetranslation.translation.TranslationRepository
import com.kun.livetranslation.ui.l10n.UiLocalizationRepository
import com.kun.livetranslation.update.UpdateChecker
import com.kun.livetranslation.update.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    /** Long-lived scope for container-owned background work (catalog fetches etc.). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = SettingsDataStore(context.applicationContext)
    val runtimeStore: CaptionRuntimeStore = CaptionRuntimeStore()
    private val libreTranslationRepository: TranslationRepository = LibreTranslateRepository(settingsRepository)
    private val mlKitTranslationRepository: TranslationRepository = MlKitTranslationRepository()
    // Falls back to ML Kit for anything the system service cannot translate.
    private val systemTranslationRepository: TranslationRepository =
        SystemTranslationRepository(context.applicationContext, fallback = mlKitTranslationRepository)
    val geminiApi: GeminiApi = GeminiApi(settingsRepository)
    private val geminiTranslationRepository: TranslationRepository =
        GeminiTranslationRepository(geminiApi, fallback = mlKitTranslationRepository)
    val translationRepository: TranslationRepository = RoutingTranslationRepository(
        settingsRepository = settingsRepository,
        mlKit = mlKitTranslationRepository,
        libre = libreTranslationRepository,
        system = systemTranslationRepository,
        gemini = geminiTranslationRepository
    )
    val mockTranslationRepository: TranslationRepository = MockTranslationRepository()
    val transcriptHistory: TranscriptHistoryStore = TranscriptHistoryStore(context.applicationContext)
    val captionProfiles: CaptionProfileRepository = FileCaptionProfileRepository(context.applicationContext)
    val glossary: GlossaryRepository = FileGlossaryRepository(context.applicationContext)
    val voskRegistry: VoskModelRegistry = VoskModelRegistry(context.applicationContext)
    val localVoskClient: LocalVoskSttClient = LocalVoskSttClient(voskRegistry)
    val languageCatalogStore: LanguageCatalogStore = LanguageCatalogStore(settingsRepository, appScope)
    val updateChecker: UpdateChecker = UpdateChecker()
    // Interface language. Prefers on-device ML Kit; falls back to the
    // configured LibreTranslate server when the model download is gated.
    val uiLocalization: UiLocalizationRepository = UiLocalizationRepository(
        context = context.applicationContext,
        settingsRepository = settingsRepository,
        scope = appScope,
        fallbackTranslator = libreTranslationRepository
    )
    val updateNotifier: UpdateNotifier = UpdateNotifier(
        context.applicationContext,
        uiStringsProvider = { uiLocalization.latestStrings() }
    )
}
