package com.kun.livetranslation.ui

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kun.livetranslation.BuildConfig
import com.kun.livetranslation.compatibility.CompatibilityTier
import com.kun.livetranslation.compatibility.DeviceCompatibilityChecker
import com.kun.livetranslation.compatibility.DeviceSpecs
import com.kun.livetranslation.di.AppContainer
import com.kun.livetranslation.settings.AudioSource
import com.kun.livetranslation.settings.SttBackend
import com.kun.livetranslation.settings.TranslationBackend
import com.kun.livetranslation.speech.VoskModelInfo
import com.kun.livetranslation.data.CaptionProfile
import com.kun.livetranslation.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val container: AppContainer,
    application: Application
) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow(
        MainUiState(installedFromPlayStore = detectPlayStoreInstall())
    )
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collectLatest { settings ->
                val simulatedTier = settings.simulatedCompatibilityTier?.let { name ->
                    try { CompatibilityTier.valueOf(name) } catch (_: Throwable) { null }
                }
                val specs = DeviceCompatibilityChecker.inspect(getApplication(), simulatedTier)
                var shouldShowUnsupported = mutableState.value.showUnsupportedModal

                if (specs.tier == CompatibilityTier.UNSUPPORTED) {
                    if (settings.sttBackend == SttBackend.LOCAL_VOSK || settings.translationBackend == TranslationBackend.ML_KIT) {
                        shouldShowUnsupported = true
                        viewModelScope.launch {
                            container.settingsRepository.update { curr ->
                                curr.copy(
                                    sttBackend = SttBackend.REMOTE_WHISPER,
                                    translationBackend = TranslationBackend.LIBRE_TRANSLATE
                                )
                            }
                        }
                    }
                }

                mutableState.value = mutableState.value.copy(
                    settings = settings,
                    uiLanguageCode = settings.uiLanguageCode,
                    onboardingComplete = settings.onboardingComplete,
                    micPermissionGranted = hasMicPermission(),
                    overlayPermissionGranted = hasOverlayPermission(),
                    deviceSpecs = specs,
                    showUnsupportedModal = shouldShowUnsupported
                )
            }
        }
        viewModelScope.launch {
            container.uiLocalization.state.collectLatest { l10n ->
                mutableState.value = mutableState.value.copy(
                    uiLanguageCode = l10n.languageCode,
                    uiLocalizationBusy = l10n.modelBusy,
                    uiLocalizationError = l10n.error,
                    uiLocalizationStage = l10n.stage,
                    uiLocalizationTranslated = l10n.translatedCount,
                    uiLocalizationTotal = l10n.translatedTotal
                )
            }
        }
        viewModelScope.launch { container.uiLocalization.restore() }
        viewModelScope.launch {
            container.runtimeStore.state.collectLatest { runtime ->
                mutableState.value = mutableState.value.copy(
                    runtime = runtime,
                    micPermissionGranted = hasMicPermission(),
                    overlayPermissionGranted = hasOverlayPermission()
                )
            }
        }
        viewModelScope.launch {
            container.languageCatalogStore.state.collectLatest { catalog ->
                mutableState.value = mutableState.value.copy(
                    libreLanguages = catalog.languages,
                    libreLoading = catalog.loading,
                    libreError = catalog.error
                )
            }
        }
        viewModelScope.launch {
            container.voskRegistry.models.collectLatest { models ->
                mutableState.value = mutableState.value.copy(voskModels = models)
            }
        }
        viewModelScope.launch {
            container.voskRegistry.downloadProgress.collectLatest { progress ->
                mutableState.value = mutableState.value.copy(voskDownloadProgress = progress)
            }
        }
        viewModelScope.launch {
            container.voskRegistry.downloadErrors.collectLatest { errors ->
                mutableState.value = mutableState.value.copy(voskDownloadErrors = errors)
            }
        }
        if (BuildConfig.GITHUB_SELF_UPDATE_ENABLED) {
            viewModelScope.launch {
                container.updateChecker.available.collectLatest { info ->
                    mutableState.value = mutableState.value.copy(availableUpdate = info)
                    if (info != null) container.updateNotifier.notifyIfNew(info)
                }
            }
            viewModelScope.launch {
                com.kun.livetranslation.update.UpdateInstaller.progress(getApplication()).collectLatest { p ->
                    mutableState.value = mutableState.value.copy(updateProgress = p)
                }
            }
            // One-shot check on launch so users see an update even before the periodic worker runs.
            viewModelScope.launch { container.updateChecker.check() }
        }
    }

    fun dismissUpdate() {
        container.updateChecker.dismiss()
    }

    /** Manual "Check for updates" from Settings. */
    suspend fun checkForUpdates(): com.kun.livetranslation.update.UpdateChecker.Result = container.updateChecker.checkNow()

    /**
     * Downloads the update in the background and installs it. The first time, Android needs
     * "Install unknown apps" allowed for LiveN, so we send the user to that setting instead.
     */
    fun startUpdate(context: Context, info: UpdateInfo) {
        if (info.apkDownloadUrl == null) return openUpdateUrl(context, info)
        if (!com.kun.livetranslation.update.UpdateInstaller.canInstall(context)) {
            android.widget.Toast.makeText(
                context, "Allow LiveN to install apps, then tap Update again.", android.widget.Toast.LENGTH_LONG
            ).show()
            com.kun.livetranslation.update.UpdateInstaller.openInstallPermissionSettings(context)
            return
        }
        com.kun.livetranslation.update.UpdateInstaller.start(context, info)
    }

    fun cancelUpdate(context: Context) = com.kun.livetranslation.update.UpdateInstaller.cancel(context)

    fun openUpdateUrl(context: Context, info: UpdateInfo) {
        val target = info.apkDownloadUrl ?: info.releasePageUrl
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun refreshPermissionState() {
        mutableState.value = mutableState.value.copy(
            micPermissionGranted = hasMicPermission(),
            overlayPermissionGranted = hasOverlayPermission()
        )
    }

    fun updateSource(code: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sourceLanguageCode = code) } }
    }

    fun updateTarget(code: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(targetLanguageCode = code) } }
    }

    fun swapLanguages() {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    sourceLanguageCode = it.targetLanguageCode,
                    targetLanguageCode = it.sourceLanguageCode
                )
            }
        }
    }

    fun updateAutoDetect(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(autoDetectSource = enabled) } }
    }

    fun updateTextSize(size: Float) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(textSizeSp = size) } }
    }

    fun updateOpacity(opacity: Float) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(overlayOpacity = opacity) } }
    }

    fun updateShowOriginal(show: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(showOriginal = show) } }
    }

    fun updateSherpaOnDevice(on: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sherpaOnDevice = on) } }
    }

    fun updateAiLiveStreaming(on: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(aiLiveStreaming = on) } }
    }

    fun updateSaveHistory(save: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(saveHistory = save) } }
    }

    fun applyProfile(profile: CaptionProfile) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    sourceLanguageCode = profile.sourceLanguage,
                    targetLanguageCode = profile.targetLanguage,
                    textSizeSp = profile.textSizeSp,
                    showOriginal = profile.showOriginal
                )
            }
        }
    }

    fun updateOverlayTheme(themeId: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(overlayThemeId = themeId) } }
    }

    fun updateOverlayFont(fontId: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(overlayFontId = fontId) } }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(serverBaseUrl = url) }
            container.languageCatalogStore.refresh()
        }
    }

    fun refreshLibreCatalog() {
        container.languageCatalogStore.refresh()
    }

    fun updateAudioSource(source: AudioSource) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(audioSource = source) } }
    }

    fun updateSttBackend(backend: SttBackend) {
        val specs = mutableState.value.deviceSpecs
        if (specs?.tier == CompatibilityTier.UNSUPPORTED && backend == SttBackend.LOCAL_VOSK) {
            mutableState.value = mutableState.value.copy(showUnsupportedModal = true)
            return
        }
        viewModelScope.launch { container.settingsRepository.update { it.copy(sttBackend = backend) } }
    }

    fun updateAiConfig(config: com.kun.livetranslation.ai.AiConfig) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    aiProvider = config.provider, aiApiKey = config.apiKey.trim(), aiBaseUrl = config.baseUrl.trim(),
                    aiModel = config.model.trim(), aiSttModel = config.sttModel.trim()
                )
            }
        }
    }

    fun updateTranslationBackend(backend: TranslationBackend) {
        val specs = mutableState.value.deviceSpecs
        if (specs?.tier == CompatibilityTier.UNSUPPORTED && backend == TranslationBackend.ML_KIT) {
            mutableState.value = mutableState.value.copy(showUnsupportedModal = true)
            return
        }
        viewModelScope.launch { container.settingsRepository.update { it.copy(translationBackend = backend) } }
    }

    fun dismissUnsupportedModal() {
        mutableState.value = mutableState.value.copy(showUnsupportedModal = false)
    }

    fun dismissBorderlineWarning() {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(borderlineWarningDismissed = true) }
        }
    }

    fun resetBorderlineWarning() {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(borderlineWarningDismissed = false) }
        }
    }

    fun setSimulatedTier(tier: CompatibilityTier?) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(
                    simulatedCompatibilityTier = tier?.name,
                    borderlineWarningDismissed = false
                )
            }
        }
    }

    fun updateSttUrl(url: String) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(sttBaseUrl = url) } }
    }

    /** Switches the on-device UI language (downloads ML Kit model + translates). */
    fun updateUiLanguage(code: String) {
        container.uiLocalization.select(code)
    }

    /** Completes first-launch onboarding, choosing the interface language. */
    fun completeOnboarding(languageCode: String) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(onboardingComplete = true) }
            container.uiLocalization.select(languageCode)
        }
    }

    fun downloadVoskModel(model: VoskModelInfo) {
        viewModelScope.launch { container.voskRegistry.downloadAndInstall(model) }
    }

    fun deleteVoskModel(model: VoskModelInfo) {
        if (model.isBundled) return
        viewModelScope.launch { container.voskRegistry.uninstall(model.modelName) }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        // Some OEM builds / Android Go devices ship no activity for the per-app overlay
        // screen (Crashlytics #154). Fall back to the generic overlay list, then to the
        // app's details page, instead of crashing.
        val fallbacks = listOf(
            intent,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
        for (candidate in fallbacks) {
            try {
                context.startActivity(candidate)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            } catch (e: RuntimeException) {
                // Some OEM builds throw SecurityException / IllegalArgumentException
                // for restricted settings screens instead; a missing settings
                // page must never crash the app.
                Log.w("MainViewModel", "Overlay settings intent rejected: $candidate", e)
            }
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(getApplication())

    /**
     * Detects whether the current install came from the Play Store. Used to warn
     * those users before they switch to a GitHub release (which would break Play
     * auto-update and may run pre-release code). Sideloaded users see the normal
     * update banner with no warning.
     */
    private fun detectPlayStoreInstall(): Boolean {
        val context: Context = getApplication()
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
            installer == PLAY_STORE_INSTALLER
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val PLAY_STORE_INSTALLER = "com.android.vending"
    }
}
