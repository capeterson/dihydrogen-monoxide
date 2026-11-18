package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.goal.GoalPolicy
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.settings.WeatherMode
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/** Fetches today's forecast, stores it, and re-snapshots the goal (§12.5). */
@Singleton
internal class WeatherControllerImpl @Inject constructor(
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val provider: WeatherProvider,
    private val weather: WeatherRepository,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val policy: GoalPolicy,
    private val ticker: DayTicker,
    private val scheduler: WeatherWorkScheduler,
    private val locationRefresher: LocationRefresher,
    private val clock: Clock,
) : WeatherController {
    override suspend fun refreshIfStale() {
        if (settings.current().weather.mode == WeatherMode.OFF) return
        val today = ticker.today().first()
        val existing = weather.forDay(today)
        val age = existing?.let { ChronoUnit.MILLIS.between(it.fetchedAt, clock.instant()) }
        val fresh = age != null && age in 0..STALE_AFTER_MS
        if (!fresh) refreshNow()
    }

    @Suppress("ReturnCount")
    override suspend fun refreshNow(): WeatherResult<WeatherDay> {
        val current = settings.current().weather
        val today = ticker.today().first()
        val coordinates = when (current.mode) {
            WeatherMode.OFF -> return WeatherResult.Failure(WeatherFailure.NO_DATA)
            WeatherMode.MANUAL_CITY -> current.manualCity?.let { it.latE1 to it.lonE1 }
            WeatherMode.DEVICE_LOCATION -> deviceState.current().cachedLocation?.let { it.latE1 to it.lonE1 }
        } ?: return failed(WeatherFailure.NO_DATA, today)

        val result = when (val forecast = provider.todayForecast(coordinates.first, coordinates.second)) {
            is WeatherResult.Failure -> forecast

            is WeatherResult.Success -> {
                val value = forecast.value
                if (abs(ChronoUnit.DAYS.between(value.date, today)) > 1) {
                    WeatherResult.Failure(WeatherFailure.NO_DATA)
                } else {
                    WeatherResult.Success(
                        WeatherDay(
                            date = today,
                            latE1 = coordinates.first,
                            lonE1 = coordinates.second,
                            maxTempC = value.maxTempC,
                            maxApparentTempC = value.maxApparentTempC,
                            meanRelHumidity = value.meanRelHumidityPercent,
                            extraMl = policy.climateForecastMl(value.maxApparentTempC),
                            fetchedAt = clock.instant(),
                        ),
                    )
                }
            }
        }
        return when (result) {
            is WeatherResult.Failure -> failed(result.reason, today)

            is WeatherResult.Success -> {
                weather.save(result.value)
                deviceState.update {
                    it.copy(weatherRuntime = it.weatherRuntime.copy(lastFetchAtMs = clock.millis(), lastFetchFailed = false))
                }
                snapshot(today)
                result
            }
        }
    }

    override suspend fun onSettingsChanged() {
        val mode = settings.current().weather.mode
        if (mode == WeatherMode.OFF) {
            scheduler.cancel()
            deviceState.update { it.copy(weatherRuntime = it.weatherRuntime.copy(lastFetchFailed = false)) }
            snapshot(ticker.today().first())
        } else {
            scheduler.ensureScheduled()
            if (mode == WeatherMode.DEVICE_LOCATION) locationRefresher.refreshNow()
            refreshNow()
        }
    }

    private suspend fun failed(reason: WeatherFailure, today: LocalDate): WeatherResult.Failure {
        deviceState.update { it.copy(weatherRuntime = it.weatherRuntime.copy(lastFetchFailed = true)) }
        snapshot(today)
        return WeatherResult.Failure(reason)
    }

    private companion object {
        const val STALE_AFTER_MS = 6L * 60 * 60 * 1000
    }
}
