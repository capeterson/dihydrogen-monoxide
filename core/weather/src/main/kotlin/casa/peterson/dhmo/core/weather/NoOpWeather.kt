package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.model.WeatherDay
import javax.inject.Inject

/** Replaced by `WeatherControllerImpl` in T-WX1. */
internal class NoOpWeatherController
@Inject
constructor() : WeatherController {
    override suspend fun refreshIfStale() = Unit

    override suspend fun refreshNow(): WeatherResult<WeatherDay> = WeatherResult.Failure(WeatherFailure.NO_DATA)

    override suspend fun onSettingsChanged() = Unit
}

/** Replaced by `OpenMeteoProvider` in T-WX1. Makes no network request. */
internal class NoOpWeatherProvider
@Inject
constructor() : WeatherProvider {
    override suspend fun todayForecast(latE1: Int, lonE1: Int): WeatherResult<DailyForecast> = WeatherResult.Failure(WeatherFailure.NO_DATA)

    override suspend fun searchCity(query: String, languageTag: String): WeatherResult<List<GeoPlace>> =
        WeatherResult.Failure(WeatherFailure.NO_DATA)
}
