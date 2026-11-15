package casa.peterson.dhmo.core.data.time

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DayTickerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-06-10T21:00:00Z")) // 23:00 in Berlin
    private val zone = FixedZoneProvider(ZoneId.of("Europe/Berlin"))
    private val settings = FakeSettingsRepository()
    private val ticker = DayTicker(context, clock, zone, settings)

    @Test
    fun `emits today and then tomorrow when the boundary passes`() = runTest {
        ticker.today().test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 10))
            clock.set(Instant.parse("2026-06-10T22:00:00Z")) // midnight in Berlin
            testScheduler.advanceTimeBy(1.hours + 1.hours)
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 11))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day start of 04 00 shifts the boundary`() = runTest {
        settings.update { it.copy(dayStartMinutes = 4 * 60) }
        clock.set(Instant.parse("2026-06-11T01:30:00Z")) // 03:30 in Berlin, still the 10th
        ticker.today().test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 10))
            clock.set(Instant.parse("2026-06-11T02:00:00Z")) // 04:00
            testScheduler.advanceTimeBy(1.hours)
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 11))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the day start setting re-emits`() = runTest {
        clock.set(Instant.parse("2026-06-11T01:30:00Z")) // 03:30 in Berlin
        ticker.today().test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 11))
            settings.update { it.copy(dayStartMinutes = 4 * 60) }
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 10))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a time zone change broadcast re-emits`() = runTest {
        ticker.today().test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 10))
            zone.zone = ZoneId.of("Asia/Tokyo") // 06:00 on the 11th
            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 11))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a time change broadcast re-emits`() = runTest {
        ticker.today().test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 10))
            clock.set(Instant.parse("2026-06-12T10:00:00Z"))
            context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 12))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
