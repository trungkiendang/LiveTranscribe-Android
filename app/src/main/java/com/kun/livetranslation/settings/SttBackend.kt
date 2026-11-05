package com.kun.livetranslation.settings

enum class SttBackend(val label: String) {
    LOCAL_VOSK("On-device (streaming)"),
    REMOTE_WHISPER("Remote Whisper"),
    GEMINI("Google Gemini");

    companion object {
        fun fromName(name: String): SttBackend =
            entries.firstOrNull { it.name == name } ?: LOCAL_VOSK
    }
}
