package casa.peterson.dhmo.core.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BackfillDailyGoalsUseCase
import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FakeWidgetUpdater
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayRolloverCoordinatorTest {
    private val clock = MutableClock(Instant.parse("2026-06-10T03:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val goals: FakeGoalRepository = FakeGoalRepository(clock)
    private val input: BuildGoalInputUseCase = BuildGoalInputUseCase(
        FakeProfileRepository(listOf(TestData.snapshot(LocalDate.of(2026, 6, 1))), clock),
        settings,
        FakeActivityRepository(clock),
        FakeWeatherRepository(),
        FakeHealthConnectController(),
    )
    private val snapshot: SnapshotDailyGoalUseCase = SnapshotDailyGoalUseCase(
        input,
        GoalCalculator(GoalPolicyV1),
        GoalPolicyV1,
        goals,
        settings,
        FakeReminderScheduler(),
        clock,
    )

    @Test
    fun `a rollover refresh snapshots the new day and repaints the widgets`() = runTest {
        val widgets = FakeWidgetUpdater()
        DayRolloverCoordinator(BackfillDailyGoalsUseCase(goals, snapshot, ticker), snapshot, ticker, widgets).refresh()
        assertThat(goals.goal(LocalDate.of(2026, 6, 10))).isNotNull()
        assertThat(widgets.updateCount).isEqualTo(1)
    }

    @Test
    fun `a failing widget refresh does not fail the rollover`() = runTest {
        val widgets = object : WidgetUpdater {
            override suspend fun updateAll() = error("launcher gone")
        }
        DayRolloverCoordinator(BackfillDailyGoalsUseCase(goals, snapshot, ticker), snapshot, ticker, widgets).refresh()
        assertThat(goals.goal(LocalDate.of(2026, 6, 10))).isNotNull()
    }
}
