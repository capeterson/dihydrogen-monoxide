package casa.peterson.dhmo.feature.insights

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.domain.stats.CorrelationStrength
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.feature.insights.correlation.CorrelationViewModel
import casa.peterson.dhmo.feature.insights.domain.CorrelationDriver
import casa.peterson.dhmo.feature.insights.domain.LoadCorrelationPairsUseCase
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CorrelationTest {
    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 17)
    private val clock = MutableClock(Instant.parse("2026-11-17T10:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val intake = FakeIntakeRepository(clock, zone)
    private val weather = FakeWeatherRepository()
    private val activity = FakeActivityRepository(clock)
    private val load = LoadCorrelationPairsUseCase(intake, weather, activity)
    private val ticker by lazy { DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, FakeSettingsRepository()) }

    /** One logged day per entry in [days]: ml and the day's apparent temperature rise together. */
    private suspend fun seed(days: Int, temperatures: Boolean = true) {
        repeat(days) { i ->
            val day = today.minusDays(days - i.toLong())
            intake.add(1500 + i * 50, Source.MANUAL, day.atTime(12, 0).toInstant(ZoneOffset.UTC), null)
            if (temperatures) weather.save(WeatherDay(day, 525, 134, 20.0 + i, 20.0 + i, null, 0, clock.instant()))
        }
    }

    @Test
    fun `pairs match logged days with their temperature, sorted by the driver`() = runTest {
        seed(5)
        val pairs = load(CorrelationDriver.TEMPERATURE, today)
        assertThat(pairs).hasSize(5)
        assertThat(pairs.map { it.first }).isInOrder()
        assertThat(pairs.first().second).isEqualTo(1500.0)
    }

    @Test
    fun `days without a forecast are left out rather than counted as zero degrees`() = runTest {
        seed(5, temperatures = false)
        assertThat(load(CorrelationDriver.TEMPERATURE, today)).isEmpty()
    }

    @Test
    fun `with some activity data a logged day without a session is a rest day`() = runTest {
        seed(4, temperatures = false)
        activity.put(ActivityAdjustment(ActivitySource.HEALTH_CONNECT, "r1", today.minusDays(1), 30, Intensity.MODERATE, 360, null, clock.instant()))
        val pairs = load(CorrelationDriver.ACTIVE_MINUTES, today)
        assertThat(pairs).hasSize(4)
        assertThat(pairs.count { it.first == 0.0 }).isEqualTo(3)
    }

    @Test
    fun `with no activity data at all there is nothing to correlate`() = runTest {
        seed(4, temperatures = false)
        assertThat(load(CorrelationDriver.ACTIVE_MINUTES, today)).isEmpty()
    }

    @Test
    fun `a rising pattern over enough days is a strong positive link`() = runTest {
        seed(20)
        val model = CorrelationViewModel(SavedStateHandle(mapOf("driver" to "TEMPERATURE")), load, ticker, FakeSettingsRepository())
        runCurrent()
        val state = model.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.result?.strength).isEqualTo(CorrelationStrength.STRONG)
        assertThat(state.result?.positive).isTrue()
        assertThat(state.result?.n).isEqualTo(20)
    }

    @Test
    fun `fewer than 14 days is not enough data`() = runTest {
        seed(10)
        val model = CorrelationViewModel(SavedStateHandle(mapOf("driver" to "TEMPERATURE")), load, ticker, FakeSettingsRepository())
        runCurrent()
        assertThat(model.state.value.result).isNull()
        assertThat(model.state.value.pairs).hasSize(10)
    }
}
