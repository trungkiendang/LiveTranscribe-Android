package com.kun.livetranslation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.kun.livetranslation.BuildConfig
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.kun.livetranslation.compatibility.CompatibilityTier
import com.kun.livetranslation.compatibility.DeviceSpecs
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kun.livetranslation.settings.AudioSource
import com.kun.livetranslation.settings.Language
import com.kun.livetranslation.settings.SttBackend
import com.kun.livetranslation.settings.TranslationBackend
import com.kun.livetranslation.speech.RecognitionStatus
import com.kun.livetranslation.speech.ModelQuality
import com.kun.livetranslation.speech.VoskModelInfo
import com.kun.livetranslation.translation.MlKitLanguages
import com.kun.livetranslation.translation.SystemTranslationRepository
import com.kun.livetranslation.overlay.OverlayFontCatalog
import com.kun.livetranslation.overlay.OverlayThemeCatalog
import com.kun.livetranslation.ui.l10n.LocalUiStrings
import com.kun.livetranslation.ui.l10n.UiLanguagePickerDialog
import com.kun.livetranslation.ui.l10n.UiLocalizationRepository.UiLocalizationStage
import com.kun.livetranslation.update.UpdateInfo
import com.kun.livetranslation.update.UpdateProgress
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalUiStrings.current
    var showVoskSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }


    // Re-check permission state on every resume so granting overlay/mic
    // access in system Settings is reflected immediately when the user
    // returns to the app — instead of requiring a close + reopen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t["LiveN"], fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = t["Settings"])
                    }
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = t["History"])
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        // On wide screens (tablets, unfolded foldables) keep the content in a centered column.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val updateCtx = LocalContext.current
            ui.availableUpdate?.let { info ->
                UpdateAvailableBanner(
                    info = info,
                    fromPlayStore = ui.installedFromPlayStore,
                    progress = ui.updateProgress,
                    onDownload = { viewModel.startUpdate(updateCtx, info) },
                    onCancel = { viewModel.cancelUpdate(updateCtx) },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }

            val specs = ui.deviceSpecs

            if (specs != null) {
                if (specs.tier == CompatibilityTier.UNSUPPORTED) {
                    UnsupportedDeviceBanner(specs = specs)
                } else if (specs.tier == CompatibilityTier.BORDERLINE && !ui.settings.borderlineWarningDismissed) {
                    BorderlineDeviceBanner(
                        specs = specs,
                        onProceedAnyway = { viewModel.dismissBorderlineWarning() }
                    )
                }
            }

            val modelReady = ui.settings.sttBackend != SttBackend.LOCAL_VOSK ||
                ui.voskModels.any {
                    it.installed && it.languageCode.equals(ui.settings.sourceLanguageCode, ignoreCase = true)
                }
            val permissionReady = ui.overlayPermissionGranted &&
                (ui.settings.audioSource == AudioSource.SYSTEM || ui.micPermissionGranted)
            CaptionControlCard(
                ui,
                onStart = onStart,
                onStop = onStop,
                canStart = modelReady && permissionReady,
                modelMissing = !modelReady,
                onDownloadModel = viewModel::downloadVoskModel,
                onManageModels = { showVoskSheet = true }
            )
            // Only shown while something is missing; all-green checkmarks are noise.
            if (!permissionReady) PermissionsCard(ui, onRequestAudioPermission, onOpenOverlaySettings)
            AudioSourceCard(ui, viewModel, onManageModels = { showVoskSheet = true })
            LanguageCard(
                ui = ui,
                viewModel = viewModel,
                onManageModels = { showVoskSheet = true },
            )
            Spacer(modifier = Modifier.height(16.dp))

            Spacer(Modifier.height(8.dp))
        }
        }

        if (showVoskSheet) {
            VoskModelSheet(
                models = ui.voskModels,
                progress = ui.voskDownloadProgress,
                errors = ui.voskDownloadErrors,
                onDismiss = { showVoskSheet = false },
                onDownload = viewModel::downloadVoskModel,
                onDelete = viewModel::deleteVoskModel,
            )
        }

        if (ui.showUnsupportedModal) {
            UnsupportedDeviceDialog(
                specs = ui.deviceSpecs,
                onDismiss = { viewModel.dismissUnsupportedModal() }
            )
        }

    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

// ── Control Card ──

@Composable
private fun CaptionControlCard(
    ui: MainUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    canStart: Boolean,
    modelMissing: Boolean = false,
    onDownloadModel: (VoskModelInfo) -> Unit = {},
    onManageModels: () -> Unit = {}
) {
    val isRunning = ui.runtime.running
    val t = LocalUiStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusColor = when (ui.runtime.status) {
                RecognitionStatus.LISTENING -> MaterialTheme.colorScheme.primary
                RecognitionStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
                RecognitionStatus.PAUSED -> MaterialTheme.colorScheme.outline
                RecognitionStatus.ERROR -> MaterialTheme.colorScheme.error
                RecognitionStatus.IDLE -> MaterialTheme.colorScheme.outline
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) t["Status"] + ": " + t[ui.runtime.status.displayName]
                    else t["Ready"],
                    style = MaterialTheme.typography.labelLarge
                )
            }
            val err = ui.runtime.lastError?.trim().orEmpty()
            // Also show the reason when it stopped the session; that is when it matters.
            if (err.isNotEmpty() && (isRunning || ui.runtime.status == RecognitionStatus.ERROR)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            if (isRunning && ui.runtime.originalText.isNotBlank()) {
                Text(
                    text = ui.runtime.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (ui.runtime.translatedText.isNotBlank()) {
                    Text(
                        text = ui.runtime.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning && canStart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Start"])
                }
                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Stop"])
                }
            }
            if (!isRunning && !canStart) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t["Grant overlay/audio permissions and install the selected speech model before starting."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!isRunning && modelMissing) {
                // A fresh install ships no speech model, so Start used to sit
                // disabled with nothing to tap. Offer the download right here.
                MissingModelPrompt(ui, onDownloadModel, onManageModels)
            }
        }
    }
}

@Composable
private fun MissingModelPrompt(
    ui: MainUiState,
    onDownloadModel: (VoskModelInfo) -> Unit,
    onManageModels: () -> Unit
) {
    val t = LocalUiStrings.current
    val source = ui.settings.sourceLanguageCode
    val candidate = ui.voskModels
        .filter { !it.installed && it.languageCode.equals(source, ignoreCase = true) }
        .minByOrNull { if (it.quality == ModelQuality.SMALL) 0 else 1 }
    Spacer(Modifier.height(8.dp))
    when {
        candidate == null -> {
            val name = Language.FALLBACK.firstOrNull { it.code.equals(source, ignoreCase = true) }?.name ?: source.uppercase()
            Text(
                text = t.format(
                    "There is no on-device speech model for %s here. Try the “Android built-in” engine (uses your phone's speech service, microphone only, if it supports this language) or pick another language.",
                    name
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        candidate.quality == ModelQuality.LARGE -> {
            TextButton(onClick = onManageModels) { Text(t["Manage on-device models"]) }
        }
        else -> {
            val progress = ui.voskDownloadProgress[candidate.modelName]
            val error = ui.voskDownloadErrors[candidate.modelName]
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = t.format("Downloading %s model…", candidate.languageName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FilledTonalButton(
                    onClick = { onDownloadModel(candidate) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (error != null) t.format("Retry %s model download (~%d MB)", candidate.languageName, candidate.sizeMb)
                        else t.format("Download %s model (~%d MB)", candidate.languageName, candidate.sizeMb)
                    )
                }
                if (error != null) {
                    Text(
                        text = t[error],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ── Permissions ──

@Composable
private fun PermissionsCard(
    ui: MainUiState,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Permissions"])
            PermissionRow(t["Microphone"], ui.micPermissionGranted) { onRequestAudioPermission() }
            PermissionRow(t["Overlay"], ui.overlayPermissionGranted) { onOpenOverlaySettings() }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    val t = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (!granted) {
            FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(8.dp)) { Text(t["Grant"]) }
        }
    }
}

// ── Audio Source ──

@Composable
private fun AudioSourceCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Audio Source"])

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = t["Microphone"],
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.audioSource == AudioSource.MIC,
                    onClick = { viewModel.updateAudioSource(AudioSource.MIC) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["System Audio"],
                    icon = Icons.Filled.SurroundSound,
                    selected = ui.settings.audioSource == AudioSource.SYSTEM,
                    onClick = { viewModel.updateAudioSource(AudioSource.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            val isSystem = ui.settings.audioSource == AudioSource.SYSTEM
            Text(
                text = if (isSystem)
                    t["Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen."]
                else
                    t["Uses the device microphone."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(t["Transcription engine"], style = MaterialTheme.typography.labelMedium)
            val isSttUnsupported = ui.deviceSpecs?.tier == CompatibilityTier.UNSUPPORTED
            val noAiKey = ui.settings.aiApiKey.isBlank()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineOption(
                    title = t["Local Vosk"],
                    subtitle = t["Runs on this device, works offline. Live words as you speak."],
                    icon = if (isSttUnsupported) Icons.Filled.Lock else Icons.Filled.Mic,
                    selected = ui.settings.sttBackend == SttBackend.LOCAL_VOSK,
                    badge = t["Offline"],
                    onClick = { viewModel.updateSttBackend(SttBackend.LOCAL_VOSK) }
                )
                EngineOption(
                    title = t["Cloud AI"],
                    subtitle = t["Sends your speech to your AI provider a few seconds at a time. Most accurate, needs internet."],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.sttBackend == SttBackend.AI,
                    badge = if (noAiKey) t["Needs API key"] else t["Online"],
                    badgeIsWarning = noAiKey,
                    onClick = { viewModel.updateSttBackend(SttBackend.AI) }
                )
                EngineOption(
                    title = if (isSystem) t["Remote Whisper"] else t["Android built-in"],
                    subtitle = if (isSystem) t["Your own Whisper server (set its URL below)."]
                    else t["Google's recognizer built into Android. Microphone only."],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.sttBackend == SttBackend.REMOTE_WHISPER,
                    onClick = { viewModel.updateSttBackend(SttBackend.REMOTE_WHISPER) }
                )
            }
            if (ui.settings.sttBackend == SttBackend.AI && ui.settings.aiProvider == com.kun.livetranslation.ai.AiProvider.GEMINI) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t["Live streaming (Gemini, experimental)"], style = MaterialTheme.typography.bodyMedium)
                        Text(
                            t["Streams audio continuously and shows the transcript and translation as you speak. Lowest delay for video."],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = ui.settings.aiLiveStreaming, onCheckedChange = viewModel::updateAiLiveStreaming)
                }
            }
            if (ui.settings.sttBackend == SttBackend.LOCAL_VOSK) {
                val installed = ui.voskModels.count { it.installed }
                val anyLargeInstalled = ui.voskModels.any { it.installed && it.quality == ModelQuality.LARGE }
                Text(
                    text = t.format("Runs on this phone and works offline. Speech models installed: %d.", installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!anyLargeInstalled) {
                    Text(
                        text = t["Tip: for better accuracy, tap Manage on-device models and install a LARGE model for the languages you use. Each is 80 MB to 2 GB and works offline after download."],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                SherpaSection(ui.settings.sherpaOnDevice, viewModel::updateSherpaOnDevice)
                OutlinedButton(
                    onClick = onManageModels,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Manage on-device models"])
                }
            } else if (ui.settings.sttBackend != SttBackend.AI) {
                Text(
                    text = if (isSystem)
                        t["Sends captured audio to the configured Whisper ASR endpoint."]
                    else
                        t["Uses Android's built-in recognizer. Available locales depend on what's installed on this device."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ChoiceChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, border, RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** One full-width, always fully readable engine choice: radio, title, what it is, and a small status badge. */
@Composable
internal fun EngineOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    badgeIsWarning: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.5f) else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badgeIsWarning) scheme.onErrorContainer else scheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (badgeIsWarning) scheme.errorContainer else scheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

// ── Language Card ──

@Composable
private fun LanguageCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit,
) {
    val sourceOptions = ui.availableSourceLanguages
    val targetOptions = ui.availableTargetLanguages
    val t = LocalUiStrings.current

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Languages"])

            // Source
            Text(t["Source (spoken)"], style = MaterialTheme.typography.labelMedium)
            LanguagePickerField(
                selectedCode = ui.settings.sourceLanguageCode,
                options = sourceOptions,
                placeholder = if (sourceOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateSource(it.code) }
            )

            // Swap + target
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Target (translation)"], style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = viewModel::swapLanguages) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t["Swap"])
                }
            }
            LanguagePickerField(
                selectedCode = ui.settings.targetLanguageCode,
                options = targetOptions,
                placeholder = if (targetOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateTarget(it.code) }
            )

            // Two independent notes: what limits the Source list, and what the Target list comes from.
            if (ui.settings.audioSource == AudioSource.SYSTEM && ui.settings.sttBackend == SttBackend.LOCAL_VOSK) {
                Text(
                    text = t["Speech recognition runs on this phone with Vosk, so you can only pick a source language whose speech model is installed."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onManageModels) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Download more languages"])
                }
            }
            val note = MaterialTheme.typography.bodySmall
            val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
            when (ui.settings.translationBackend) {
                TranslationBackend.ML_KIT -> Text(
                    t.format("Translated on this phone with Google ML Kit: %d languages. Each language pair downloads a ~30 MB model once, then works offline.", MlKitLanguages.LIST.size),
                    style = note, color = noteColor
                )
                TranslationBackend.SYSTEM -> Text(
                    t.format("Translated by your Samsung phone's built-in translator, with Google ML Kit as backup for pairs it lacks. The list shows the %d languages ML Kit supports.", MlKitLanguages.LIST.size),
                    style = note, color = noteColor
                )
                TranslationBackend.AI -> Text(
                    t.format("Translated online by your AI provider; your text is sent to it. The list shows the same %d languages as on-device translation.", MlKitLanguages.LIST.size),
                    style = note, color = noteColor
                )
                TranslationBackend.LIBRE_TRANSLATE -> when {
                    ui.libreLanguages.isEmpty() && ui.libreError != null -> Text(
                        t.format("Could not reach your LibreTranslate server at %s, so a basic language list is shown. Check the address under Translation engine.", ui.settings.serverBaseUrl),
                        style = note, color = MaterialTheme.colorScheme.error
                    )
                    ui.libreLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t["Loading languages from your LibreTranslate server…"], style = note, color = noteColor)
                    }
                    ui.libreLanguages.isNotEmpty() -> Text(
                        t.format("%d languages from your LibreTranslate server. To add more, install extra Argos Translate packages on the server.", ui.libreLanguages.size),
                        style = note, color = noteColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Auto-detect source"], style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ui.settings.autoDetectSource,
                    onCheckedChange = viewModel::updateAutoDetect
                )
            }
            if (ui.settings.autoDetectSource) {
                Text(
                    text = when (ui.settings.sttBackend) {
                        SttBackend.LOCAL_VOSK ->
                            t["Auto-detect only affects translation. Vosk still listens in the Source language above; choose Cloud AI or Remote Whisper to detect the spoken language too."]
                        SttBackend.AI ->
                            t["Cloud AI detects the spoken language and the translation follows it. Choose the target language above."]
                        SttBackend.REMOTE_WHISPER ->
                            if (ui.settings.audioSource == AudioSource.SYSTEM)
                                t["Your Whisper server detects the spoken language and the translation follows it. Choose the target language above."]
                            else
                                t["Android's built-in recognizer listens in the Source language above; auto-detect only affects translation."]
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LanguagePickerField(
    selectedCode: String,
    options: List<Language>,
    placeholder: String,
    onPick: (Language) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }
    val label = selected?.let { "${it.name}  (${it.code})" }
        ?: selectedCode.takeIf { it.isNotBlank() }?.uppercase()
        ?: placeholder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = options.isNotEmpty()) { open = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (options.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }

    if (open) {
        LanguagePickerDialog(
            options = options,
            selectedCode = selectedCode,
            onDismiss = { open = false },
            onPick = {
                onPick(it)
                open = false
            }
        )
    }
}

@Composable
internal fun LanguagePickerDialog(
    options: List<Language>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val t = LocalUiStrings.current
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(t["Close"]) } },
        title = { Text(t["Choose language"]) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(t["Search"]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filtered.forEach { lang ->
                        val isSelected = lang.code.equals(selectedCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(lang) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isSelected -> Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                else -> Spacer(Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lang.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = t.format("No languages match \"%s\".", query),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    )
}

// ── Vosk model management ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoskModelSheet(
    models: List<VoskModelInfo>,
    progress: Map<String, Float>,
    onDismiss: () -> Unit,
    onDownload: (VoskModelInfo) -> Unit,
    onDelete: (VoskModelInfo) -> Unit,
    errors: Map<String, String> = emptyMap()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val t = LocalUiStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t["On-device speech models"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = t["Vosk models run fully offline on this phone. Downloading a model is a one-time step; uninstall any time to free storage. For the strongest transcription, pick the LARGE server-grade model for the languages you use most."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val installed = models.filter { it.installed }
            val availableSmall = models.filter { !it.installed && it.quality == ModelQuality.SMALL }
            val availableLarge = models.filter { !it.installed && it.quality == ModelQuality.LARGE }

            if (installed.isNotEmpty()) {
                Text(t["Installed"], style = MaterialTheme.typography.labelLarge)
                installed.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        error = errors[model.modelName],
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) }
                    )
                }
            }

            if (availableLarge.isNotEmpty()) {
                Text(t["Large · server-grade accuracy"], style = MaterialTheme.typography.labelLarge)
                Text(
                    text = t["Full Vosk server models with the lowest error rates. Each one is 80 MB to 2 GB but runs entirely on-device after the one-time download. This is the strongest transcription option for every language."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                availableLarge.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        error = errors[model.modelName],
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) }
                    )
                }
            }

            if (availableSmall.isNotEmpty()) {
                Text(t["Small · fast & light"], style = MaterialTheme.typography.labelLarge)
                Text(
                    text = t["Compact ~40 MB models for quick installs or low-storage phones. Accuracy is noticeably lower than the large variants."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                availableSmall.forEach { model ->
                    VoskRow(
                        model = model,
                        progress = progress[model.modelName],
                        error = errors[model.modelName],
                        onDownload = { onDownload(model) },
                        onDelete = { onDelete(model) }
                    )
                }
            }

            Text(
                text = t["Models are fetched from alphacephei.com/vosk/models over HTTPS."],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = t["Languages without a model here (for example Indonesian) may still work from the microphone with the “Android built-in” engine, if your phone's speech service supports that language. Android may need to download the language first."],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun VoskRow(
    model: VoskModelInfo,
    progress: Float?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    error: String? = null
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.installed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${model.languageName} (${model.languageCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append(model.modelName)
                            if (model.sizeMb > 0) append(" · ~${formatModelSize(model.sizeMb)}")
                            if (model.quality == ModelQuality.LARGE) append(" · ${t["LARGE"]}")
                            if (model.isBundled) append(" · ${t["bundled"]}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    progress != null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    model.installed && !model.isBundled -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = t["Delete"])
                        }
                    }
                    model.installed && model.isBundled -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = t["Installed"],
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        FilledTonalButton(onClick = onDownload, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (error != null) t["Retry"] else t["Get"])
                        }
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (progress == null && error != null && !model.installed) {
                Text(
                    text = t[error],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatModelSize(sizeMb: Int): String {
    if (sizeMb >= 1024) {
        val gb = sizeMb / 1024f
        return if (gb >= 10f) "${gb.toInt()} GB" else String.format("%.1f GB", gb)
    }
    return "$sizeMb MB"
}

// ── Overlay Settings ──

@Composable
private fun OverlaySettingsCard(
    ui: MainUiState,
    viewModel: MainViewModel,
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(t["Overlay"])

            Text(t.format("Text size: %dsp", ui.settings.textSizeSp.toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.textSizeSp,
                valueRange = 14f..40f,
                onValueChange = viewModel::updateTextSize
            )

            Text(t.format("Opacity: %d%%", (ui.settings.overlayOpacity * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.overlayOpacity,
                valueRange = 0.2f..1f,
                onValueChange = viewModel::updateOpacity
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Show original text"], style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ui.settings.showOriginal,
                    onCheckedChange = viewModel::updateShowOriginal
                )
            }

            Text(t["Overlay theme"], style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayThemeCatalog.THEMES.forEach { theme ->
                    ChoiceChip(
                        label = theme.label,
                        icon = Icons.Filled.Palette,
                        selected = ui.settings.overlayThemeId == theme.id,
                        onClick = { viewModel.updateOverlayTheme(theme.id) }
                    )
                }
            }

            Text(t["Overlay font"], style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayFontCatalog.FONTS.forEach { font ->
                    ChoiceChip(
                        label = font.label,
                        icon = Icons.Filled.TextFields,
                        selected = ui.settings.overlayFontId == font.id,
                        onClick = { viewModel.updateOverlayFont(font.id) }
                    )
                }
            }
        }
    }
}

// ── Server Card ──

@Composable
private fun ServerCard(
    ui: MainUiState,
    translateUrl: String,
    onTranslateUrlChange: (String) -> Unit,
    onSaveTranslateUrl: () -> Unit,
    onRefreshLibre: () -> Unit,
    onSelectTranslationBackend: (TranslationBackend) -> Unit,
    sttUrl: String,
    onSttUrlChange: (String) -> Unit,
    onSaveSttUrl: () -> Unit,
    showStt: Boolean
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel(t["Translation engine"])
            }

            val isTranslateUnsupported = ui.deviceSpecs?.tier == CompatibilityTier.UNSUPPORTED
            val noAiKey = ui.settings.aiApiKey.isBlank()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineOption(
                    title = t["On-device (ML Kit)"],
                    subtitle = t["Google ML Kit on this device. A ~30 MB model per language pair downloads once, then works offline."],
                    icon = if (isTranslateUnsupported) Icons.Filled.Lock else Icons.Filled.Translate,
                    selected = ui.settings.translationBackend == TranslationBackend.ML_KIT,
                    badge = t["Offline"],
                    onClick = { onSelectTranslationBackend(TranslationBackend.ML_KIT) }
                )
                EngineOption(
                    title = t["Cloud AI"],
                    subtitle = t["Your AI provider translates the text, with better context. Add your API key in the AI provider section above; without one, ML Kit is used."],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.translationBackend == TranslationBackend.AI,
                    badge = if (noAiKey) t["Needs API key"] else t["Online"],
                    badgeIsWarning = noAiKey,
                    onClick = { onSelectTranslationBackend(TranslationBackend.AI) }
                )
                // Only offered on Samsung phones, where One UI may provide a system translator.
                if (SystemTranslationRepository.isOfferedOnThisDevice()) {
                    EngineOption(
                        title = t["Samsung translator"],
                        subtitle = t["The translator built into your Samsung phone. Falls back to ML Kit for pairs it lacks, and for auto-detect."],
                        icon = Icons.Filled.Translate,
                        selected = ui.settings.translationBackend == TranslationBackend.SYSTEM,
                        badge = t["Offline"],
                        onClick = { onSelectTranslationBackend(TranslationBackend.SYSTEM) }
                    )
                }
                EngineOption(
                    title = t["LibreTranslate"],
                    subtitle = t["Your own LibreTranslate server (set its URL below)."],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.translationBackend == TranslationBackend.LIBRE_TRANSLATE,
                    onClick = { onSelectTranslationBackend(TranslationBackend.LIBRE_TRANSLATE) }
                )
            }

            if (ui.settings.translationBackend == TranslationBackend.LIBRE_TRANSLATE) {
                OutlinedTextField(
                    value = translateUrl,
                    onValueChange = onTranslateUrlChange,
                    label = { Text(t["LibreTranslate URL"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://<your-pc-ip>:5000") +
                        " The app fetches /languages to populate the dropdowns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSaveTranslateUrl,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(t["Save"]) }
                    OutlinedButton(
                        onClick = onRefreshLibre,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t["Refresh"])
                    }
                }
                if (ui.libreError != null && !ui.libreLoading) {
                    Text(
                        text = t.format("Language fetch error: %s", ui.libreError),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (showStt) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sttUrl,
                    onValueChange = onSttUrlChange,
                    label = { Text(t["Speech-to-Text URL (Whisper)"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://<your-pc-ip>:9000/asr?output=json"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSaveSttUrl,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t["Save STT URL"]) }
            }
        }
    }
}

// ── Update Available Banner ──

@Composable
private fun UpdateAvailableBanner(
    info: UpdateInfo,
    fromPlayStore: Boolean,
    progress: UpdateProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t["Update available"],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = info.releaseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = info.notes.take(240),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 4
                )
            }
            if (fromPlayStore) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t["You installed this app from the Play Store. The GitHub build is a pre-release and may be unstable. Installing it will also disable Play Store auto-updates for this app."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            UpdateProgressRow(progress, onCancel)
            if (progress == null || progress.state == UpdateProgress.State.FAILED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (info.apkDownloadUrl != null) t["Update now"] else t["View release"])
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(t["Later"]) }
                }
            }
        }
    }
}

/** Progress of an in-app update: percent while downloading, then "confirm the install". */
@Composable
private fun UpdateProgressRow(progress: UpdateProgress?, onCancel: () -> Unit) {
    val t = LocalUiStrings.current
    when (progress?.state) {
        UpdateProgress.State.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t["Downloading"]} ${progress.percent}% · ${t["you can leave the app"]}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(t["Cancel"]) }
            }
        }
        UpdateProgress.State.INSTALLING ->
            Text(t["Confirm the install in the Android dialog or notification."], style = MaterialTheme.typography.bodySmall)
        UpdateProgress.State.FAILED ->
            Text("✗ ${progress.message ?: t["Update failed"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        null -> {}
    }
}

// ── Interface Language ──

@Composable
private fun UiLanguageCard(
    languageCode: String,
    busy: Boolean,
    error: String?,
    stage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
    translatedCount: Int = 0,
    translatedTotal: Int = 0,
    onSelect: (String) -> Unit
) {
    val t = LocalUiStrings.current
    var showPicker by remember { mutableStateOf(false) }
    val selected = MlKitLanguages.LIST.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
    val label = if (languageCode.equals("en", true)) {
        "${t["English"]} (en)"
    } else {
        "${selected?.name.orEmpty()} ($languageCode)"
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(t["Interface language"])

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = when (stage) {
                        UiLocalizationStage.TRANSLATING ->
                            if (translatedTotal > 0) {
                                t.format("Translating the interface… (%d/%d)", translatedCount, translatedTotal)
                            } else {
                                t["Translating the interface…"]
                            }
                        UiLocalizationStage.DOWNLOADING -> t["Downloading translation model…"]
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let {
                Text(
                    text = t["Translation failed. Tap to retry."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { onSelect(languageCode) }) { Text(t["Retry"]) }
            }
            if (!busy && error == null && !languageCode.equals("en", true)) {
                Text(
                    text = t["On-device ML Kit translation may be inaccurate."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPicker) {
        UiLanguagePickerDialog(
            currentCode = languageCode,
            busy = busy,
            error = error,
            stage = stage,
            translatedCount = translatedCount,
            translatedTotal = translatedTotal,
            onSelect = {
                showPicker = false
                onSelect(it)
            },
            onDismiss = { showPicker = false }
        )
    }
}

// ── Compatibility & Hardware Requirements ──

@Composable
private fun UnsupportedDeviceBanner(specs: DeviceSpecs) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Unsupported for On-Device AI",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = "This device does not meet the minimum hardware requirements (2.5 GB RAM, 4 CPU cores) for on-device Vosk & ML Kit processing. To prevent crashes, the app has automatically enabled Remote Whisper and LibreTranslate.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF37474F)
            )
            Text(
                text = "Detected: ${specs.totalRamMb} MB RAM • ${specs.cpuCores} cores • ${if (specs.is64Bit) "64-bit" else "32-bit"} • ${specs.freeStorageMb} MB free",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF546E7A)
            )
        }
    }
}

@Composable
private fun BorderlineDeviceBanner(
    specs: DeviceSpecs,
    onProceedAnyway: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Notice: Limited Hardware Resources",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = "Your device (${specs.deviceModel}) meets the minimum requirements, but has limited resources (${specs.totalRamMb} MB RAM, ${specs.cpuCores} cores, ${if (specs.is64Bit) "64-bit" else "32-bit"}). On-device models may be slow or run short of memory here. For best results, use Cloud AI or your own Whisper server for speech recognition.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3E2723)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onProceedAnyway,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Proceed Anyway", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UnsupportedDeviceDialog(
    specs: DeviceSpecs?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Hardware Requirements Not Met", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "On-device neural speech recognition and translation require at least 2.5 GB RAM and 4 CPU cores to run reliably."
                )
                if (specs != null) {
                    Text(
                        "Device specs:\n• RAM: ${specs.totalRamMb} MB (min 2560 MB)\n• CPU: ${specs.cpuCores} cores (min 4)\n• Architecture: ${if (specs.is64Bit) "64-bit" else "32-bit"}\n• Free storage: ${specs.freeStorageMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "On-device models are locked. Remote Whisper STT and LibreTranslate have been selected automatically so you can use live captions seamlessly."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}

@Composable
private fun DeviceCompatibilityCard(
    specs: DeviceSpecs?,
    onSimulateTier: (CompatibilityTier?) -> Unit
) {
    if (specs == null) return
    var showSimMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("System Compatibility Check")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (specs.tier) {
                                CompatibilityTier.PASSED -> Color(0xFF2E7D32)
                                CompatibilityTier.BORDERLINE -> Color(0xFFE65100)
                                CompatibilityTier.UNSUPPORTED -> Color(0xFFC62828)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = specs.tier.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "${specs.deviceModel} • ${specs.androidVersion}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("• RAM: ${specs.totalRamMb} MB (min 2.5 GB, 4 GB+ recommended)", style = MaterialTheme.typography.bodySmall)
                Text("• CPU: ${specs.cpuCores} cores (${if (specs.is64Bit) "64-bit" else "32-bit"})", style = MaterialTheme.typography.bodySmall)
                Text("• Free Storage: ${specs.freeStorageMb} MB", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showSimMenu = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Test Tier...", fontSize = 12.sp)
                }
            }

            if (showSimMenu) {
                AlertDialog(
                    onDismissRequest = { showSimMenu = false },
                    title = { Text("Simulate Compatibility Tier") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Select a tier to test app behavior and UI adaptation on this device:")
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.PASSED); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Passed (>= 4 GB RAM, 64-bit)") }
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.BORDERLINE); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Borderline (3 GB RAM / 32-bit / 4 cores)") }
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.UNSUPPORTED); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Unsupported (< 2.5 GB RAM / < 4 cores)") }
                            TextButton(
                                onClick = { onSimulateTier(null); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset to Real Hardware") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showSimMenu = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}


// ── Settings screen ──

/**
 * All settings in one screen. Narrow screens get one scrolling column; wide screens (tablets,
 * an unfolded Galaxy Z Fold) get two balanced columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onAddQuickTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalUiStrings.current
    val context = LocalContext.current
    val container = (context.applicationContext as com.kun.livetranslation.LiveCaptionApp).container
    var translateUrlDraft by remember(ui.settings.serverBaseUrl) { mutableStateOf(ui.settings.serverBaseUrl) }
    var sttUrlDraft by remember(ui.settings.sttBaseUrl) { mutableStateOf(ui.settings.sttBaseUrl) }

    val statusBarAndUpdates: @Composable () -> Unit = {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(t["Status bar quick toggle"])
                Text(
                    t["Start and stop captions from the quick settings panel, without opening the app."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onAddQuickTile, modifier = Modifier.fillMaxWidth()) {
                    Text(t["Add quick toggle to status bar"])
                }
            }
        }
    }
    val updatesCard: @Composable () -> Unit = { UpdatesCard(ui, viewModel) }
    val aiCard: @Composable () -> Unit = { AiProviderCard(saved = ui.settings.aiConfig, onSave = viewModel::updateAiConfig) }
    val engineCard: @Composable () -> Unit = {
        ServerCard(
            ui = ui,
            translateUrl = translateUrlDraft,
            onTranslateUrlChange = { translateUrlDraft = it },
            onSaveTranslateUrl = { viewModel.updateBaseUrl(translateUrlDraft) },
            onRefreshLibre = { viewModel.refreshLibreCatalog() },
            onSelectTranslationBackend = viewModel::updateTranslationBackend,
            sttUrl = sttUrlDraft,
            onSttUrlChange = { sttUrlDraft = it },
            onSaveSttUrl = { viewModel.updateSttUrl(sttUrlDraft) },
            showStt = ui.settings.audioSource == AudioSource.SYSTEM &&
                ui.settings.sttBackend == SttBackend.REMOTE_WHISPER
        )
    }
    val overlayCard: @Composable () -> Unit = { OverlaySettingsCard(ui = ui, viewModel = viewModel) }
    val toolsCard: @Composable () -> Unit = {
        FeatureToolsCard(
            settings = ui.settings,
            profiles = container.captionProfiles,
            glossary = container.glossary,
            micPermissionGranted = ui.micPermissionGranted,
            overlayPermissionGranted = ui.overlayPermissionGranted,
            voskModels = ui.voskModels,
            onApplyProfile = viewModel::applyProfile,
            onSaveHistoryChange = viewModel::updateSaveHistory,
        )
    }
    val languageCard: @Composable () -> Unit = {
        UiLanguageCard(
            languageCode = ui.uiLanguageCode,
            busy = ui.uiLocalizationBusy,
            error = ui.uiLocalizationError,
            stage = ui.uiLocalizationStage,
            translatedCount = ui.uiLocalizationTranslated,
            translatedTotal = ui.uiLocalizationTotal,
            onSelect = viewModel::updateUiLanguage
        )
    }
    val deviceCard: @Composable () -> Unit = {
        DeviceCompatibilityCard(
            specs = ui.deviceSpecs,
            onSimulateTier = { viewModel.setSimulatedTier(it) }
        )
    }

    val left = listOf(statusBarAndUpdates, updatesCard, aiCard, engineCard)
    val right = listOf(overlayCard, toolsCard, languageCard, deviceCard)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(t["Settings"], fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t["Back"])
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (maxWidth >= 720.dp) {
                Row(
                    Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf(left, right).forEach { column ->
                        Column(
                            Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) { column.forEach { it() } }
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { (left + right).forEach { it() } }
            }
        }
    }
}

/** Version, a manual "Check for updates" button, and how updates normally arrive. */
@Composable
private fun UpdatesCard(ui: MainUiState, viewModel: MainViewModel) {
    val t = LocalUiStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.kun.livetranslation.update.UpdateChecker.Result?>(null) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["About and updates"])
            Text("LiveN v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                t["LiveN checks for a new version each time it opens and about every 12 hours. You can also check now."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val r = result) {
                is com.kun.livetranslation.update.UpdateChecker.Result.UpToDate ->
                    Text("✓ ${t["You have the latest version."]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                is com.kun.livetranslation.update.UpdateChecker.Result.Failed ->
                    Text("✗ ${t["Could not check"]}: ${r.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                is com.kun.livetranslation.update.UpdateChecker.Result.Available ->
                    Text("${t["New version available"]}: ${r.info.releaseName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                null -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            result = viewModel.checkForUpdates()
                            checking = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (checking) t["Checking…"] else t["Check for updates"]) }
                (result as? com.kun.livetranslation.update.UpdateChecker.Result.Available)?.let { r ->
                    if (ui.updateProgress == null || ui.updateProgress.state == UpdateProgress.State.FAILED) {
                        Button(onClick = { viewModel.startUpdate(context, r.info) }, modifier = Modifier.weight(1f)) {
                            Text(t["Update now"])
                        }
                    }
                }
            }
            UpdateProgressRow(ui.updateProgress) { viewModel.cancelUpdate(context) }
        }
    }
}
