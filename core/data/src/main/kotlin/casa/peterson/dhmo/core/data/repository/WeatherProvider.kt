package casa.peterson.dhmo.core.data.repository

import java.time.LocalDate

data class DailyForecast(
    /** The location's local date returned by the provider. */
    val date: LocalDate,
    val maxTempC: Double,
    val maxApparentTempC: Double,
    val meanRelHumidityPercent: Double?,
)

data class GeoPlace(
    val name: String,
    val admin1: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
)

sealed interface WeatherResult<out T> {
    data class Success<T>(val value: T) : WeatherResult<T>

    data class Failure(val reason: WeatherFailure) : WeatherResult<Nothing>
}

enum class WeatherFailure { NETWORK, HTTP, PARSE, NO_DATA }

interface WeatherProvider {
    /** `latE1` / `lonE1` are degrees x 10, already rounded. Nothing else about the user is sent. */
    suspend fun todayForecast(latE1: Int, lonE1: Int): WeatherResult<DailyForecast>

    suspend fun searchCity(query: String, languageTag: String): WeatherResult<List<GeoPlace>>
}
