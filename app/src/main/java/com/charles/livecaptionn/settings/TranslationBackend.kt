package com.charles.livecaptionn.settings

/**
 * Which translator drives the text → text step of the pipeline.
 *
 * - [ML_KIT]: Google ML Kit on-device translation. Fully offline after the
 *   language pair is downloaded (once). ~30 MB per language, ~59 languages.
 * - [LIBRE_TRANSLATE]: HTTP call to a user-configured LibreTranslate server.
 *   Supports whatever languages the server has Argos packages for.
 * - [SYSTEM]: the phone's own translation service (Samsung devices only; falls back to
 *   ML Kit when the service or language pair is missing).
 */
enum class TranslationBackend {
    ML_KIT,
    LIBRE_TRANSLATE,
    SYSTEM;

    companion object {
        fun fromName(name: String?): TranslationBackend =
            entries.firstOrNull { it.name == name } ?: ML_KIT
    }
}
