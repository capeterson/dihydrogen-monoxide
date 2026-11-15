package casa.peterson.dhmo.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineActivityAndWeatherRepositoryTest {
    @get:Rule val env = RoomTestEnvironment()

    private val clock = MutableClock(Instant.parse("2026-06-10T08:00:00Z"))
    private val day = LocalDate.of(2026, 6, 10)

    @Test
    fun `planned activity is priced per intensity and can be removed`() = runTest {
        val repo = OfflineActivityRepository(env.db.activityDao(), clock)
        repo.addPlanned(day, 30, Intensity.LIGHT)
        repo.addPlanned(day, 30, Intensity.MODERATE)
        repo.addPlanned(day, 30, Intensity.VIGOROUS)

        val rows = repo.forDay(day)
        assertThat(rows.map { it.extraMl }).containsExactly(180, 360, 540)
        assertThat(rows.map { it.source }.toSet()).containsExactly(ActivitySource.MANUAL_PLANNED)
        assertThat(rows.all { it.sourceRecordId.startsWith("manual-") }).isTrue()
        assertThat(repo.observeDay(day).first()).hasSize(3)
        assertThat(repo.minutesPerDay(day, day.plusDays(1))).containsExactly(day, 90)

        repo.removePlanned(rows.first().sourceRecordId)
        assertThat(repo.forDay(day)).hasSize(2)
    }

    @Test
    fun `weather days round trip`() = runTest {
        val repo = OfflineWeatherRepository(env.db.weatherDao())
        val weather = WeatherDay(day, 524, 134, 28.5, 31.0, 55.0, 250, clock.instant())
        repo.save(weather)
        repo.save(weather.copy(date = day.plusDays(1), meanRelHumidity = null))

        assertThat(repo.forDay(day)).isEqualTo(weather)
        assertThat(repo.observeDay(day).first()).isEqualTo(weather)
        assertThat(repo.range(day, day.plusDays(1))).hasSize(2)
        assertThat(repo.range(day, day.plusDays(1)).last().meanRelHumidity).isNull()
        assertThat(repo.forDay(day.minusDays(1))).isNull()
    }
}
