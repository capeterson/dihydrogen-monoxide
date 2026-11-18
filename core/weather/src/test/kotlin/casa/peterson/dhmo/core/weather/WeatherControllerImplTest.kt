package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.CachedLocation
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.settings.WeatherSettings
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherProvider
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WeatherControllerImplTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock(Instant.parse("2026-11-15T10:00:00Z"))
    private val settings = FakeSettingsRepository(
        AppSettings(weather = WeatherSettings(WeatherMode.MANUAL_CITY, ManualCity("Berlin", null, "DE", 525, 134))),
    )
    private val deviceState = FakeDeviceStateRepository()
    private val provider = FakeWeatherProvider(WeatherResult.Success(DailyForecast(today, 31.0, 33.0, 60.0)))
    private val weather = FakeWeatherRepository()
    private val snapshot = mockk<SnapshotDailyGoalUseCase>(relaxed = true)
    private val ticker = mockk<DayTicker> { every { today() } returns flowOf(today) }
    private val scheduler = RecordingScheduler()
    private val location = mockk<LocationRefresher>(relaxed = true)

    private val controller = WeatherControllerImpl(
        settings, deviceState, provider, weather, snapshot, GoalPolicyV1, ticker, scheduler, location, clock,
    )

    private class RecordingScheduler : WeatherWorkScheduler {
        var scheduled = 0
        var cancelled = 0

        override fun ensureScheduled() {
            scheduled++
        }

        override fun cancel() {
            cancelled++
        }
    }

    @Test
    fun `a successful fetch stores the day with the table amount and re-snapshots the goal`() = runTest {
        val result = controller.refreshNow() as WeatherResult.Success
        assertThat(result.value.extraMl).isEqualTo(500) // 33 degrees apparent: 30..35
        assertThat(weather.forDay(today)?.maxApparentTempC).isEqualTo(33.0)
        assertThat(provider.forecastRequests).containsExactly(525 to 134)
        assertThat(deviceState.current().weatherRuntime.lastFetchFailed).isFalse()
        coVerify { snapshot(today) }
    }

    @Test
    fun `a failed fetch writes no row, sets the flag and still re-snapshots so the goal falls back`() = runTest {
        provider.forecast = WeatherResult.Failure(WeatherFailure.NETWORK)
        assertThat(controller.refreshNow()).isEqualTo(WeatherResult.Failure(WeatherFailure.NETWORK))
        assertThat(weather.forDay(today)).isNull()
        assertThat(deviceState.current().weatherRuntime.lastFetchFailed).isTrue()
        coVerify { snapshot(today) }
    }

    @Test
    fun `off mode makes no request`() = runTest {
        settings.update { it.copy(weather = WeatherSettings(WeatherMode.OFF)) }
        assertThat(controller.refreshNow()).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        controller.refreshIfStale()
        assertThat(provider.forecastRequests).isEmpty()
    }

    @Test
    fun `device mode without a cached location is no data`() = runTest {
        settings.update { it.copy(weather = WeatherSettings(WeatherMode.DEVICE_LOCATION)) }
        assertThat(controller.refreshNow()).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
        assertThat(provider.forecastRequests).isEmpty()
        deviceState.update { DeviceState(cachedLocation = CachedLocation(-338, 1512, 0)) }
        assertThat(controller.refreshNow()).isInstanceOf(WeatherResult.Success::class.java)
        assertThat(provider.forecastRequests).containsExactly(-338 to 1512)
    }

    @Test
    fun `a forecast more than a day away from today is rejected`() = runTest {
        provider.forecast = WeatherResult.Success(DailyForecast(today.plusDays(3), 20.0, 20.0, null))
        assertThat(controller.refreshNow()).isEqualTo(WeatherResult.Failure(WeatherFailure.NO_DATA))
    }

    @Test
    fun `refreshIfStale fetches when there is no row or it is over six hours old, not otherwise`() = runTest {
        controller.refreshIfStale()
        assertThat(provider.forecastRequests).hasSize(1)
        controller.refreshIfStale()
        assertThat(provider.forecastRequests).hasSize(1)
        clock.advanceBy(java.time.Duration.ofHours(7))
        controller.refreshIfStale()
        assertThat(provider.forecastRequests).hasSize(2)
    }

    @Test
    fun `settings changes schedule or cancel the worker`() = runTest {
        controller.onSettingsChanged()
        assertThat(scheduler.scheduled).isEqualTo(1)
        settings.update { it.copy(weather = WeatherSettings(WeatherMode.OFF)) }
        controller.onSettingsChanged()
        assertThat(scheduler.cancelled).isEqualTo(1)
    }
}
