package com.kun.livetranslation.data

import com.kun.livetranslation.settings.CaptionSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settingsFlow: Flow<CaptionSettings>
    suspend fun update(transform: (CaptionSettings) -> CaptionSettings)
}
