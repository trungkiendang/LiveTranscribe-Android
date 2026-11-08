package com.kun.livetranslation.speech

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    /** Set when the engine already translated the text (one-call and live cloud modes). */
    val translation: String? = null
)
