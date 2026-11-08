package com.kun.livetranslation.ui.l10n

import java.util.Locale

/**
 * A runtime-localized string table for the whole app UI.
 *
 * Each map key is the canonical English source string; when a key has no
 * translation yet the English text is returned, so the UI degrades gracefully
 * while ML Kit downloads a model / translates in the background — the UI never
 * renders blank or half-localized text.
 */
class UiStrings(private val map: Map<String, String> = emptyMap()) {

    operator fun get(key: String): String = map[key] ?: key

    val isLocalized: Boolean get() = map.isNotEmpty()

    /** Translator for map values with an empty-translation guard. */
    fun translated(key: String): String? = map[key]

    /**
     * Localizes an English format template while preserving its placeholders.
     * Falls back to the English template if the translation is missing or
     * dropped/mangled the %-specifier (which would otherwise crash or corrupt
     * String.format output).
     */
    fun format(template: String, vararg args: Any?): String {
        val localized = map[template] ?: template
        if (localized != template &&
            UiStringCatalog.placeholderCount(localized) != UiStringCatalog.placeholderCount(template)
        ) {
            return typedFormat(template, args) ?: template
        }
        return typedFormat(localized, args) ?: typedFormat(template, args) ?: template
    }

    private fun typedFormat(template: String, args: Array<out Any?>): String? = try {
        String.format(Locale.getDefault(), template, *args)
    } catch (_: Throwable) {
        null
    }

    companion object {
        val EMPTY = UiStrings(emptyMap())
    }
}

/**
 * Single source of truth for every user-facing English string the app can
 * render. These are translated on-device by ML Kit into the chosen UI language
 * and then served through [UiStrings]; English is always the fallback.
 *
 * Keys (and format templates) themselves are the English source text, so the
 * catalog stays in lock-step with the Compose screens without a separate
 * id<->text mapping to keep maintained.
 */
object UiStringCatalog {

    /** Short display name of the app itself, used directly as a catalog key. */
    const val appName = "LiveN"

    /** Every unique English string / template the UI may pass through `t[...]`. */
    val ALL: List<String> = listOf(
        // App / top bar
        appName,
        "Start",
        "Stop",
        "Ready",
        "Status",
        "Idle",
        "Listening",
        "Processing",
        "Paused",
        "Error",
        "History",

        // Permissions
        "Permissions",
        "Microphone",
        "Overlay",
        "Grant",

        // Audio source
        "Audio Source",
        "System Audio",
        "Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen.",
        "Uses the device microphone.",
        "Transcription engine",
        "Remote Whisper",
        "Android built-in",
        "Local Vosk",
        "Manage on-device models",
        "Sends captured audio to the configured Whisper ASR endpoint.",
        "Uses Android's built-in recognizer. Available locales depend on what's installed on this device.",

        // Language pickers
        "Languages",
        "Source (spoken)",
        "No languages available",
        "Pick a language",
        "Target (translation)",
        "Swap",
        "Download more languages",
        "Auto-detect source",
        "Close",
        "Choose language",
        "Search",
        "No languages match \"%s\".",

        // Vosk model management
        "On-device speech models",
        "Vosk models run fully offline on this phone. Downloading a model is a one-time step; uninstall any time to free storage. For the strongest transcription, pick the LARGE server-grade model for the languages you use most.",
        "Installed",
        "Large · server-grade accuracy",
        "Full Vosk server models with the lowest error rates. Each one is 80 MB to 2 GB but runs entirely on-device after the one-time download. This is the strongest transcription option for every language.",
        "Small · fast & light",
        "Compact ~40 MB models for quick installs or low-storage phones. Accuracy is noticeably lower than the large variants.",
        "Models are fetched from alphacephei.com/vosk/models over HTTPS.",
        "Languages without a model here (for example Indonesian) may still work from the microphone with the “Android built-in” engine, if your phone's speech service supports that language. Android may need to download the language first.",
        "Delete",
        "Get",
        "LARGE",
        "bundled",
        "Not now",
        "Opening Google Play…",
        "Download failed (HTTP 404): this model is no longer published",
        "Download failed: no internet connection",
        "Download failed: the connection timed out",
        "There is no on-device speech model for %s here. Try the “Android built-in” engine (uses your phone's speech service, microphone only, if it supports this language) or pick another language.",
        "Downloading %s model…",
        "Download %s model (~%d MB)",
        "Retry %s model download (~%d MB)",

        // Overlay settings
        "Text size: %dsp",
        "Opacity: %d%%",
        "Show original text",
        "Hide when quiet",
        "Accurate on-device (English, Chinese)",
        "Streaming Zipformer models. More accurate than the standard models and still offline.",
        "Live streaming (Gemini, experimental)",
        "Streams audio continuously and shows the transcript and translation as you speak. Lowest delay for video.",
        "Add",
        "Overlay theme",
        "Overlay font",

        // Server / translation engine
        "Translation engine",
        "On-device (ML Kit)",
        "LibreTranslate",
        "Google ML Kit translates fully on this device — no server required. The first time you use a language pair, a ~30 MB model downloads and is then cached offline forever.",
        "LibreTranslate URL",
        "Example: %s",
        "The app fetches /languages to populate the dropdowns.",
        "Save",
        "Refresh",
        "Language fetch error: %s",
        "Runs on this phone and works offline. Speech models installed: %d.",
        "Tip: for better accuracy, tap Manage on-device models and install a LARGE model for the languages you use. Each is 80 MB to 2 GB and works offline after download.",
        "Speech recognition runs on this phone with Vosk, so you can only pick a source language whose speech model is installed.",
        "Translated on this phone with Google ML Kit: %d languages. Each language pair downloads a ~30 MB model once, then works offline.",
        "Translated by your Samsung phone's built-in translator, with Google ML Kit as backup for pairs it lacks. The list shows the %d languages ML Kit supports.",
        "Translated online by your AI provider; your text is sent to it. The list shows the same %d languages as on-device translation.",
        "Could not reach your LibreTranslate server at %s, so a basic language list is shown. Check the address under Translation engine.",
        "Loading languages from your LibreTranslate server…",
        "%d languages from your LibreTranslate server. To add more, install extra Argos Translate packages on the server.",
        "Auto-detect only affects translation. Vosk still listens in the Source language above; choose Cloud AI or Remote Whisper to detect the spoken language too.",
        "Cloud AI detects the spoken language and the translation follows it. Choose the target language above.",
        "Your Whisper server detects the spoken language and the translation follows it. Choose the target language above.",
        "Android's built-in recognizer listens in the Source language above; auto-detect only affects translation.",
        "Speech-to-Text URL (Whisper)",
        "Save STT URL",

        // Update banner
        "Update available",
        "Download APK",
        "View release",
        "Later",
        "You installed this app from the Play Store. The GitHub build is a pre-release and may be unstable. Installing it will also disable Play Store auto-updates for this app.",

        // History
        "Transcript History",
        "No matching transcripts",
        "No transcripts yet",
        "Try a different search term.",
        "Transcripts will appear here as you use captioning.",
        "Search transcripts…",
        "Clear search",
        "Clear",
        "Copy",
        "Back",
        "Delete entry",
        "Delete this transcript entry?\n\n\"%s\"",
        "Cancel",

        // Premium
        "Upgrade",
        "Go Ad-Free",
        "Ad-Free active",
        "Manage subscription",
        "Email used at checkout",
        "Restore purchase",
        "Refresh status",

        "API key",
        "Model",
        "Cloud AI",
        "Sends your speech to your cloud AI provider one phrase at a time. Set it up in Advanced settings.",
        "Translates with your cloud AI provider. Your text is sent to it. Set it up in Advanced settings; without a key, ML Kit is used.",
        "Cloud AI provider",
        "Base URL",
        "Speech model",
        "This provider can translate but not transcribe speech. Use Gemini or another OpenAI-compatible service for speech.",
        "Your key is stored only on this phone. Cloud AI sends your text or speech to the provider you choose.",
        "Testing…",
        "Test connection",
        "Run a successful test to enable Save.",
        "Choose",
        "Recommended",
        "Translation",
        "Speech recognition: OK",
        "Speech recognition",
        "Choose a model",
        "Tap to choose",
        "Search models",
        "Use",
        "Loading…",
        "Checking key and loading models…",
        "Key accepted",
        "models available",
        "Enter an API key to load the provider's full model list.",
        "Settings",
        "Status bar quick toggle",
        "Start and stop captions from the quick settings panel, without opening the app.",
        "About and updates",
        "LiveN checks for a new version each time it opens and about every 12 hours. You can also check now.",
        "You have the latest version.",
        "Could not check",
        "New version available",
        "Checking…",
        "Check for updates",
        "Update now",
        "Downloading",
        "you can leave the app",
        "Confirm the install in the Android dialog or notification.",
        "Update failed",
        "Advanced settings",
        "Add quick toggle to status bar",
        "Samsung translator",
        "Uses the translation service built into your Samsung phone. If the phone does not provide it for a language pair, on-device ML Kit is used automatically. Auto-detect always uses ML Kit.",
        "Hide advanced settings",
        // Feedback / support
        "Support & Feedback",
        "GitHub feedback is not configured.",
        "Report a Problem",
        "Submitted Reports",
        "Open",
        "Closed",
        "Your report will be submitted to this app's public GitHub issue tracker. Do not include passwords, private keys, medical information, financial information, or anything you do not want visible to the repository maintainers.",
        "Title / Subject *",
        "Description *",
        "Include phone/app diagnostics",
        "Diagnostics include app version, device model, Android version, storage/memory info. No personal data is collected.",
        "Name (optional)",
        "Email (optional)",
        "Image attached",
        "Change image",
        "Attach screenshot / image",
        "Configuration error.",
        "Created: %s",
        "Open in browser",
        "Comments (%d)",
        "Post a Reply",
        "Your reply",
        "Attach",
        "Send",
        "Submit",
        "Dismiss",
        "Report submitted successfully!",
        "Remove",

        // Overlay window
        "Pause captioning",
        "Minimize overlay",
        "Close overlay",
        "Resume captioning",

        // Notification / foreground service
        "Captioning in progress",
        "Listening and translating speech.",
        "Pause/Resume",
        "Live Captioning",
        "Shows when live captioning is active.",

        // Update notifier
        "LiveN update available",
        "%s is ready to install",
        "Download",
        "App updates",
        "Notifies you when a new version of LiveN is available on GitHub",

        // Interface language feature
        "Interface language",
        "Choose interface language",
        "English",
        "Machine-translated interface",
        "On-device ML Kit translation may be inaccurate.",
        "UI text is translated on-device by Google ML Kit, not by human translators, and may be inaccurate.",
        "Downloading translation model…",
        "Translating the interface…",
        "Translating the interface… (%d/%d)",
        "Translation failed. Tap to retry.",
        "Switch to English",
        "Switch",
        "Retry",
        "Current",

        // Onboarding
        "Welcome",
        "Live captions & translation on your device",
        "Choose your interface language",
        "You can change this later in Settings.",
        "Get started"
    ).distinct()

    private val PLACEHOLDER = Regex("%\\d*[_a-zA-Z%]")

    fun placeholderCount(text: String): Int = PLACEHOLDER.findAll(text).count()

    /**
     * Whether a string carries enough meaning on its own to be worth
     * translating. Skips blank text, punctuation-only tokens, and stray
     * single characters (e.g. the "sp" unit suffix), which ML Kit handles
     * poorly and which would degrade translation quality.
     */
    fun isSegmentTranslatable(segment: String): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.length < 2) return false
        if (trimmed.all { it.isWhitespace() || !it.isLetter() }) return false
        return true
    }
}