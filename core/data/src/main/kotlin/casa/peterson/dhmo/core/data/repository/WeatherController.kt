package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.WeatherDay

/** Implemented by `:core:weather` (Phase 5). */
interface WeatherController {
    suspend fun refreshIfStale()

    suspend fun refreshNow(): WeatherResult<WeatherDay>

    suspend fun onSettingsChanged()
}
