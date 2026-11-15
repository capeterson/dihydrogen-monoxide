package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/** Thin facade over the settings store. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
