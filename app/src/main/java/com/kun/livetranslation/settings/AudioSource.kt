package com.kun.livetranslation.settings

enum class AudioSource(val label: String) {
    MIC("Microphone"),
    SYSTEM("System Audio");

    companion object {
        fun fromName(name: String): AudioSource =
            entries.firstOrNull { it.name == name } ?: MIC
    }
}
