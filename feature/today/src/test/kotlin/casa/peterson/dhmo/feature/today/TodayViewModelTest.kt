package casa.peterson.dhmo.feature.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val intake = FakeIntakeRepository(clock, zone)
    private val goals = FakeGoalRepository(clock)
    private val containers = FakeContainerRepository(TestData.defaultContainers(casa.peterson.dhmo.core.model.settings.VolumeUnit.ML))
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val observe = ObserveTodayUseCase(ticker, intake, goals, settings, clock, zone)
    private val vm =
        TodayViewModel(
            observe,
            containers,
            settings,
            FakeDeviceStateRepository(),
            LogIntakeUseCase(intake),
            DeleteIntakeUseCase(intake),
            UndoDeleteIntakeUseCase(intake),
            clock,
        )

    @Test
    fun quickAddDeleteAndUndo() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            var ready = awaitItem()
            while (ready !is TodayUiState.Ready) ready = awaitItem()
            vm.onQuickAdd(ready.containers.first())
            while ((ready as TodayUiState.Ready).entries.isEmpty()) ready = awaitItem()
            val id = ready.entries.single().id
            vm.effects.test {
                vm.onDelete(id)
                assertThat(awaitItem()).isEqualTo(TodayEffect.EntryDeleted(id))
                vm.onUndoDelete(id)
                assertThat(intake.get(id)?.deletedAt).isNull()
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidAmountProducesError() = runTest {
        vm.effects.test {
            vm.onCustomAdd(0)
            assertThat(awaitItem()).isEqualTo(TodayEffect.Error(R.string.feature_today_invalid_amount))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun goal() = DailyGoal(
        LocalDate.of(2026, 6, 10),
        2000,
        2000,
        GoalMode.CALCULATED,
        GoalBreakdown(1, 36, emptyList(), 2000, 2000, 2000, ClampFlag.NONE),
        0,
        1,
        clock.instant(),
    )
}
