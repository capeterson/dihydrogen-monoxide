package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import casa.peterson.dhmo.core.data.repository.WeatherResult
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Where the two Open-Meteo APIs live. Tests point both at a `MockWebServer`. */
internal data class WeatherEndpoints(
    val forecast: HttpUrl = "https://api.open-meteo.com/v1/forecast".toHttpUrl(),
    val geocoding: HttpUrl = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl(),
)

@Serializable
internal data class ForecastResponse(val daily: Daily? = null)

@Serializable
internal data class Daily(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentTemperatureMax: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m_mean") val relativeHumidityMean: List<Double?> = emptyList(),
)

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
internal data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

/**
 * Open-Meteo client (TECHNICAL_PLAN §12). The only thing a forecast request carries is a coordinate rounded to
 * 0.1 degrees; there is no key, account or identifier.
 */
internal class OpenMeteoProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoints: WeatherEndpoints,
) : WeatherProvider {
    override suspend fun todayForecast(latE1: Int, lonE1: Int): WeatherResult<DailyForecast> {
        val url = endpoints.forecast.newBuilder()
            .addQueryParameter("latitude", formatE1(latE1))
            .addQueryParameter("longitude", formatE1(lonE1))
            .addQueryParameter("daily", "temperature_2m_max,apparent_temperature_max,relative_humidity_2m_mean")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "1")
            .build()
        return fetch(url) { body ->
            val daily = json.decodeFromString<ForecastResponse>(body).daily ?: return@fetch WeatherResult.Failure(WeatherFailure.NO_DATA)
            val maxTemp = daily.temperatureMax.firstOrNull()
            val apparent = daily.apparentTemperatureMax.firstOrNull()
            val date = daily.time.firstOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (maxTemp == null || apparent == null || date == null) {
                WeatherResult.Failure(WeatherFailure.NO_DATA)
            } else {
                WeatherResult.Success(DailyForecast(date, maxTemp, apparent, daily.relativeHumidityMean.firstOrNull()))
            }
        }
    }

    override suspend fun searchCity(query: String, languageTag: String): WeatherResult<List<GeoPlace>> {
        val url = endpoints.geocoding.newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "10")
            .addQueryParameter("language", languageTag.substringBefore('-').ifBlank { "en" })
            .addQueryParameter("format", "json")
            .build()
        return fetch(url) { body ->
            WeatherResult.Success(
                json.decodeFromString<GeocodingResponse>(body).results.map {
                    GeoPlace(it.name, it.admin1, it.country, it.countryCode, it.latitude, it.longitude)
                },
            )
        }
    }

    private suspend fun <T> fetch(url: HttpUrl, parse: (String) -> WeatherResult<T>): WeatherResult<T> {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return try {
            val (code, body) = execute(request)
            if (code !in HTTP_OK_RANGE) WeatherResult.Failure(WeatherFailure.HTTP) else parse(body)
        } catch (_: IOException) {
            WeatherResult.Failure(WeatherFailure.NETWORK)
        } catch (_: SerializationException) {
            WeatherResult.Failure(WeatherFailure.PARSE)
        } catch (_: IllegalArgumentException) {
            WeatherResult.Failure(WeatherFailure.PARSE)
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) = response.use { r ->
                    readBody(continuation, r)
                }
            },
        )
    }

    private fun readBody(continuation: CancellableContinuation<Pair<Int, String>>, response: Response) {
        try {
            continuation.resume(response.code to response.body.string())
        } catch (e: IOException) {
            continuation.resumeWithException(e)
        }
    }

    companion object {
        const val USER_AGENT = "DihydrogenMonoxide (Android)"
        private val HTTP_OK_RANGE = 200..299
        private const val TENTHS = 10.0

        /** `525` -> `"52.5"`, `-3` -> `"-0.3"`, `0` -> `"0.0"` (never `-0.0`). */
        fun formatE1(valueE1: Int): String = String.format(Locale.ROOT, "%.1f", valueE1 / TENTHS).let { if (it == "-0.0") "0.0" else it }
    }
}
