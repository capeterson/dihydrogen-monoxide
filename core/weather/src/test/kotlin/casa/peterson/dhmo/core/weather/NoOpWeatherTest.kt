package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpWeatherTest {
    @Test
    fun `placeholders report no data`() = runTest {
        assertThat(NoOpWeatherController().refreshNow()).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        assertThat(NoOpWeatherProvider().todayForecast(525, 134)).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        assertThat(NoOpWeatherProvider().searchCity("Berlin", "en")).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        NoOpWeatherController().refreshIfStale()
        NoOpWeatherController().onSettingsChanged()
    }
}
