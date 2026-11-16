package casa.peterson.dhmo.feature.settings.units

import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.ChangeDayStartUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnitsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** 2026-06-10 at 02:30 UTC: with a midnight day start, "today" is June 10; with 04:00 it is June 9. */
    private val clock = MutableClock(Instant.parse("2026-06-10T02:30:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val intake = FakeIntakeRepository(clock, zone)
    private val goals = FakeGoalRepository(clock)
    private val reminders = FakeReminderScheduler()
    private val snapshot = SnapshotDailyGoalUseCase(
        BuildGoalInputUseCase(
            FakeProfileRepository(listOf(TestData.snapshot(LocalDate.of(2026, 1, 1), TestData.adultProfile)), clock),
            settings,
            FakeActivityRepository(clock),
            FakeWeatherRepository(),
            FakeHealthConnectController(),
        ),
        GoalCalculator(),
        GoalPolicyV1,
        goals,
        settings,
        reminders,
        clock,
    )
    private val model by lazy { UnitsViewModel(settings, ChangeDayStartUseCase(settings, intake, snapshot, reminders, clock, zone)) }

    @Test
    fun unitsAreSaved() = runTest {
        model.uiState.launchIn(backgroundScope)
        model.setUnits(VolumeUnit.US_FL_OZ)
        advanceUntilIdle()
        assertThat(settings.current().units).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(model.uiState.value.units).isEqualTo(VolumeUnit.US_FL_OZ)
    }

    @Test
    fun dayStartNeedsConfirmationThenGoesThroughTheUseCase() = runTest {
        model.uiState.launchIn(backgroundScope)
        intake.add(250, Source.MANUAL, Instant.parse("2026-06-10T01:00:00Z"))
        model.requestDayStart(240)
        advanceUntilIdle()
        assertThat(model.uiState.value.pendingDayStart).isEqualTo(240)
        assertThat(settings.current().dayStartMinutes).isEqualTo(0)

        model.confirmDayStart()
        advanceUntilIdle()
        assertThat(settings.current().dayStartMinutes).isEqualTo(240)
        assertThat(model.uiState.value.pendingDayStart).isNull()
        assertThat(model.uiState.value.changingDayStart).isFalse()
        // The 01:00 entry belonged to June 10 and, with a 04:00 start, falls on June 9.
        assertThat(intake.all.single().hydrationDay).isEqualTo(LocalDate.of(2026, 6, 9))
        assertThat(goals.goal(LocalDate.of(2026, 6, 9))?.dayStartMinutes).isEqualTo(240)
        assertThat(reminders.reasons).contains(RescheduleReason.SETTINGS_CHANGED)
    }

    @Test
    fun dismissingLeavesTheDayStartAlone() = runTest {
        model.uiState.launchIn(backgroundScope)
        model.requestDayStart(300)
        advanceUntilIdle()
        model.dismissDayStart()
        advanceUntilIdle()
        assertThat(model.uiState.value.pendingDayStart).isNull()
        assertThat(settings.current().dayStartMinutes).isEqualTo(0)
        assertThat(reminders.reasons).isEmpty()
    }

    @Test
    fun choosingTheCurrentValueAsksNothing() = runTest {
        model.uiState.launchIn(backgroundScope)
        model.requestDayStart(0)
        advanceUntilIdle()
        assertThat(model.uiState.value.pendingDayStart).isNull()
        model.confirmDayStart()
        advanceUntilIdle()
        assertThat(reminders.reasons).isEmpty()
    }

    @Test
    fun outOfRangeDayStartIsRejected() {
        assertThat(runCatching { model.requestDayStart(1_440) }.isFailure).isTrue()
        assertThat(runCatching { model.requestDayStart(-1) }.isFailure).isTrue()
    }
}
