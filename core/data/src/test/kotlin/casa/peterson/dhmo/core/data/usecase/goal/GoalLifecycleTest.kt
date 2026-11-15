package casa.peterson.dhmo.core.data.usecase.goal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GoalLifecycleTest {
    private val clock = MutableClock(Instant.parse("2026-06-10T03:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val goals = FakeGoalRepository(clock)
    private val reminders = FakeReminderScheduler()
    private val intake = FakeIntakeRepository(clock, zone)
    private val profiles: FakeProfileRepository = FakeProfileRepository(listOf(TestData.snapshot(LocalDate.of(2026, 6, 1))), clock)
    private val input: BuildGoalInputUseCase = BuildGoalInputUseCase(
        profiles,
        settings,
        FakeActivityRepository(clock),
        FakeWeatherRepository(),
        FakeHealthConnectController(),
    )
    private val snapshot = SnapshotDailyGoalUseCase(input, GoalCalculator(), GoalPolicyV1, goals, settings, reminders, clock)

    @Test
    fun backfillCreatesOnlyMissingDaysAfterTheLastSnapshot() = runTest {
        snapshot(LocalDate.of(2026, 6, 7), notifyReminders = false)
        BackfillDailyGoalsUseCase(goals, snapshot, ticker)()
        assertThat(goals.savedSnapshots.map { it.date }).containsExactly(
            LocalDate.of(2026, 6, 7),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 6, 10),
        ).inOrder()
    }

    @Test
    fun changeDayStartRebucketsAndResnapshotsTransitionDays() = runTest {
        val entry = intake.add(250, Source.MANUAL)
        snapshot(LocalDate.of(2026, 6, 10))
        ChangeDayStartUseCase(settings, intake, snapshot, reminders, clock, zone)(240)
        assertThat(intake.get(entry.id)?.hydrationDay).isEqualTo(LocalDate.of(2026, 6, 9))
        assertThat(goals.goal(LocalDate.of(2026, 6, 9))?.dayStartMinutes).isEqualTo(240)
        assertThat(goals.goal(LocalDate.of(2026, 6, 10))?.dayStartMinutes).isEqualTo(240)
    }

    @Test
    fun todayStreamEmitsEntryAndGoalChanges() = runTest {
        val today = LocalDate.of(2026, 6, 10)
        snapshot(today)
        ObserveTodayUseCase(ticker, intake, goals, settings, clock, zone)().test {
            assertThat(awaitItem().totalMl).isEqualTo(0)
            intake.add(250, Source.MANUAL)
            var next = awaitItem()
            while (next.totalMl != 250) next = awaitItem()
            goals.setDayOverride(today, 1800, null)
            snapshot(today)
            do {
                next = awaitItem()
            } while (next.goal?.targetMl != 1800)
            assertThat(next.entries).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun todayStreamSwitchesAtTheDayBoundary() = runTest {
        clock.set(Instant.parse("2026-06-10T23:00:00Z"))
        ObserveTodayUseCase(ticker, intake, goals, settings, clock, zone)().test {
            assertThat(awaitItem().day).isEqualTo(LocalDate.of(2026, 6, 10))
            clock.set(Instant.parse("2026-06-11T00:00:00Z"))
            advanceTimeBy(3_600_001)
            assertThat(awaitItem().day).isEqualTo(LocalDate.of(2026, 6, 11))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
