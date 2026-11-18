package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherResult
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenMeteoProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: OpenMeteoProvider

    @Before
    fun setUp() {
        server.start()
        provider = OpenMeteoProvider(
            OkHttpClient(),
            Json { ignoreUnknownKeys = true },
            WeatherEndpoints(server.url("/v1/forecast"), server.url("/v1/search")),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `forecast request has the documented path, query and agent`() = runTest {
        respond(SAMPLE)
        provider.todayForecast(525, 134)
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/forecast")
        assertThat(request.url.queryParameter("latitude")).isEqualTo("52.5")
        assertThat(request.url.queryParameter("longitude")).isEqualTo("13.4")
        assertThat(request.url.queryParameter("daily"))
            .isEqualTo("temperature_2m_max,apparent_temperature_max,relative_humidity_2m_mean")
        assertThat(request.url.queryParameter("timezone")).isEqualTo("auto")
        assertThat(request.url.queryParameter("forecast_days")).isEqualTo("1")
        assertThat(request.headers["User-Agent"]).startsWith("DihydrogenMonoxide")
        assertThat(request.url.queryParameterNames).containsExactly("latitude", "longitude", "daily", "timezone", "forecast_days")
    }

    @Test
    fun `parses the sample response`() = runTest {
        respond(SAMPLE)
        assertThat(provider.todayForecast(525, 134))
            .isEqualTo(WeatherResult.Success(DailyForecast(LocalDate.of(2026, 11, 15), 7.8, 3.4, 76.0)))
    }

    @Test
    fun `coordinates format with one decimal and never negative zero`() {
        assertThat(OpenMeteoProvider.formatE1(0)).isEqualTo("0.0")
        assertThat(OpenMeteoProvider.formatE1(-3)).isEqualTo("-0.3")
        assertThat(OpenMeteoProvider.formatE1(-338)).isEqualTo("-33.8")
        assertThat(OpenMeteoProvider.formatE1(1799)).isEqualTo("179.9")
    }

    @Test
    fun `humidity may be missing`() = runTest {
        respond("""{"daily":{"time":["2026-11-15"],"temperature_2m_max":[7.8],"apparent_temperature_max":[3.4]}}""")
        val result = provider.todayForecast(525, 134) as WeatherResult.Success
        assertThat(result.value.meanRelHumidityPercent).isNull()
    }

    @Test
    fun `null temperature, empty lists and a missing daily block are no data`() = runTest {
        respond("""{"daily":{"time":["2026-11-15"],"temperature_2m_max":[null],"apparent_temperature_max":[3.4]}}""")
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        respond("""{"daily":{"time":[],"temperature_2m_max":[],"apparent_temperature_max":[]}}""")
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        respond("""{"latitude":1.0}""")
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
    }

    @Test
    fun `http errors and malformed json map to their own failures`() = runTest {
        respond("""{"reason":"bad","error":true}""", code = 400)
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.HTTP))
        respond("not json")
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.PARSE))
    }

    @Test
    fun `an unreachable server is a network failure`() = runTest {
        server.close() // nothing is listening any more: connection refused
        assertThat(provider.todayForecast(1, 1)).isEqualTo(WeatherResult.Failure(WeatherFailure.NETWORK))
    }

    @Test
    fun `city search sends language subtag and maps results`() = runTest {
        respond(
            """{"results":[{"id":1,"name":"Berlin","latitude":52.52,"longitude":13.41,"country_code":"DE","country":"Germany","admin1":"State of Berlin"}]}""",
        )
        val result = provider.searchCity("Berlin", "de-DE") as WeatherResult.Success
        val request = server.takeRequest()
        assertThat(request.url.queryParameter("name")).isEqualTo("Berlin")
        assertThat(request.url.queryParameter("language")).isEqualTo("de")
        assertThat(request.url.queryParameter("count")).isEqualTo("10")
        assertThat(result.value.single().name).isEqualTo("Berlin")
        assertThat(result.value.single().countryCode).isEqualTo("DE")
    }

    @Test
    fun `an empty geocoding result is an empty list`() = runTest {
        respond("""{"generationtime_ms":0.2}""")
        assertThat(provider.searchCity("Zzzzzz", "en")).isEqualTo(WeatherResult.Success(emptyList<Any>()))
    }

    private companion object {
        const val SAMPLE =
            """{"latitude":52.5,"longitude":13.4,"timezone":"Europe/Berlin","daily":{"time":["2026-11-15"],""" +
                """"temperature_2m_max":[7.8],"apparent_temperature_max":[3.4],"relative_humidity_2m_mean":[76]}}"""
    }
}
